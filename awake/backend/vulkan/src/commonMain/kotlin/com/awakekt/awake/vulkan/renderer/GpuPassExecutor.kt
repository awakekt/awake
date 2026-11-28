/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.render.command.GpuPassExecutor
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.drawsForRecording
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.gen.VulkanCommands
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import kotlin.math.ceil

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
        // An offscreen View submitted earlier in this frame was prepared against this slot and may
        // still read its camera, light and shadow blocks on the GPU. Preparing a draw waits for it
        // (VulkanDrawPreparer.startBatch), but a frame whose camera prepared none, or one presented
        // without a scene, never got there (#1687). Free when nothing is outstanding or it already waited.
        renderer.awaitSubmittedOffscreenCommandsFor(currentFrame)
        renderer.settleShadowArray(input)
        renderer.settleMaskTarget(input)
        renderer.graphicsDevice.frameUniforms.write(currentFrame, input)
        val depthDraws = renderer.prepareDepthDraws(input)
        Vulkan.vkResetCommandBuffer(renderer.commandBuffers[currentFrame], 0)
        renderer.recordResolvedCommandBuffer(
            renderer.commandBuffers[currentFrame], currentFrame, imageIndex, emptyList(),
            input.viewProjection, input.cameraEye, depthDraws = depthDraws,
            prePasses = input.prePasses,
            postPasses = input.postPasses,
            environment = input.environment,
            sceneViewport = input.viewport,
            passInput = input,
        )
        // Staged UI is one frame's: a frame that stages none draws none, not the last one again.
        renderer.uiRuns = emptyList()
        renderer.submitAndPresent(currentFrame, imageIndex)
    }

    override fun renderToTexture(target: RenderTarget, input: GpuPassInput) {
        renderer.runOffscreenCommands(block = renderer.recordToTexture(target, input))
    }

    override fun submitToTexture(target: RenderTarget, input: GpuPassInput) {
        renderer.submitOffscreenCommands(renderer.submittedSlotFor(input), renderer.recordToTexture(target, input))
    }
}

/** The commands that render [input] into offscreen [target], for either offscreen runner. */
private fun Renderer.recordToTexture(target: RenderTarget, input: GpuPassInput): (Long) -> Unit {
    ensureShadowLayers(input)
    val offscreen = target as OffscreenRenderTarget
    ensureMaskTarget(offscreen.width, offscreen.height, input)
    val sceneRect = input.viewport?.clampedTo(offscreen.width.toFloat(), offscreen.height.toFloat())
    check(input.resolvedPath) { "Vulkan requires a resolved GpuPassInput." }
    val sorted = input.drawsForRecording()
    val depthDraws = prepareDepthDraws(input)
    return { commandBuffer ->
        prepareResources(commandBuffers.size, commandBuffer)
        graphicsDevice.frameUniforms.write(commandBuffers.size, input)
        // The offscreen frame's own slots, past the last frame in flight, as its materials use.
        recordDepthPrePass(commandBuffer, commandBuffers.size, depthDraws, input.prePasses, input.environment)
        recordSceneDepthPass(commandBuffer, commandBuffers.size, depthDraws, cameraDepthPass(input.viewProjection), input.environment)
        recordMaskPass(commandBuffer, commandBuffers.size, input.maskPasses, input.environment)
        offscreen.prepareForColorAttachment(commandBuffer)
        val clearRgba = input.environment.clearColorOr(clearColor)
        VulkanCommands.vkCmdBeginRenderPassColorDepth(
            commandBuffer = commandBuffer,
            renderPass = renderPipeline.renderPass,
            framebuffer = offscreen.framebuffer,
            width = offscreen.width,
            height = offscreen.height,
            r = clearRgba.r,
            g = clearRgba.g,
            b = clearRgba.b,
            a = clearRgba.a,
            depth = 1f,
        )
        if (sceneRect != null) {
            VulkanCommands.vkCmdSetViewportRect(commandBuffer, sceneRect.x, sceneRect.y, sceneRect.width, sceneRect.height, 0f, 1f)
            VulkanCommands.vkCmdSetScissorRect(commandBuffer, sceneRect.x.toInt(), sceneRect.y.toInt(), ceil(sceneRect.width).toInt(), ceil(sceneRect.height).toInt())
        } else {
            VulkanCommands.vkCmdSetViewportRect(commandBuffer, 0f, 0f, offscreen.width.toFloat(), offscreen.height.toFloat(), 0f, 1f)
            VulkanCommands.vkCmdSetScissorRect(commandBuffer, 0, 0, offscreen.width, offscreen.height)
        }
        // The depth cascade pass uses set 1 for its pass-scoped matrix. Rebind the
        // scene-owned shadow map before any lit pipeline is selected; offscreen
        // recording has no Renderer.recordCommandBuffer helper to do this for it.
        bindDepthSets(commandBuffer)
        recordSharedPassFeatures(
            RenderPassSlot.Scene,
            VulkanFrameContext(
                renderer = this,
                commandBuffer = commandBuffer,
                frameIndex = commandBuffers.size,
                groupedDrawCalls = sorted.opaqueByPipeline,
                transparentDrawCalls = sorted.transparent,
                primaryPipeline = pipelineFor(renderPipeline.vertexFormat) ?: renderPipeline,
                viewProjection = input.viewProjection,
                cameraEye = input.cameraEye,
                environment = input.environment,
                passInput = input,
                surfaceWidth = offscreen.width,
                surfaceHeight = offscreen.height,
            ),
        )
        Vulkan.vkCmdEndRenderPass(commandBuffer)
        recordPostPassesToTexture(commandBuffer, offscreen, input)
        offscreen.transitionToShaderReadOnly(commandBuffer)
    }
}

