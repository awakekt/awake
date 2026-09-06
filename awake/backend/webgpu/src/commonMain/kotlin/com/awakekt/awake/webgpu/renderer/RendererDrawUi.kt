/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.graphics2d.TextureCompositeMode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.passes2d.SharedUiRenderFeature
import com.awakekt.awake.render.passes2d.UiMeshUploader
import com.awakekt.awake.render.passes2d.UiRunCoalescer
import com.awakekt.awake.render.passes2d.uploadUiRuns
import com.awakekt.awake.render.renderer.UiTargetCompositeMode
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.webgpu.pipeline.WebGpuUiRunRecorder
import com.awakekt.awake.webgpu.texture.OffscreenRenderTarget
import com.awakekt.awake.webgpu.ui.DynamicMesh
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.beginRenderPass

/**
 * UI primitive staging for the WebGPU backend.
 */
internal fun Renderer.performDrawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) {
    ensureUiQuadPipeline()
    if (font != null) ensureGlyphPipeline(font)
    primitives.filterIsInstance<UiDrawPrimitive.Texture>().map { TextureCompositeMode(it.blendMode, it.premultiplied) }.distinct()
        .forEach(::ensureTextureQuadPipeline)
    if (primitives.any { it is UiDrawPrimitive.RoundedQuad || it is UiDrawPrimitive.ShadowQuad }) ensureRoundedQuadPipeline()

    uiRuns = uploadUiRuns(
        UiRunCoalescer.coalesce(primitives, Renderer.MAX_UI_QUADS),
        WebGpuUiMeshUploader(this),
    )
}

/** Executes the shared, paint-order-preserving UI pass against an offscreen attachment. */
internal fun Renderer.performDrawUiToTexture(target: RenderTarget, primitives: List<UiDrawPrimitive>, font: UiFont?) {
    val offscreen = target as OffscreenRenderTarget
    performDrawUi(primitives, font)
    val quad = requireNotNull(uiRenderPipeline)
    quad.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    uiGlyphRenderPipeline?.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    uiTextureRenderPipelines.values.forEach {
        it.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    }
    uiRoundedQuadRenderPipeline?.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    val device = graphicsDevice.wgpuContext.device
    val encoder = device.createCommandEncoder()
    encoder.beginRenderPass(
        RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = offscreen.colorView,
                    loadOp = GPULoadOp.Clear,
                    // A graphics layer is composited over its parent. Clearing it with the
                    // scene colour would turn every uncovered pixel into an opaque rectangle.
                    clearValue = io.ygdrasil.webgpu.Color(0.0, 0.0, 0.0, 0.0),
                    storeOp = GPUStoreOp.Store,
                ),
            ),
        ),
    ) {
        SharedUiRenderFeature().recordCommands(
            runs = uiRuns,
            surfaceWidth = offscreen.width,
            surfaceHeight = offscreen.height,
            recorder = WebGpuUiRunRecorder(
                encoder = this,
                quad = quad,
                roundedQuad = uiRoundedQuadRenderPipeline,
                glyph = uiGlyphRenderPipeline,
                textures = uiTextureRenderPipelines,
                textureMeshForPrimitive = ::textureMeshForPrimitive,
            ),
        )
        end()
    }
    device.queue.submit(listOf(encoder.finish()))
}

/** Runs one sampled full-target composite; source and destination are never attached here. */
internal fun Renderer.performCompositeUiTargets(
    destination: RenderTarget,
    source: RenderTarget,
    output: RenderTarget,
    mode: UiTargetCompositeMode,
) {
    val destinationTarget = destination as OffscreenRenderTarget
    val sourceTarget = source as OffscreenRenderTarget
    val outputTarget = output as OffscreenRenderTarget
    require(
        destinationTarget.width == sourceTarget.width && sourceTarget.width == outputTarget.width &&
            destinationTarget.height == sourceTarget.height && sourceTarget.height == outputTarget.height,
    ) {
        "UI target composites require equal source, destination, and output dimensions."
    }
    require(outputTarget !== destinationTarget && outputTarget !== sourceTarget) {
        "UI target composite output must not alias a sampled input target."
    }
    val pipeline = uiTargetCompositePipelines.getOrPut(mode) {
        com.awakekt.awake.webgpu.ui.UiTargetCompositePipeline(
            graphicsDevice,
            swapchainManager,
            uiTargetCompositeShaderCode,
            mode,
        )
    }
    val device = graphicsDevice.wgpuContext.device
    val encoder = device.createCommandEncoder()
    encoder.beginRenderPass(
        RenderPassDescriptor(
            colorAttachments = listOf(
                RenderPassColorAttachment(
                    view = outputTarget.colorView,
                    loadOp = GPULoadOp.Clear,
                    clearValue = io.ygdrasil.webgpu.Color(0.0, 0.0, 0.0, 0.0),
                    storeOp = GPUStoreOp.Store,
                ),
            ),
        ),
    ) {
        setPipeline(pipeline.pipeline)
        setBindGroup(0u, pipeline.bindGroupFor(sourceTarget, destinationTarget))
        draw(3u)
        end()
    }
    device.queue.submit(listOf(encoder.finish()))
}

/** WebGPU's mesh allocation for [uploadUiRuns]. Single-buffered, so no frame index. */
private class WebGpuUiMeshUploader(private val renderer: Renderer) : UiMeshUploader<DynamicMesh> {
    override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): DynamicMesh =
        renderer.quadMeshForRun(runIndex).also { it.update(vertices, indices) }

    override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): DynamicMesh =
        renderer.roundedQuadMeshForRun(runIndex).also { it.update(vertices, indices) }

    override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): DynamicMesh =
        renderer.glyphMeshForRun(runIndex).also { it.update(vertices, indices) }
}
