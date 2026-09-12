/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuPassExecutor
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget

/**
 * Transitional adapter around the existing lowering functions. Keeping this object behind
 * [Renderer] gives A4 a real ownership boundary while the large recording body is moved out of
 * the extension file incrementally.
 */
internal class RendererGpuPassExecutor(
    private val renderer: Renderer,
) : GpuPassExecutor {
    override fun draw(input: GpuPassInput) {
        val currentFrame = renderer.swapchainManager.currentFrame
        val imageIndex = renderer.acquireSwapchainImage(currentFrame) ?: return
        check(input.resolvedPath) { "Vulkan requires a resolved GpuPassInput." }
        val resolvedDraws = input.resolvedDraws
        val depthDraws = renderer.prepareDepthDraws(input)
        Vulkan.vkResetCommandBuffer(renderer.commandBuffers[currentFrame], 0)
        renderer.recordResolvedCommandBuffer(
            renderer.commandBuffers[currentFrame], currentFrame, imageIndex, resolvedDraws,
            input.viewProjection, input.cameraEye, depthDraws = depthDraws,
            prePasses = input.prePasses,
            postPasses = input.postPasses,
            environment = input.environment,
            sceneViewport = input.viewport,
        )
        renderer.submitAndPresent(currentFrame, imageIndex)
    }

    override fun renderToTexture(target: RenderTarget, input: GpuPassInput) {
        with(renderer) {
            val offscreen = target as OffscreenRenderTarget
            val sceneRect = input.viewport?.clampedTo(offscreen.width.toFloat(), offscreen.height.toFloat())
            check(input.resolvedPath) { "Vulkan requires a resolved GpuPassInput." }
            val resolvedDraws = input.resolvedDraws
            run {
                val sorted = sortForRecording(resolvedDraws)
                val depthDraws = prepareDepthDraws(input)
                runOffscreenCommands { commandBuffer ->
                    recordDepthPrePass(commandBuffer, depthDraws, input.prePasses, input.environment.shadowsEnabled)
                    recordSceneDepthPass(commandBuffer, depthDraws, cameraDepthPass(input.viewProjection))
                    offscreen.prepareForColorAttachment(commandBuffer)
                    Vulkan.vkCmdBeginRenderPass(
                        commandBuffer,
                        VkRenderPassBeginInfo(
                            renderPass = renderPipeline.renderPass,
                            framebuffer = offscreen.framebuffer,
                            renderArea = VkRect2D(extent = VkExtent2D(offscreen.width, offscreen.height)),
                            pClearValues = arrayOf(clearColorValue, Renderer.clearDepthValue),
                        ),
                        VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
                    )
                    Vulkan.vkCmdSetViewport(
                        commandBuffer,
                        0,
                        arrayOf(sceneRect?.toVkViewport() ?: VkViewport(width = offscreen.width.toFloat(), height = offscreen.height.toFloat())),
                    )
                    Vulkan.vkCmdSetScissor(
                        commandBuffer,
                        0,
                        arrayOf(sceneRect?.toVkScissor() ?: VkRect2D(extent = VkExtent2D(offscreen.width, offscreen.height))),
                    )
                    // The depth cascade pass uses set 1 for its pass-scoped matrix. Rebind the
                    // scene-owned shadow map before any lit pipeline is selected; offscreen
                    // recording has no Renderer.recordCommandBuffer helper to do this for it.
                    bindDepthSets(commandBuffer)
                    recordSharedPassFeatures(
                        RenderPassSlot.Scene,
                        VulkanFrameContext(
                            renderer = this@with,
                            commandBuffer = commandBuffer,
                            frameIndex = commandBuffers.size,
                            groupedDrawCalls = sorted.opaqueByPipeline,
                            transparentDrawCalls = sorted.transparent,
                            primaryPipeline = pipelineFor(renderPipeline.vertexFormat) ?: renderPipeline,
                            viewProjection = input.viewProjection,
                            cameraEye = input.cameraEye,
                            environmentState = input.environment,
                        ),
                    )
                    Vulkan.vkCmdEndRenderPass(commandBuffer)
                    recordPostPasses(commandBuffer, input.postPasses)
                    offscreen.transitionToShaderReadOnly(commandBuffer)
                }
            }
        }
    }
}

private fun Renderer.prepareDepthDraws(input: GpuPassInput): List<PreparedDraw> {
    if (input.prePasses.isEmpty() && sceneDepthTarget == null) return emptyList()
    return input.resolvedDraws
}
