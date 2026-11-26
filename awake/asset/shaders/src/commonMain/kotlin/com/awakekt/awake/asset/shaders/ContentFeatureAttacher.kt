/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.command.UniformBlockOwner
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineRequest
import com.awakekt.awake.render.pipeline.PipelineSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * [ContentFeatureHost] over one engine's [gpu]. The engine records [beforeGeometry],
 * [opaqueGeometry] and [afterGeometry] in its scene pass and calls [releaseAll] after destroying its pipelines.
 *
 * Features with the same pipeline spec share one registered pipeline, and one depth pipeline per
 * depth spec, counted by the features using them: the last to detach frees them. Features
 * declaring an equal [ContentFeature.geometryKey] likewise share one uploaded GPU geometry when
 * the backend implements [ContentFeatureGpu.uploadGeometry], counted by the features using it so
 * the last to detach releases the vertex/index buffers. Each feature keeps its own uniform block
 * and texture group ([ContentFeatureGpu.uploadGroup]) and draw, so detaching one leaves the others
 * drawing. A backend that cannot give a feature a group of its own takes one feature per spec, as
 * before.
 */
class ContentFeatureAttacher<P : UniformBlockOwner>(private val gpu: ContentFeatureGpu<P>) : ContentFeatureHost {
    val beforeGeometry = AttachedContentSlot()
    val opaqueGeometry = AttachedContentSlot()

    /** Attached features painted after opaque geometry and before transparent surfaces (e.g. Early-Z skybox). */
    val afterOpaque = AttachedContentSlot()

    /** Attached features painted after opaque geometry and before transparent scene draws. */
    val beforeTransparent = AttachedContentSlot()
    val afterGeometry = AttachedContentSlot()
    private val attached = mutableListOf<Attachment>()
    private val shared = HashMap<PipelineSpec, SharedPipeline>()
    private val sharedAlternates = HashMap<PipelineSpec, Int>()
    private val sharedGeometries = HashMap<Any, SharedGeometry>()
    private val sharedTextures = HashMap<SharedTextureKey, SharedTexture>()

    /** Attaches register and build across suspensions; one at a time, so two of one spec share. */
    private val attaching = Mutex()

    override val supportsSceneDepth: Boolean get() = gpu.supportsSceneDepth

    override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature = attaching.withLock {
        val feature = source.resolve(gpu.backend)
        require(!feature.samplesMask) {
            "Content feature '${feature.name}' samples the mask, whose set layouts are " +
                "fixed when the engine starts. Declare it in the RenderPlan instead."
        }
        require(!feature.samplesSceneDepth || gpu.supportsSceneDepth) {
            "Content feature '${feature.name}' samples scene depth, which requires " +
                "RenderPlan.sceneDepthShaderSet to be configured when the engine starts."
        }
        val pipeline = acquire(feature)
        val uploads = mutableListOf<ContentUpload>()
        var sharedGeometry: SharedGeometry? = null
        val acquiredTextures = mutableListOf<SharedTexture>()
        var builtWhole = false
        val built = try {
            sharedGeometry = acquireGeometry(feature)
            val textureHandles = acquireTextures(feature, acquiredTextures)
            build(pipeline, feature, sharedGeometry?.upload?.geometry, textureHandles, uploads).also {
                require(it.pass == RenderPassSlot.Scene) {
                    "Content feature '${feature.name}' records in ${it.pass}; attached features " +
                        "record in the scene pass."
                }
                builtWhole = true
            }
        } finally {
            // Whatever failed or cancelled the build mid-way, release what it allocated. Nothing has
            // recorded with them yet, so no frame can be reading them.
            if (!builtWhole) {
                release(pipeline)
                sharedGeometry?.let(::releaseGeometry)
                acquiredTextures.forEach(::releaseTexture)
                uploads.forEach(ContentUpload::release)
            }
        }
        val slot = when (feature.paint) {
            ContentPaint.BeforeGeometry -> beforeGeometry
            ContentPaint.OpaqueGeometry -> opaqueGeometry
            ContentPaint.AfterOpaque -> afterOpaque
            ContentPaint.BeforeTransparent -> beforeTransparent
            ContentPaint.AfterGeometry -> afterGeometry
        }
        slot.add(built)
        Attachment(pipeline, sharedGeometry, acquiredTextures, built, slot, uploads).also { attached += it }
    }

