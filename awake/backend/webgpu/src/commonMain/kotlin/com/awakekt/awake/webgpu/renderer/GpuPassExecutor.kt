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
            flushUniformUploads()
            swapchainManager.syncSurface()
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
            val encoder = createRenderEncoder()
            com.awakekt.awake.render.passes.prepareFrameResources(
                renderFeatures,
                0,
                com.awakekt.awake.webgpu.texture.TextureUploads(graphicsDevice),
            )
            val colorTexture = renderingContext.getCurrentTexture()
            val colorView = colorTexture.createView()
            depthPrePass?.recordCommands(encoder, input.prePasses, input.environment)
            sceneDepthPass?.recordCommands(
                encoder,
                sorted.allDraws,
                GpuShadowCascadeData(
                    viewProjections = listOf(input.viewProjection),
                    splitDistances = floatArrayOf(Float.MAX_VALUE),
                ),
            )
            // Nothing on a frame with nothing masked; the overlay reading it draws nothing either.
            maskPass?.recordCommands(encoder, input.maskPasses, input.environment)
            val scenePass = encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = swapchainManager.multisampleColorView ?: colorView,
                            resolveTarget = colorView.takeIf { swapchainManager.multisampleColorView != null },
                            loadOp = GPULoadOp.Clear,
                            clearValue = clearColorValue(input.environment),
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
            )
            sceneRect?.let { rect ->
                scenePass.setViewport(rect.x, rect.y, rect.width, rect.height, 0f, 1f)
                scenePass.setScissorRect(rect.x.toUInt(), rect.y.toUInt(), rect.width.toUInt(), rect.height.toUInt())
            }
            recordPassFeatures(
                renderFeatures,
                RenderPassSlot.Scene,
                sceneContext(
                    scenePass,
                    sorted,
                    primary.pipeline,
                    input.viewProjection,
                    input.cameraEye,
                    SurfaceSize(renderingContext.width.toInt(), renderingContext.height.toInt()),
                    input.environment,
                    input,
                ),
            )
            if (!input.postPassChain.isEmpty) {
                val parentTarget = com.awakekt.awake.webgpu.pipeline.WebGpuImagePassParentTarget(
                    colorTexture = colorTexture,
                    colorView = colorView,
                    colorFormat = swapchainManager.imageFormatWebGpu,
                    width = renderingContext.width.toInt(),
                    height = renderingContext.height.toInt(),
                    isOffscreen = false,
                    sampleable = true,
                )
                imagePassExecutor.executeImagePass(
                    commandEncoder = encoder,
                    currentPassEncoder = scenePass,
                    parentTarget = parentTarget,
                    chain = input.postPassChain,
                    stats = statsCounter,
                ).end()
            } else {
                scenePass.end()
            }
            recordPostPasses(encoder, input.postPasses)
            recordUiOverlay(
                encoder,
                colorTexture,
                colorView,
                sorted,
                primary.pipeline,
                input.viewProjection,
                input.cameraEye,
            )
            // Staged UI is one frame's: a frame that stages none draws none, not the last one again.
            uiRuns = emptyList()
            submitRenderCommands(encoder)
            colorView.close()
            presentedColor = PresentedColor(
                colorTexture,
                renderingContext.width.toInt(),
                renderingContext.height.toInt(),
                swapchainManager.imageFormatWebGpu,
            )
            gpuFrameTimer?.publish()
            statsCounter.publish(gpuFrameTimer?.lastMs)
        }
    }

    override fun renderToTexture(target: RenderTarget, input: GpuPassInput) {
        val renderer = this.renderer
        renderer.flushUniformUploads()
        val offscreen = target as OffscreenRenderTarget
        val primary = PrimaryPipelineBinding(pipeline = renderer.renderPipeline.handle, wireframe = false)
        check(input.resolvedPath) { "WebGPU requires a resolved GpuPassInput." }
        val sceneRect = input.viewport?.clampedTo(
            offscreen.width.toFloat(),
            offscreen.height.toFloat(),
        )
        val sorted = sortForRecording(input.resolvedDraws)
        val encoder = renderer.createRenderEncoder()
        com.awakekt.awake.render.passes.prepareFrameResources(
            renderer.renderFeatures,
            0,
            com.awakekt.awake.webgpu.texture.TextureUploads(renderer.graphicsDevice),
        )
        renderer.depthPrePass?.recordCommands(encoder, input.prePasses, input.environment)
        renderer.sceneDepthPass?.recordCommands(
            encoder,
            sorted.allDraws,
            GpuShadowCascadeData(
                viewProjections = listOf(input.viewProjection),
                splitDistances = floatArrayOf(Float.MAX_VALUE),
            ),
        )
        renderer.maskPass?.recordCommands(encoder, input.maskPasses, input.environment)
        val scenePass = encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = offscreen.sceneColorView,
                        resolveTarget = offscreen.sceneResolveView,
                        loadOp = GPULoadOp.Clear,
                        clearValue = renderer.clearColorValue(input.environment),
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
        )
        sceneRect?.let { rect ->
            scenePass.setViewport(rect.x, rect.y, rect.width, rect.height, 0f, 1f)
            scenePass.setScissorRect(rect.x.toUInt(), rect.y.toUInt(), rect.width.toUInt(), rect.height.toUInt())
        }
        recordPassFeatures(
            renderer.renderFeatures,
            RenderPassSlot.Scene,
            WebGpuFrameContext(
                renderer = renderer,
                encoder = scenePass,
                groupedDrawCalls = sorted.opaqueByPipeline,
                transparentDrawCalls = sorted.transparent,
                primaryPipeline = primary.pipeline,
                viewProjection = input.viewProjection,
                cameraEye = input.cameraEye,
                environment = input.environment,
                surfaceWidth = offscreen.width,
                surfaceHeight = offscreen.height,
                passInput = input,
            ),
        )
        if (!input.postPassChain.isEmpty) {
            val parentTarget = com.awakekt.awake.webgpu.pipeline.WebGpuImagePassParentTarget(
                colorTexture = offscreen.colorTexture,
                colorView = offscreen.colorView,
                colorFormat = offscreen.colorFormat,
                width = offscreen.width,
                height = offscreen.height,
                isOffscreen = true,
                sampleable = true,
            )
            renderer.imagePassExecutor.executeImagePass(
                commandEncoder = encoder,
                currentPassEncoder = scenePass,
                parentTarget = parentTarget,
                chain = input.postPassChain,
                stats = renderer.statsCounter,
            ).end()
        } else {
            scenePass.end()
        }
        recordPostPasses(encoder, input.postPasses)
        renderer.submitRenderCommands(encoder)
    }

    /** Records the UI overlay owned by this executor after the scene pass. */
    @Suppress("LongParameterList")
    internal fun recordUiOverlay(
        encoder: io.ygdrasil.webgpu.GPUCommandEncoder,
        colorTexture: io.ygdrasil.webgpu.GPUTexture,
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

        val width = renderingContext.width.toInt()
        val height = renderingContext.height.toInt()
        quadPipeline.writeScreenSize(width.toFloat(), height.toFloat())
        renderer.uiGlyphRenderPipeline?.writeScreenSize(width.toFloat(), height.toFloat())
        renderer.uiTextureRenderPipelines.values.forEach {
            it.writeScreenSize(width.toFloat(), height.toFloat())
        }
        renderer.uiRoundedQuadRenderPipeline?.writeScreenSize(width.toFloat(), height.toFloat())
        renderer.imagePassExecutor.beginPass()
        val initialPass = encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = colorView,
                        loadOp = GPULoadOp.Load,
                        storeOp = GPUStoreOp.Store,
                    ),
                ),
            ),
        )
        val context = renderer.sceneContext(
            encoder = initialPass,
            opaqueDraws = opaqueDraws,
            primaryPipeline = primaryPipeline,
            viewProjection = viewProjection,
            cameraEye = cameraEye,
            surface = SurfaceSize(width, height),
            commandEncoder = encoder,
            parentTarget = com.awakekt.awake.webgpu.pipeline.WebGpuImagePassParentTarget(
                colorTexture = colorTexture,
                colorView = colorView,
                colorFormat = renderer.swapchainManager.imageFormatWebGpu,
                width = width,
                height = height,
                isOffscreen = false,
                sampleable = true,
            ),
        )
        recordPassFeatures(
            renderer.renderFeatures,
            RenderPassSlot.Ui,
            context,
        )
        context.encoder.end()
    }
}

internal class PresentedColor(
    val texture: io.ygdrasil.webgpu.GPUTexture,
    val width: Int,
    val height: Int,
    val format: io.ygdrasil.webgpu.GPUTextureFormat,
)

@Suppress("UnusedParameter")
private fun recordPostPasses(
    encoder: io.ygdrasil.webgpu.GPUCommandEncoder,
    postPasses: List<com.awakekt.awake.render.command.GpuSubPass>,
) {
    if (postPasses.isEmpty()) return
}