private fun Renderer.prepareDepthDraws(input: GpuPassInput): List<PreparedDraw> {
    val shadowActive = input.environment.shadowsEnabled && input.prePasses.isNotEmpty()
    val sceneDepthActive = sceneDepthTarget != null && needsSceneDepth(input.environment)
    if (!shadowActive && !sceneDepthActive) return emptyList()
    // Transparent draws write no depth in the main pass and must never populate SceneDepth or shadows.
    return input.resolvedOpaqueDraws
}

/**
 * Which frame slot [input]'s draws bind: the dedicated offscreen slot (`commandBuffers.size`) when
 * prepared through [Renderer.offscreenDrawPreparer] (or when [input] carries no draws), or the
 * presented frame slot a legacy caller prepared against via [Renderer.gpuDrawPreparer] (#1687, #1709).
 */
private fun Renderer.submittedSlotFor(input: GpuPassInput): Int {
    val offscreenSlot = commandBuffers.size
    return firstPresentedSlot(input.resolvedOpaqueDraws, offscreenSlot)
        ?: firstPresentedSlot(input.resolvedTransparentDraws, offscreenSlot)
        ?: firstPresentedSlot(input.resolvedEdgeDraws, offscreenSlot)
        ?: firstPresentedSubPassSlot(input.prePasses, offscreenSlot)
        ?: firstPresentedSubPassSlot(input.maskPasses, offscreenSlot)
        ?: offscreenSlot
}

private fun firstPresentedSubPassSlot(passes: List<GpuSubPass>, offscreenSlot: Int): Int? {
    for (i in 0 until passes.size) {
        firstPresentedSlot(passes[i].resolvedDraws, offscreenSlot)?.let { return it }
    }
    return null
}

private fun firstPresentedSlot(draws: List<PreparedDraw>, offscreenSlot: Int): Int? {
    for (i in 0 until draws.size) {
        val slot = draws[i].materialBinding as? com.awakekt.awake.vulkan.material.Material.UniformSlot ?: continue
        if (slot.frameIndex != offscreenSlot) return slot.frameIndex
    }
    return null
}