    /** Frees every still-attached feature's uploads, shared geometry, shared textures, and depth pipelines. For engine
     * teardown, after the renderer destroyed its features and the registry its pipelines. */
    fun releaseAll() {
        attached.forEach { it.uploads.forEach(ContentUpload::release) }
        attached.clear()
        sharedGeometries.values.forEach { it.upload.release() }
        sharedGeometries.clear()
        sharedTextures.values.forEach { it.upload.release() }
        sharedTextures.clear()
        shared.values.forEach { it.destroyDepth() }
        shared.clear()
        sharedAlternates.clear()
    }

    /** The pipeline [feature]'s spec compiled to, registered by the first feature to use it. */
    private suspend fun acquire(feature: ContentFeature): SharedPipeline {
        shared[feature.spec]?.let { entry ->
            entry.users++
            return entry
        }
        require(gpu.registry[feature.spec] == null) {
            "Content feature '${feature.name}' has the pipeline spec of one the RenderPlan declared, " +
                "whose texture group that feature owns."
        }
        val altSpec = feature.alternateSpec?.takeIf { it != feature.spec }
        val altAlreadyInRegistry = altSpec != null && gpu.registry[altSpec] != null
        val registerAltSpec = altSpec?.takeIf { !altAlreadyInRegistry }
        gpu.registry.register(listOf(PipelineRequest(PipelineKey.Content(feature.name), feature.spec, solidSpec = registerAltSpec)))
        val pipeline = checkNotNull(gpu.registry[feature.spec]) {
            "Content feature '${feature.name}' was not registered before its feature was built."
        }
        val alternatePipeline = altSpec?.let { gpu.registry[it] }
        val ownsAlternate = when {
            altSpec == null || alternatePipeline == null -> false
            altSpec in sharedAlternates -> {
                sharedAlternates[altSpec] = sharedAlternates.getValue(altSpec) + 1
                true
            }
            !altAlreadyInRegistry -> {
                sharedAlternates[altSpec] = 1
                true
            }
            else -> false
        }
        return SharedPipeline(feature.spec, pipeline, altSpec.takeIf { ownsAlternate }, alternatePipeline).also {
            shared[feature.spec] = it
        }
    }

    /** One user fewer; the last one frees the depth pipelines and the pipeline. */
    private fun release(entry: SharedPipeline) {
        if (--entry.users > 0) return
        shared.remove(entry.spec)
        entry.destroyDepth()
        gpu.registry.remove(entry.spec)?.let(gpu::destroyPipeline)
        entry.alternateSpec?.let { alt ->
            val remaining = (sharedAlternates[alt] ?: 0) - 1
            if (remaining <= 0) {
                sharedAlternates.remove(alt)
                gpu.registry.remove(alt)?.let(gpu::destroyPipeline)
            } else {
                sharedAlternates[alt] = remaining
            }
        }
    }

    private fun acquireGeometry(feature: ContentFeature): SharedGeometry? {
        val key = feature.geometryKey
        val source = feature.geometry
        return when {
            key == null || source == null -> null
            key in sharedGeometries -> sharedGeometries.getValue(key).also { it.users++ }
            else -> gpu.uploadGeometry(source)?.let { upload ->
                if (upload.geometry == null) {
                    upload.release()
                    throw IllegalArgumentException(
                        "ContentFeatureGpu.uploadGeometry returned no ContentGeometry for '${feature.name}'.",
                    )
                }
                SharedGeometry(key, upload).also { sharedGeometries[key] = it }
            }
        }
    }

    private fun releaseGeometry(entry: SharedGeometry) {
        if (--entry.users > 0) return
        sharedGeometries.remove(entry.key)
        entry.upload.release()
    }

    private fun acquireTextures(
        feature: ContentFeature,
        acquired: MutableList<SharedTexture>,
    ): Map<Int, ContentTextureHandle> {
        if (feature.textureKeys.isEmpty()) return emptyMap()
        val mipSkip = gpu.textureMipSkip
        return buildMap(feature.textureKeys.size) {
            for ((binding, rawKey) in feature.textureKeys) {
                val cacheKey = SharedTextureKey(rawKey, mipSkip)
                val entry = sharedTextures[cacheKey]?.also { it.users++ }
                    ?: gpu.uploadTexture(feature.textures.getValue(binding))?.let { upload ->
                        SharedTexture(cacheKey, upload).also { sharedTextures[cacheKey] = it }
                    }
                    ?: continue
                acquired += entry
                put(binding, entry.upload.texture)
            }
        }
    }

