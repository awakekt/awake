/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.ContentFeatureSource
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.spec
import com.awakekt.awake.asset.shaders.stagesFor
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.MASK_LAYER_COUNT
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.OutlineFields
import com.awakekt.awake.render.renderer.OutlineStyle
import com.awakekt.awake.render.renderer.OutlineUniformLayout

private const val FULLSCREEN_TRIANGLE_VERTICES = 3

/** No outline: what a mask layer with no draws this frame writes. */
private val NO_COLOR = Color(0f, 0f, 0f, 0f)

/**
 * An outline around the silhouette of every masked draw, in its mask layer's style, drawn over the
 * scene. Pair it with `RenderPlan.maskShaderSet`, which draws the mask it reads; a draw joins the
 * mask through [com.awakekt.awake.render.command.GpuDrawRequest.maskLayer], and each layer's
 * [OutlineStyle] arrives in that layer's mask sub-pass. It draws nothing on a frame with nothing
 * masked.
 */
fun outlineContentFeature(): ContentFeatureSource = ContentFeatureSource { backend ->
    val stages = aslShaderSet(::outlineShader).stagesFor(backend)
    ContentFeature(
        name = "outline",
        spec = stages.spec(
            vertexFormat = VertexFormat.None,
            variant = PipelineVariant.Overlay,
            uniforms = OutlineUniformLayout,
        ),
        paint = ContentPaint.AfterGeometry,
        samplesMask = true,
    ) { pipeline, uniforms, _ -> OutlineRenderFeature(pipeline, uniforms) }
}

/** Records the outline overlay over the scene pass, when the frame has a mask to read. */
class OutlineRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    private val styles = arrayOfNulls<OutlineStyle>(MASK_LAYER_COUNT)

    override fun recordCommands(context: RenderFrameContext) {
        val input = context.passInput ?: return
        if (input.maskPasses.isEmpty()) return
        styles.fill(null)
        input.maskPasses.forEach { mask ->
            if (mask.targetLayer in styles.indices) styles[mask.targetLayer] = OutlineStyle.unpack(mask.passUniforms)
        }
        // The scene's own rectangle when it draws into part of the surface, as an editor's view does.
        val width = input.viewport?.width ?: context.surfaceWidth.toFloat()
        val height = input.viewport?.height ?: context.surfaceHeight.toFloat()
        uniforms.write(context.frameIndex) {
            put(OutlineFields.Viewport, width, height, 0f, 0f)
            put(OutlineFields.Color0, styles[0]?.color ?: NO_COLOR)
            put(OutlineFields.Color1, styles[1]?.color ?: NO_COLOR)
            put(OutlineFields.Widths, styles[0]?.widthPixels ?: 0f, styles[1]?.widthPixels ?: 0f, 0f, 0f)
        }
        val recorder: CommandRecorder = context.recorder
        recorder.bindPipeline(pipeline)
        recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(context.frameIndex))
        // Null on Vulkan, which binds the mask with the pipeline.
        context.engineBinding(pipeline, BindingSemantic.MaskDepth)?.let {
            recorder.bindMaterial(BindingSemantic.MaskDepth, it)
        }
        recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
    }

    /** Nothing: the registry owns the pipeline it built, and the block lives on it. */
    override fun destroy() = Unit
}
