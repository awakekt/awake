/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlockOwner
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineRequest
import com.awakekt.awake.render.pipeline.PipelineSpec

/**
 * A running engine that takes content features after start, for content that arrives with a
 * scene rather than with the [RenderPlan]. Reached by casting the app's renderer:
 *
 * ```kotlin
 * val terrain = (renderer as? ContentFeatureHost)?.attachContentFeature(source)
 * terrain?.detach()
 * ```
 *
 * Two limits, both rejected at attach: a feature that samples scene depth (its set layout is
 * fixed when the engine starts), and a second feature with the pipeline spec of one already
 * registered (the two would share one pipeline and one texture group).
 */
interface ContentFeatureHost {
    /** Builds [source] against the running engine; it draws from the next frame. */
    suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature
}

/** A content feature attached through [ContentFeatureHost]. */
fun interface AttachedContentFeature {
    /** Stops drawing it and frees its pipeline, textures and geometry. Safe to call twice. */
    fun detach()
}

/**
 * GPU resources uploaded for one content feature, and how to free them.
 *
 * @property geometry Vertex and index geometry uploaded to GPU buffers, or `null` if none.
 * @param release Teardown callback invoked when releasing uploaded GPU resources.
 */
class ContentUpload(
    /** Vertex and index geometry uploaded to GPU buffers, or `null` if none. */
    val geometry: ContentGeometry?,
    private val release: () -> Unit,
) {
    /** Releases uploaded GPU resources associated with this content feature. */
    fun release() = release.invoke()
}

/** The backend half of building a content feature: uploads and pipeline teardown. */
interface ContentFeatureGpu<P : UniformBlockOwner> {
    /** The target render backend providing hardware pipeline resources. */
    val backend: RenderBackend

    /** Registry mapping pipeline specifications to instantiated pipeline objects. */
    val registry: PipelineRegistry<P>

    /**
     * Resolves the low-level pipeline handle for the specified pipeline object.
     *
     * @param pipeline The typed pipeline owner.
     * @return Hardware handle wrapping the backend pipeline reference.
     */
    fun handle(pipeline: P): PipelineHandle

    /** Writes [feature]'s textures into [pipeline]'s group and uploads its geometry. */
    fun upload(pipeline: P, feature: ContentFeature): ContentUpload

    /**
     * Draws [source]'s draw into this backend's depth pass through a pipeline built from [depth]
     * over [pipeline]'s own group 0, so the feature's uniform block and textures bind unchanged.
     *
     * @return What frees that pipeline and stops the draw, released with the feature's uploads
     * once no frame can read it; null when this engine renders no depth pass.
     */
    suspend fun addDepthCaster(pipeline: P, depth: PipelineSpec, source: ContentDepthSource): ContentUpload?

    /**
     * Frees all GPU state and resources associated with the specified pipeline.
     *
     * @param pipeline The pipeline owner to destroy.
     */
    fun destroyPipeline(pipeline: P)

    /** Returns once no submitted frame can still read a resource about to be freed. */
    fun awaitIdle()
}

/**
 * [feature] built against the pipeline the registry already compiled for it, casting into the
 * depth pass when it declares a [ContentFeature.depth] pipeline. What it allocates joins [uploads].
 */
suspend fun <P : UniformBlockOwner> ContentFeatureGpu<P>.buildContentFeature(
    feature: ContentFeature,
    uploads: MutableList<ContentUpload>,
): RenderFeature<RenderFrameContext> {
    val pipeline = checkNotNull(registry[feature.spec]) {
        "Content feature '${feature.name}' was not registered before its feature was built."
    }
    val block = checkNotNull(pipeline.uniformBlock) {
        "Content feature '${feature.name}' declares uniforms, so the factory must have " +
            "allocated a block for its pipeline."
    }
    val upload = upload(pipeline, feature).also { uploads += it }
    val gate = ContentFeaturePassGate(feature.build(handle(pipeline), block, upload.geometry))
    feature.depth?.let { depth ->
        require(gate.feature is ContentDepthSource) {
            "Content feature '${feature.name}' declares a depth pipeline, but the feature it builds " +
                "is not a ContentDepthSource, so the depth pass would have nothing to draw."
        }
        addDepthCaster(pipeline, depth, gate)?.let { uploads += it }
    }
    return gate
}

