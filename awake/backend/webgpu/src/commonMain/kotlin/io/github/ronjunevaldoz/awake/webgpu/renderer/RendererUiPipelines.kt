// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.renderer

import io.github.ronjunevaldoz.awake.render.passes2d.UiPipelineKind
import io.github.ronjunevaldoz.awake.render.passes.uniforms.MaterialUniformLayouts
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.webgpu.pipeline.WebGpuBindGroupHandle
import io.github.ronjunevaldoz.awake.webgpu.ui.UiRenderPipeline
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPURenderPipeline

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
        kind = UiPipelineKind.Quad,
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
        kind = UiPipelineKind.Glyph,
        font = font,
    )
}

/** Builds [Renderer.uiTextureRenderPipeline] on the first `drawUi` call that has any
 * [io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive.Texture] primitives -- cached after that. */
internal fun Renderer.ensureTextureQuadPipeline() {
    if (uiTextureRenderPipeline != null) return
    uiTextureRenderPipeline = UiRenderPipeline(
        graphicsDevice,
        swapchainManager,
        uiTextureShaderCode,
        kind = UiPipelineKind.Texture,
    )
}

/** Builds [Renderer.uiRoundedQuadRenderPipeline] on the first `drawUi` call that has any
 * [io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive.RoundedQuad] primitives outside an active
 * convex-path clip -- cached after that. Mirrors Vulkan's `ensureRoundedQuadPipeline()`. */
internal fun Renderer.ensureRoundedQuadPipeline() {
    if (uiRoundedQuadRenderPipeline != null) return
    uiRoundedQuadRenderPipeline = UiRenderPipeline(
        graphicsDevice,
        swapchainManager,
        uiRoundedQuadShaderCode,
        kind = UiPipelineKind.RoundedQuad,
    )
}

// Derived, not counted by hand: MaterialUniformLayouts.Primary IS triangle.wgsl's Uniforms
// struct, so a field added there resizes this buffer automatically.
private val UNIFORM_FLOAT_COUNT = MaterialUniformLayouts.Primary.total

/** [Renderer.instancedPipelines]' own uniform buffer/bind group -- a third near-duplicate, for
 * the exact "auto" pipeline-layout reason: a bind group is only valid against the pipeline layout it came from.
 * One pair serves every instanced pipeline/draw call: they all write the same `viewProjection` +
 * light block (see `instanced.wgsl`), so unlike the per-draw `mvp` this class writes elsewhere
 * there is nothing here for a second instanced call to clobber. */
internal fun Renderer.ensureInstancedUniformResources(pipeline: GPURenderPipeline) {
    if (instancedUniformBuffer != null) return
    val device = graphicsDevice.wgpuContext.device
    val buffer = device.createBuffer(
        BufferDescriptor(
            size = (UNIFORM_FLOAT_COUNT * Float.SIZE_BYTES).toULong(),
            usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
        ),
    )
    instancedUniformBuffer = buffer
    instancedUniformBindGroup = device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = listOf(
                BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer)),
            ),
        ),
    )
    instancedUniformBinding = WebGpuBindGroupHandle(instancedUniformBindGroup!!)
}

/** [Renderer.skinnedInstancedPipelines]' own pair of the same -- a separate one from
 * [ensureInstancedUniformResources]' despite writing an identical uniform block, because a bind
 * group is only valid against the pipeline layout it was derived from and this is a different
 * pipeline object (the same reason wireframe needs its own pair). */
internal fun Renderer.ensureSkinnedInstancedUniformResources(pipeline: GPURenderPipeline) {
    if (skinnedInstancedUniformBuffer != null) return
    val device = graphicsDevice.wgpuContext.device
    val buffer = device.createBuffer(
        BufferDescriptor(
            size = (UNIFORM_FLOAT_COUNT * Float.SIZE_BYTES).toULong(),
            usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
        ),
    )
    skinnedInstancedUniformBuffer = buffer
    skinnedInstancedUniformBindGroup = device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = listOf(
                BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer)),
            ),
        ),
    )
    skinnedInstancedUniformBinding = WebGpuBindGroupHandle(skinnedInstancedUniformBindGroup!!)
}
