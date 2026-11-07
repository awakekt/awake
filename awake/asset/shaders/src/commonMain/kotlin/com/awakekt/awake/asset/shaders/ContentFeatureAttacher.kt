/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.command.UniformBlockOwner
import com.awakekt.awake.render.passes.ContentFeature
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
 * [ContentFeatureHost] over one engine's [gpu]. The engine records [beforeGeometry] and
 * [afterGeometry] in its scene pass and calls [releaseAll] after destroying its pipelines.
 *
 * Features with the same pipeline spec share one registered pipeline, and one depth pipeline per
 * depth spec, counted by the features using them: the last to detach frees them. Each feature
 * keeps its own uniform block and texture group ([ContentFeatureGpu.uploadGroup]), geometry and
 * draw, so detaching one leaves the others drawing. A backend that cannot give a feature a group
 * of its own takes one feature per spec, as before.
 */
class ContentFeatureAttacher<P : UniformBlockOwner>(private val gpu: ContentFeatureGpu<P>) : ContentFeatureHost {
    val beforeGeometry = AttachedContentSlot()
    val afterGeometry = AttachedContentSlot()
    private val attached = mutableListOf<Attachment>()
    private val shared = HashMap<PipelineSpec, SharedPipeline>()

    /** Attaches register and build across suspensions; one at a time, so two of one spec share. */
    private val attaching = Mutex()

    override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature = attaching.withLock {
        val feature = source.resolve(gpu.backend)
        require(!feature.samplesSceneDepth && !feature.samplesMask) {
            "Content feature '${feature.name}' samples scene depth or the mask, whose set layouts are " +
                "fixed when the engine starts. Declare it in the RenderPlan instead."
        }
        val pipeline = acquire(feature)
        val uploads = mutableListOf<ContentUpload>()
        var builtWhole = false
        val built = try {
            build(pipeline, feature, uploads).also {
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
                uploads.forEach(ContentUpload::release)
            }
        }
        val slot = if (feature.paint == ContentPaint.BeforeGeometry) beforeGeometry else afterGeometry
        slot.add(built)
        Attachment(pipeline, built, slot, uploads).also { attached += it }
    }

    /** Frees every still-attached feature's uploads and every shared depth pipeline. For engine
     * teardown, after the renderer destroyed its features and the registry its pipelines. */
    fun releaseAll() {
        attached.forEach { it.uploads.forEach(ContentUpload::release) }
        attached.clear()
        shared.values.forEach { it.destroyDepth() }
        shared.clear()
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
        gpu.registry.register(listOf(PipelineRequest(PipelineKey.Content(feature.name), feature.spec)))
        val pipeline = checkNotNull(gpu.registry[feature.spec]) {
            "Content feature '${feature.name}' was not registered before its feature was built."
        }
        return SharedPipeline(feature.spec, pipeline).also { shared[feature.spec] = it }
    }

    /** One user fewer; the last one frees the depth pipelines and the pipeline. */
    private fun release(entry: SharedPipeline) {
        if (--entry.users > 0) return
        shared.remove(entry.spec)
        entry.destroyDepth()
        gpu.registry.remove(entry.spec)?.let(gpu::destroyPipeline)
    }

    private suspend fun build(
        entry: SharedPipeline,
        feature: ContentFeature,
        uploads: MutableList<ContentUpload>,
    ): RenderFeature<RenderFrameContext> {
        val group = gpu.uploadGroup(entry.pipeline, feature)
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
            upload = gpu.upload(entry.pipeline, feature)
        }
        uploads += upload
        return gateContentFeature(feature, gpu.handle(entry.pipeline), block, upload, uploads) { depth, gate ->
            entry.depthCaster(depth)?.cast(gate)
        }
    }

    /** A registered pipeline and the depth pipelines built over it, with how many features use them. */
    private inner class SharedPipeline(val spec: PipelineSpec, val pipeline: P) {
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
        val built: RenderFeature<RenderFrameContext>,
        val slot: AttachedContentSlot,
        val uploads: List<ContentUpload>,
    ) : AttachedContentFeature {
        override fun detach() {
            if (!attached.remove(this)) return
            slot.remove(built)
            // Earlier frames may still be reading the pipeline and textures freed below.
            gpu.awaitIdle()
            built.destroy()
            release(pipeline)
            uploads.forEach(ContentUpload::release)
        }
    }
}
