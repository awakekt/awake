/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuPassExecutor
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.allDraws
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.passes.recordPassFeatures
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.webgpu.texture.OffscreenRenderTarget
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.beginRenderPass

/** Transitional adapter while the recording body moves out of extension files. */
internal class RendererGpuPassExecutor(
    internal val renderer: Renderer,
) : GpuPassExecutor {
    override fun draw(input: GpuPassInput) {
        with(renderer) {
            swapchainManager.syncSurface()
            val device = graphicsDevice.wgpuContext.device
            val renderingContext = graphicsDevice.wgpuContext.renderingContext
            val useWireframe = wireframe && wireframeRenderPipeline != null
            val activeRenderPipeline = if (useWireframe) wireframeRenderPipeline!! else renderPipeline
            val primary = PrimaryPipelineBinding(activeRenderPipeline.handle, useWireframe)
            val sceneRect = input.viewport?.clampedTo(
                renderingContext.width.toFloat(),
                renderingContext.height.toFloat(),
            )
            check(input.resolvedPath) { "WebGPU requires a resolved GpuPassInput." }
            val resolved = input.resolvedDraws
            val sorted = sortForRecording(resolved)
            val encoder = device.createCommandEncoder()
            val colorView = renderingContext.getCurrentTexture().createView()
            depthPrePass?.recordCommands(encoder, input.prePasses)
            sceneDepthPass?.recordCommands(
                encoder,
                sorted.allDraws,
                GpuShadowCascadeData(
                    viewProjections = listOf(input.viewProjection),
                    splitDistances = floatArrayOf(Float.MAX_VALUE),
                ),
            )
            encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = colorView,
                            loadOp = GPULoadOp.Clear,
                            clearValue = clearColorValue,
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                    depthStencilAttachment = RenderPassDepthStencilAttachment(
                        view = requireNotNull(swapchainManager.depthTextureView),
                        depthClearValue = 1.0f,
                        depthLoadOp = GPULoadOp.Clear,
                        depthStoreOp = GPUStoreOp.Store,
                    ),
                ),
            ) {
                sceneRect?.let { rect ->
                    setViewport(rect.x, rect.y, rect.width, rect.height, 0f, 1f)
                    setScissorRect(rect.x.toUInt(), rect.y.toUInt(), rect.width.toUInt(), rect.height.toUInt())
                }
                recordPassFeatures(
                    renderFeatures,
                    RenderPassSlot.Scene,
                    sceneContext(
                        this,
                        sorted,
                        primary.pipeline,
                        input.viewProjection,
                        input.cameraEye,
                        SurfaceSize(renderingContext.width.toInt(), renderingContext.height.toInt()),
                        input.environment,
                    ),
                )
                end()
            }
            recordPostPasses(encoder, input.postPasses)
            recordUiOverlay(
                encoder,
                colorView,
                sorted,
                primary.pipeline,
                input.viewProjection,
                input.cameraEye,
            )
            device.queue.submit(listOf(encoder.finish()))
        }
    }

    override fun renderToTexture(target: RenderTarget, input: GpuPassInput) {
        val renderer = this.renderer
        val offscreen = target as OffscreenRenderTarget
        val device = renderer.graphicsDevice.wgpuContext.device
        val primary = PrimaryPipelineBinding(pipeline = renderer.renderPipeline.handle, wireframe = false)
        check(input.resolvedPath) { "WebGPU requires a resolved GpuPassInput." }
        val sceneRect = input.viewport?.clampedTo(
            offscreen.width.toFloat(),
            offscreen.height.toFloat(),
        )
        val sorted = sortForRecording(input.resolvedDraws)
        val encoder = device.createCommandEncoder()
        renderer.depthPrePass?.recordCommands(encoder, input.prePasses)
        renderer.sceneDepthPass?.recordCommands(
            encoder,
            sorted.allDraws,
            GpuShadowCascadeData(
                viewProjections = listOf(input.viewProjection),
                splitDistances = floatArrayOf(Float.MAX_VALUE),
            ),
        )
        encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = offscreen.colorView,
                        loadOp = GPULoadOp.Clear,
                        clearValue = renderer.clearColorValue,
                        storeOp = GPUStoreOp.Store,
                    ),
                ),
                depthStencilAttachment = RenderPassDepthStencilAttachment(
                    view = offscreen.depthView,
                    depthClearValue = 1.0f,
                    depthLoadOp = GPULoadOp.Clear,
                    depthStoreOp = GPUStoreOp.Store,
                ),
            ),
        ) {
            sceneRect?.let { rect ->
                setViewport(rect.x, rect.y, rect.width, rect.height, 0f, 1f)
                setScissorRect(rect.x.toUInt(), rect.y.toUInt(), rect.width.toUInt(), rect.height.toUInt())
            }
            recordPassFeatures(
                renderer.renderFeatures,
                RenderPassSlot.Scene,
                WebGpuFrameContext(
                    renderer = renderer,
                    encoder = this,
                    groupedDrawCalls = sorted.opaqueByPipeline,
                    transparentDrawCalls = sorted.transparent,
                    primaryPipeline = primary.pipeline,
                    viewProjection = input.viewProjection,
                    cameraEye = input.cameraEye,
                    environmentState = input.environment,
                    surfaceWidth = offscreen.width,
                    surfaceHeight = offscreen.height,
                ),
            )
            end()
        }
        recordPostPasses(encoder, input.postPasses)
        device.queue.submit(listOf(encoder.finish()))
    }

    /** Records the UI overlay owned by this executor after the scene pass. */
    internal fun recordUiOverlay(
        encoder: io.ygdrasil.webgpu.GPUCommandEncoder,
        colorView: io.ygdrasil.webgpu.GPUTextureView,
        opaqueDraws: com.awakekt.awake.render.command.SortedDraws<out com.awakekt.awake.render.command.PreparedDraw>,
        primaryPipeline: com.awakekt.awake.render.command.PipelineHandle,
        viewProjection: com.awakekt.awake.core.math.Mat4,
        cameraEye: com.awakekt.awake.core.math.Vec3f,
    ) {
        val renderer = this.renderer
        val renderingContext = renderer.graphicsDevice.wgpuContext.renderingContext
        val quadPipeline = renderer.uiRenderPipeline
        if (quadPipeline == null || renderer.uiRuns.isEmpty()) return

        quadPipeline.writeScreenSize(renderingContext.width.toFloat(), renderingContext.height.toFloat())
        renderer.uiGlyphRenderPipeline?.writeScreenSize(
            renderingContext.width.toFloat(),
            renderingContext.height.toFloat(),
        )
        renderer.uiTextureRenderPipelines.values.forEach {
            it.writeScreenSize(renderingContext.width.toFloat(), renderingContext.height.toFloat())
        }
        renderer.uiRoundedQuadRenderPipeline?.writeScreenSize(
            renderingContext.width.toFloat(),
            renderingContext.height.toFloat(),
        )
        encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = colorView,
                        loadOp = GPULoadOp.Load,
                        storeOp = GPUStoreOp.Store,
                    ),
                ),
            ),
        ) {
            recordPassFeatures(
                renderer.renderFeatures,
                RenderPassSlot.Ui,
                renderer.sceneContext(
                    this,
                    opaqueDraws,
                    primaryPipeline,
                    viewProjection,
                    cameraEye,
                    SurfaceSize(renderingContext.width.toInt(), renderingContext.height.toInt()),
                ),
            )
            end()
        }
    }
}

private fun recordPostPasses(
    encoder: io.ygdrasil.webgpu.GPUCommandEncoder,
    postPasses: List<com.awakekt.awake.render.command.GpuSubPass>,
) {
    if (postPasses.isEmpty()) return
}
