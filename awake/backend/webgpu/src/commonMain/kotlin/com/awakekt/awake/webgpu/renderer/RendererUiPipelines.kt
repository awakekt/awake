/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.asset.shaders.program
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.TextureCompositeMode
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.render.font.UiFontSamplingInfo
import com.awakekt.awake.render.font.samplingInfo
import com.awakekt.awake.render.passes2d.UiPipelineKind
import com.awakekt.awake.render.pipeline.UiPipelineDescriptor
import com.awakekt.awake.webgpu.ui.UiRenderPipeline

/** Lazy construction of every UI-overlay graphics pipeline `performDrawUi` ([RendererDrawUi.kt])
 * needs -- each is built only on the first call that actually needs it, so a game that never
 * draws UI, glyphs, textured quads, or rounded quads never pays for the pipeline it doesn't
 * use. See [Renderer]'s class doc comment for why this lives in a sibling file as `internal`
 * extension functions rather than as members. */

/** Builds [Renderer.uiRenderPipeline] on the first `drawUi` call of any kind -- quad
 * rendering doesn't need a font, so this doesn't wait for one. Cached after the first build
 * (a game calls `drawUi` every frame). */
internal fun Renderer.ensureUiQuadPipeline() {
    if (uiRenderPipeline != null) return
    uiRenderPipeline = UiRenderPipeline(
        graphicsDevice,
        swapchainManager,
        uiShaderCode,
        descriptor = uiPipelineDescriptor(UiPipelineKind.Quad),
    )
}

/** Builds [Renderer.uiGlyphRenderPipeline] on the first `drawUi` call that passes a non-null
 * [font] -- cached after that (a game calls `drawUi` with the same font every frame). */
internal fun Renderer.ensureGlyphPipeline(font: UiFont) {
    if (currentUiFont !== font) {
        uiGlyphRenderPipeline?.destroy()
        uiGlyphRenderPipeline = null
        currentUiFont = font
    }
    if (uiGlyphRenderPipeline != null) return
    uiGlyphRenderPipeline = UiRenderPipeline(
        graphicsDevice,
        swapchainManager,
        uiGlyphShaderCode,
        descriptor = uiPipelineDescriptor(UiPipelineKind.Glyph),
        font = font,
    ).also { it.writeFontInfo(font.samplingInfo) }
}

/** Writes the two glyph-shader UBO font fields from [font]'s declared sampling attributes. */
private fun UiRenderPipeline.writeFontInfo(info: UiFontSamplingInfo) {
    writeFontInfo(
        isDistanceField = info.mode == UiFontSamplingMode.DistanceField,
        rangePx = info.distanceFieldRangePx,
    )
}

/** Builds [Renderer.uiTextureRenderPipeline] on the first `drawUi` call that has any
 * [com.awakekt.awake.core.graphics2d.UiDrawPrimitive.Texture] primitives -- cached after that. */
internal fun Renderer.ensureTextureQuadPipeline(mode: TextureCompositeMode) {
    if (uiTextureRenderPipelines.containsKey(mode)) return
    uiTextureRenderPipelines[mode] = UiRenderPipeline(
        graphicsDevice,
        swapchainManager,
        uiTextureShaderCode,
        descriptor = uiPipelineDescriptor(UiPipelineKind.Texture, mode.blendMode, mode.premultiplied),
    )
}

/** Builds [Renderer.uiRoundedQuadRenderPipeline] on the first `drawUi` call that has any
 * [com.awakekt.awake.core.graphics2d.UiDrawPrimitive.RoundedQuad] primitives outside an active
 * convex-path clip -- cached after that. Mirrors Vulkan's `ensureRoundedQuadPipeline()`. */
internal fun Renderer.ensureRoundedQuadPipeline() {
    if (uiRoundedQuadRenderPipeline != null) return
    uiRoundedQuadRenderPipeline = UiRenderPipeline(
        graphicsDevice,
        swapchainManager,
        uiRoundedQuadShaderCode,
        descriptor = uiPipelineDescriptor(UiPipelineKind.RoundedQuad),
    )
}

private fun uiPipelineDescriptor(
    kind: UiPipelineKind,
    blendMode: BlendMode = BlendMode.SourceOver,
    isPremultiplied: Boolean = false,
) = UiPipelineDescriptor(
    variant = kind.pipelineVariant,
    vertexFormat = kind.vertexFormat,
    blendMode = blendMode,
    isPremultiplied = isPremultiplied,
)

internal fun Renderer.activeUiPipelineTargets(): List<com.awakekt.awake.webgpu.pipeline.WebGpuUiPipelineTarget> = buildList {
    uiRenderPipeline?.let { add(webGpuUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiQuad.webGpu.program())) }
    uiGlyphRenderPipeline?.let { add(webGpuUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiGlyph.webGpu.program())) }
    uiRoundedQuadRenderPipeline?.let { add(webGpuUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiRoundedQuad.webGpu.program())) }
    uiTextureRenderPipelines.values.forEach { add(webGpuUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiTexture.webGpu.program())) }
    uiTargetCompositePipelines.values.forEach { add(webGpuUiTargetComposite(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiTargetComposite.webGpu.program())) }
}

private fun webGpuUiTarget(
    pipeline: UiRenderPipeline,
    program: com.awakekt.awake.render.pipeline.ShaderProgram,
): com.awakekt.awake.webgpu.pipeline.WebGpuUiPipelineTarget = object : com.awakekt.awake.webgpu.pipeline.WebGpuUiPipelineTarget {
    override val identity: Any = pipeline
    override val program: com.awakekt.awake.render.pipeline.ShaderProgram = program
    override val bindingsByGroup: Map<Int, com.awakekt.awake.render.pipeline.GroupBindings> =
        checkNotNull(program.bindingsByGroup) { "UI shader program must declare bindingsByGroup" }

    override fun buildPipeline(
        wgslSource: String,
        vertexEntryPoint: String,
        fragmentEntryPoint: String,
    ): io.ygdrasil.webgpu.GPURenderPipeline = pipeline.buildPipeline(wgslSource, vertexEntryPoint, fragmentEntryPoint)

    override fun swapIn(
        newPipeline: io.ygdrasil.webgpu.GPURenderPipeline,
    ): io.ygdrasil.webgpu.GPURenderPipeline = pipeline.swapIn(newPipeline)
}

private fun webGpuUiTargetComposite(
    pipeline: com.awakekt.awake.webgpu.ui.UiTargetCompositePipeline,
    program: com.awakekt.awake.render.pipeline.ShaderProgram,
): com.awakekt.awake.webgpu.pipeline.WebGpuUiPipelineTarget = object : com.awakekt.awake.webgpu.pipeline.WebGpuUiPipelineTarget {
    override val identity: Any = pipeline
    override val program: com.awakekt.awake.render.pipeline.ShaderProgram = program
    override val bindingsByGroup: Map<Int, com.awakekt.awake.render.pipeline.GroupBindings> =
        checkNotNull(program.bindingsByGroup) { "UI shader program must declare bindingsByGroup" }

    override fun buildPipeline(
        wgslSource: String,
        vertexEntryPoint: String,
        fragmentEntryPoint: String,
    ): io.ygdrasil.webgpu.GPURenderPipeline = pipeline.buildPipeline(wgslSource, vertexEntryPoint, fragmentEntryPoint)

    override fun swapIn(
        newPipeline: io.ygdrasil.webgpu.GPURenderPipeline,
    ): io.ygdrasil.webgpu.GPURenderPipeline = pipeline.swapIn(newPipeline)
}