    private fun releaseTexture(entry: SharedTexture) {
        if (--entry.users > 0) return
        sharedTextures.remove(entry.key)
        entry.upload.release()
    }

    private suspend fun build(
        entry: SharedPipeline,
        feature: ContentFeature,
        sharedGeometry: ContentGeometry?,
        sharedTextureHandles: Map<Int, ContentTextureHandle>,
        uploads: MutableList<ContentUpload>,
    ): RenderFeature<RenderFrameContext> {
        val includeGeometry = sharedGeometry == null
        val group = if (sharedTextureHandles.isEmpty()) {
            if (includeGeometry) gpu.uploadGroup(entry.pipeline, feature) else gpu.uploadGroup(entry.pipeline, feature, includeGeometry = false)
        } else {
            gpu.uploadGroup(entry.pipeline, feature, includeGeometry = includeGeometry, sharedTextures = sharedTextureHandles)
        }
        val block: UniformBlock
        val upload: ContentUpload
        if (group != null) {
            block = group.uniformBlock
            upload = group.upload
        } else {
            require(entry.users == 1) {
                "Content feature '${feature.name}' has the pipeline spec of one already attached, and " +
                    "this backend cannot give it a texture group of its own."
            }
            block = checkNotNull(entry.pipeline.uniformBlock) {
                "Content feature '${feature.name}' declares uniforms, so the factory must have " +
                    "allocated a block for its pipeline."
            }
            upload = if (sharedTextureHandles.isEmpty()) {
                if (includeGeometry) gpu.upload(entry.pipeline, feature) else gpu.upload(entry.pipeline, feature, includeGeometry = false)
            } else {
                gpu.upload(entry.pipeline, feature, includeGeometry = includeGeometry, sharedTextures = sharedTextureHandles)
            }
        }
        uploads += upload
        return gateContentFeature(
            feature,
            gpu.handle(entry.pipeline),
            entry.alternatePipeline?.let(gpu::handle),
            block,
            upload,
            uploads,
            sharedGeometry ?: upload.geometry,
        ) { depth, gate ->
            entry.depthCaster(depth)?.cast(gate)
        }
    }

    /** Immutable uploaded GPU geometry shared across features declaring the same [key]. */
    private class SharedGeometry(
        val key: Any,
        val upload: ContentUpload,
    ) {
        var users = 1
    }

    private data class SharedTextureKey(val key: Any, val mipSkip: Int)

    /** Immutable uploaded GPU texture shared across features declaring the same [key] and mip skip. */
    private class SharedTexture(
        val key: SharedTextureKey,
        val upload: ContentTextureUpload,
    ) {
        var users = 1
    }

    /** A registered pipeline and the depth pipelines built over it, with how many features use them. */
    private inner class SharedPipeline(
        val spec: PipelineSpec,
        val pipeline: P,
        val alternateSpec: PipelineSpec? = null,
        val alternatePipeline: P? = null,
    ) {
        var users = 1
        private val depth = HashMap<PipelineSpec, ContentDepthCaster?>()

        suspend fun depthCaster(spec: PipelineSpec): ContentDepthCaster? =
            if (spec in depth) depth[spec] else gpu.createDepthCaster(pipeline, spec).also { depth[spec] = it }

        fun destroyDepth() {
            depth.values.forEach { it?.destroy() }
            depth.clear()
        }
    }

    private inner class Attachment(
        val pipeline: SharedPipeline,
        val sharedGeometry: SharedGeometry?,
        val sharedTextures: List<SharedTexture>,
        val built: RenderFeature<RenderFrameContext>,
        val slot: AttachedContentSlot,
        val uploads: List<ContentUpload>,
    ) : AttachedContentFeature {
        override fun detach() {
            if (!attached.remove(this)) return
            slot.remove(built)
            // Earlier frames may still be reading the pipeline, geometry and textures freed below.
            gpu.awaitIdle()
            built.destroy()
            release(pipeline)
            sharedGeometry?.let(::releaseGeometry)
            sharedTextures.forEach(::releaseTexture)
            uploads.forEach(ContentUpload::release)
        }
    }
}