/**
 * Records [feature], and hands the depth pass its draw, only in passes whose environment lets
 * content features draw: one gate, so a pass that leaves them out leaves out their depth too.
 */
private class ContentFeaturePassGate(
    val feature: RenderFeature<RenderFrameContext>,
) : RenderFeature<RenderFrameContext>,
    ContentDepthSource {
    override val pass: RenderPassSlot get() = feature.pass

    override fun recordCommands(context: RenderFrameContext) {
        if (context.environment.contentFeatures) feature.recordCommands(context)
    }

    override fun depthDraw(frameIndex: Int, environment: GpuEnvironmentState): PreparedDraw? =
        if (environment.contentFeatures) (feature as? ContentDepthSource)?.depthDraw(frameIndex, environment) else null

    override fun destroy() = feature.destroy()
}

/**
 * Attached content features recorded at one paint position in the scene pass. An engine places
 * one before and one after its opaque geometry, so the renderer's own feature list never changes.
 */
class AttachedContentSlot : RenderFeature<RenderFrameContext> {
    private val features = ArrayList<RenderFeature<RenderFrameContext>>()

    override val pass = RenderPassSlot.Scene

    internal fun add(feature: RenderFeature<RenderFrameContext>) {
        features += feature
    }

    internal fun remove(feature: RenderFeature<RenderFrameContext>) {
        features -= feature
    }

    override fun recordCommands(context: RenderFrameContext) {
        // Indexed: the record path allocates nothing per frame.
        for (index in features.indices) features[index].recordCommands(context)
    }

    override fun destroy() {
        features.forEach { it.destroy() }
        features.clear()
    }
}

/**
 * [ContentFeatureHost] over one engine's [gpu]. The engine records [beforeGeometry] and
 * [afterGeometry] in its scene pass and calls [releaseAll] after destroying its pipelines.
 */
class ContentFeatureAttacher<P : UniformBlockOwner>(private val gpu: ContentFeatureGpu<P>) : ContentFeatureHost {
    val beforeGeometry = AttachedContentSlot()
    val afterGeometry = AttachedContentSlot()
    private val attached = mutableListOf<Attachment>()

    // TooGenericExceptionCaught: whatever fails mid-build, what it allocated is released and the
    // failure rethrown unchanged.
    @Suppress("TooGenericExceptionCaught")
    override suspend fun attachContentFeature(source: ContentFeatureSource): AttachedContentFeature {
        val feature = source.resolve(gpu.backend)
        require(!feature.samplesSceneDepth) {
            "Content feature '${feature.name}' samples scene depth, whose set layout is fixed when " +
                "the engine starts. Declare it in the RenderPlan instead."
        }
        require(gpu.registry[feature.spec] == null) {
            "Content feature '${feature.name}' has the pipeline spec of one already registered; " +
                "the two would share one pipeline and one texture group."
        }
        gpu.registry.register(listOf(PipelineRequest(PipelineKey.Content(feature.name), feature.spec)))
        val uploads = mutableListOf<ContentUpload>()
        val built = try {
            gpu.buildContentFeature(feature, uploads).also {
                require(it.pass == RenderPassSlot.Scene) {
                    "Content feature '${feature.name}' records in ${it.pass}; attached features " +
                        "record in the scene pass."
                }
            }
        } catch (failure: Throwable) {
            // Nothing has recorded with them yet, so no frame can be reading them.
            gpu.registry.remove(feature.spec)?.let(gpu::destroyPipeline)
            uploads.forEach(ContentUpload::release)
            throw failure
        }
        val slot = if (feature.paint == ContentPaint.BeforeGeometry) beforeGeometry else afterGeometry
        slot.add(built)
        return Attachment(feature, built, slot, uploads).also { attached += it }
    }

    /** Frees every still-attached feature's uploads. For engine teardown, after the renderer
     * destroyed its features and the registry its pipelines. */
    fun releaseAll() {
        attached.forEach { it.uploads.forEach(ContentUpload::release) }
        attached.clear()
    }

    private inner class Attachment(
        val feature: ContentFeature,
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
            gpu.registry.remove(feature.spec)?.let(gpu::destroyPipeline)
            uploads.forEach(ContentUpload::release)
        }
    }
}
