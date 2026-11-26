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
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.MASK_LAYER_COUNT
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.passes.ContentFeature
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.passes.RenderFeature
import com.awakekt.awake.render.passes.RenderFrameContext
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.renderer.OUTLINE_MIN_WIDTH_PIXELS
import com.awakekt.awake.render.renderer.OutlineFields
import com.awakekt.awake.render.renderer.OutlineUniformLayout
import com.awakekt.awake.render.renderer.UniformWriter

private const val FULLSCREEN_TRIANGLE_VERTICES = 3
private const val MIN_PACKED_STYLE_SIZE = 5
private const val RED_INDEX = 0
private const val GREEN_INDEX = 1
private const val BLUE_INDEX = 2
private const val ALPHA_INDEX = 3
private const val WIDTH_INDEX = 4
private const val SOFTNESS_INDEX = 5
private const val SOFTNESS_EPSILON = 0.001f

/**
 * Maximum border width (in pixels) when [EffectQuality.Low] downgrades a soft Gaussian glow
 * (`softness > 0`, 64 depth taps/pixel) to a crisp 16-tap silhouette (`softness = 0`).
 * Already-crisp outlines keep their authored width untouched.
 */
private const val LOW_SOFT_WIDTH_CAP = 2f

/**
 * An outline around the silhouette of every masked draw, in its mask layer's style, drawn over the
 * scene. Pair it with `RenderPlan.maskShaderSet`, which draws the mask it reads; a draw joins the
 * mask through [com.awakekt.awake.render.command.GpuDrawRequest.maskLayer], and each layer's
 * [com.awakekt.awake.render.renderer.OutlineStyle] arrives in that layer's mask sub-pass.
 *
 * Honors [RenderFrameContext.effectQuality] with zero per-frame allocations:
 * - [EffectQuality.Off]: skips the outline overlay draw completely (0 samples).
 * - [EffectQuality.Low]: converts soft 64-tap Gaussian glows (`softness > 0`) to crisp 16-tap
 *   silhouettes (`softness = 0`, width capped at 2 px) to protect mobile tile GPUs while keeping
 *   already-crisp outlines at their authored width.
 * - [EffectQuality.Medium] / [EffectQuality.High]: draws the full authored outline style.
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

    // Flat primitive per-layer state so decoding passUniforms allocates zero Color/OutlineStyle objects per frame.
    private val layerR = FloatArray(MASK_LAYER_COUNT)
    private val layerG = FloatArray(MASK_LAYER_COUNT)
    private val layerB = FloatArray(MASK_LAYER_COUNT)
    private val layerA = FloatArray(MASK_LAYER_COUNT)
    private val layerWidth = FloatArray(MASK_LAYER_COUNT)
    private val layerSoftness = FloatArray(MASK_LAYER_COUNT)
    private val layerDraws = BooleanArray(MASK_LAYER_COUNT)
    private val scissor = OutlineScissor()
    private var viewportWidth = 0f
    private var viewportHeight = 0f
    private val fillUniforms: UniformWriter.() -> Unit = {
        put(OutlineFields.Viewport, viewportWidth, viewportHeight, 0f, 0f)
        put(OutlineFields.Color0, layerR[0], layerG[0], layerB[0], layerA[0])
        put(OutlineFields.Color1, layerR[1], layerG[1], layerB[1], layerA[1])
        put(OutlineFields.Widths, layerWidth[0], layerWidth[1], layerSoftness[0], layerSoftness[1])
    }

    override fun recordCommands(context: RenderFrameContext) {
        val input = context.passInput
        if (context.effectQuality == EffectQuality.Off || input == null || !styledOnScreen(context, input)) return
        viewportWidth = sceneWidth(context, input)
        viewportHeight = sceneHeight(context, input)
        uniforms.write(context.frameIndex, fillUniforms)
        val recorder: CommandRecorder = context.recorder
        recorder.bindPipeline(pipeline)
        recorder.bindMaterial(BindingSemantic.Material, uniforms.binding(context.frameIndex))
        // Null on Vulkan, which binds the mask with the pipeline.
        context.engineBinding(pipeline, BindingSemantic.MaskDepth)?.let {
            recorder.bindMaterial(BindingSemantic.MaskDepth, it)
        }
        if (scissor.narrowed) recorder.setScissor(scissor.x, scissor.y, scissor.width, scissor.height)
        recorder.draw(FULLSCREEN_TRIANGLE_VERTICES, 1)
        if (scissor.narrowed) recorder.setScissor(scissor.fullX, scissor.fullY, scissor.fullWidth, scissor.fullHeight)
    }

    /**
     * Reads each mask layer's packed style into primitive arrays, applies [EffectQuality] scaling,
     * and fits [scissor] around active styled draws. Allocates nothing per frame.
     */
    private fun styledOnScreen(context: RenderFrameContext, input: GpuPassInput): Boolean {
        clearLayers()
        val downgradeSoft = context.effectQuality == EffectQuality.Low
        var anyDraws = false
        val masks = input.maskPasses
        for (i in masks.indices) {
            val mask = masks[i]
            val layer = mask.targetLayer
            val packed = mask.passUniforms
            if (layer in layerDraws.indices && packed.size >= MIN_PACKED_STYLE_SIZE) {
                anyDraws = decodeLayer(layer, packed, downgradeSoft) || anyDraws
            }
        }
        return anyDraws &&
            scissor.fit(
                masks = masks,
                draws = layerDraws,
                widths = layerWidth,
                left = input.viewport?.x ?: 0f,
                top = input.viewport?.y ?: 0f,
                sceneWidth = sceneWidth(context, input),
                sceneHeight = sceneHeight(context, input),
            )
    }

    private fun clearLayers() {
        for (layer in 0 until MASK_LAYER_COUNT) {
            layerR[layer] = 0f
            layerG[layer] = 0f
            layerB[layer] = 0f
            layerA[layer] = 0f
            layerWidth[layer] = 0f
            layerSoftness[layer] = 0f
            layerDraws[layer] = false
        }
    }

    private fun decodeLayer(layer: Int, packed: FloatArray, downgradeSoft: Boolean): Boolean {
        val rawWidth = packed[WIDTH_INDEX]
        val rawSoftness = if (packed.size > SOFTNESS_INDEX) packed[SOFTNESS_INDEX].coerceIn(0f, 1f) else 0f
        val effectiveSoftness = if (downgradeSoft && rawSoftness > SOFTNESS_EPSILON) 0f else rawSoftness
        val effectiveWidth = if (downgradeSoft && rawSoftness > SOFTNESS_EPSILON) {
            minOf(rawWidth, LOW_SOFT_WIDTH_CAP)
        } else {
            rawWidth
        }
        val alpha = packed[ALPHA_INDEX]
        layerR[layer] = packed[RED_INDEX]
        layerG[layer] = packed[GREEN_INDEX]
        layerB[layer] = packed[BLUE_INDEX]
        layerA[layer] = alpha
        layerWidth[layer] = effectiveWidth
        layerSoftness[layer] = effectiveSoftness
        val draws = effectiveWidth >= OUTLINE_MIN_WIDTH_PIXELS && alpha > 0f
        layerDraws[layer] = draws
        return draws
    }

    // The scene's own rectangle when it draws into part of the surface, as an editor's view does.
    private fun sceneWidth(context: RenderFrameContext, input: GpuPassInput) = input.viewport?.width ?: context.surfaceWidth.toFloat()

    private fun sceneHeight(context: RenderFrameContext, input: GpuPassInput) = input.viewport?.height ?: context.surfaceHeight.toFloat()

    /** Nothing: the registry owns the pipeline it built, and the block lives on it. */
    override fun destroy() = Unit
}
