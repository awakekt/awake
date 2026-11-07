/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.command.UniformBlockOwner
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.pipeline.PipelineSpec

/**
 * One content feature's own uniform block and texture group, laid out as a shared pipeline's
 * group 0, with what was uploaded into it. See [ContentFeatureGpu.uploadGroup].
 *
 * @property uniformBlock The feature's uniforms and textures, bound wherever the pipeline's own
 * group would be.
 * @property upload The feature's textures and geometry; releasing it frees the group too.
 */
class ContentGroupUpload(
    val uniformBlock: UniformBlock,
    val upload: ContentUpload,
)

/**
 * A depth pipeline built once over a content pipeline's group 0, that every feature drawing
 * through that pipeline casts into the depth pass through. See [ContentFeatureGpu.createDepthCaster].
 */
interface ContentDepthCaster {
    /**
     * Draws [source]'s depth draw in every rendered sub-pass from the next frame.
     *
     * @return What stops it, released with the feature's other uploads; null when the engine
     * renders no depth pass.
     */
    suspend fun cast(source: ContentDepthSource): ContentUpload?

    /** Frees the depth pipeline, once nothing casts through it. */
    fun destroy()
}

/**
 * The fallback for a backend that builds one depth pipeline per caster: each [cast] is a whole
 * [ContentFeatureGpu.addDepthCaster], and its upload frees that pipeline.
 */
internal class PerCasterDepthPipelines<P : UniformBlockOwner>(
    private val gpu: ContentFeatureGpu<P>,
    private val pipeline: P,
    private val depth: PipelineSpec,
) : ContentDepthCaster {
    override suspend fun cast(source: ContentDepthSource): ContentUpload? = gpu.addDepthCaster(pipeline, depth, source)

    override fun destroy() = Unit
}
