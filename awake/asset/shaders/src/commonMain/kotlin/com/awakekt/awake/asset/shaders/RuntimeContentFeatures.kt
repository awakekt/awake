/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.command.UniformBlockOwner
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentGeometry
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.texture.TextureUploadRecorder

/**
 * A running engine that takes content features after start, for content that arrives with a
 * scene rather than with the [RenderPlan]. Reached by casting the app's renderer:
 *
 * ```kotlin
 * val terrain = (renderer as? ContentFeatureHost)?.attachContentFeature(source)
 * terrain?.detach()
 * ```
 *
 * Features with the same pipeline spec, several terrains with one shader set say, share one
 * pipeline; each keeps its own uniforms, textures, geometry and draw. One limit is rejected at
 * attach: a feature that samples scene depth or the mask, whose set layouts are fixed when the
 * engine starts.
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
 * @property prepare Publishes mutable images for the writable frame slot before render passes.
 * @param release Teardown callback invoked when releasing uploaded GPU resources.
 */
class ContentUpload(
    /** Vertex and index geometry uploaded to GPU buffers, or `null` if none. */
    val geometry: ContentGeometry?,
    val prepare: (Int, TextureUploadRecorder) -> Unit = { _, _ -> },
    private val release: () -> Unit,
) {
    constructor(geometry: ContentGeometry?, release: () -> Unit) : this(geometry, { _, _ -> }, release)

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

    /**
     * Uploads [feature] into a uniform block and texture group of its own, laid out as
     * [pipeline]'s group 0, so several features can draw through [pipeline] with their own
     * uniforms and textures. Releasing the returned upload frees the group.
     *
     * @return null when this backend binds only the pipeline's own group, in which case a second
     * feature with the same spec is refused.
     */
    fun uploadGroup(pipeline: P, feature: ContentFeature): ContentGroupUpload? = null

    /**
     * A depth pipeline built from [depth] over [pipeline]'s group 0, for every feature drawing
     * through [pipeline] to cast through. The default builds one per caster through [addDepthCaster].
     *
     * @return null when this engine renders no depth pass.
     */
    suspend fun createDepthCaster(pipeline: P, depth: PipelineSpec): ContentDepthCaster? =
        PerCasterDepthPipelines(this, pipeline, depth)
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
    return gateContentFeature(feature, handle(pipeline), block, upload, uploads) { depth, gate ->
        addDepthCaster(pipeline, depth, gate)
    }
}

/**
 * [feature] built over [block] and [upload], behind the pass gate, and cast into the depth pass
 * through [cast] when it declares a [ContentFeature.depth] pipeline. The cast's upload joins [uploads].
 */
internal suspend fun gateContentFeature(
    feature: ContentFeature,
    pipeline: PipelineHandle,
    block: UniformBlock,
    upload: ContentUpload,
    uploads: MutableList<ContentUpload>,
    cast: suspend (PipelineSpec, ContentDepthSource) -> ContentUpload?,
): RenderFeature<RenderFrameContext> {
    val gate = ContentFeaturePassGate(feature.build(pipeline, block, upload.geometry), upload)
    feature.depth?.let { depth ->
        require(gate.feature is ContentDepthSource) {
            "Content feature '${feature.name}' declares a depth pipeline, but the feature it builds " +
                "is not a ContentDepthSource, so the depth pass would have nothing to draw."
        }
        cast(depth, gate)?.let { uploads += it }
    }
    return gate
}

/**
 * Records [feature], and hands the depth pass its draw, only in passes whose environment lets
 * content features draw: one gate, so a pass that leaves them out leaves out their depth too.
 */
private class ContentFeaturePassGate(
    val feature: RenderFeature<RenderFrameContext>,
    private val upload: ContentUpload,
) : RenderFeature<RenderFrameContext>,
    ContentDepthSource {
    override val pass: RenderPassSlot get() = feature.pass

    override fun prepareFrame(frameIndex: Int, uploads: TextureUploadRecorder) {
        upload.prepare(frameIndex, uploads)
        feature.prepareFrame(frameIndex, uploads)
    }

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

    override fun prepareFrame(frameIndex: Int, uploads: TextureUploadRecorder) {
        for (index in features.indices) features[index].prepareFrame(frameIndex, uploads)
    }

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
