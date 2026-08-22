// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan.renderer

import io.github.ronjunevaldoz.awake.render.passes2d.uploadUiRuns
import io.github.ronjunevaldoz.awake.render.passes2d.UiMeshUploader
import io.github.ronjunevaldoz.awake.render.passes2d.UiRunCoalescer
import io.github.ronjunevaldoz.awake.render.texture.RenderTarget
import io.github.ronjunevaldoz.awake.core.graphics2d.UiDrawPrimitive
import io.github.ronjunevaldoz.awake.core.math2d.Rectangle
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.vulkan.Vulkan
import io.github.ronjunevaldoz.awake.vulkan.enums.VkSubpassContents
import io.github.ronjunevaldoz.awake.vulkan.models.VkExtent2D
import io.github.ronjunevaldoz.awake.vulkan.models.VkOffset2D
import io.github.ronjunevaldoz.awake.vulkan.models.VkRect2D
import io.github.ronjunevaldoz.awake.vulkan.models.VkViewport
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkRenderPassBeginInfo
import io.github.ronjunevaldoz.awake.render.passes2d.DrawRun
import io.github.ronjunevaldoz.awake.vulkan.texture.OffscreenRenderTarget
import io.github.ronjunevaldoz.awake.vulkan.ui.DynamicMesh

/**
 * UI overlay primitive staging and headless rendering for the Vulkan backend.
 */

/**
 * Stages this frame's UI overlay content by coalescing [primitives] and uploading to dynamic meshes.
 */
internal fun Renderer.performDrawUi(primitives: List<UiDrawPrimitive>, font: UiFont?) {
    waitForCurrentFrameResourceSlot()
    if (swapchainManager.imageViews.isNotEmpty()) {
        ensureUiQuadPipeline()
        if (font != null) ensureGlyphPipeline(font)
        if (primitives.any { it is UiDrawPrimitive.Texture }) ensureTextureQuadPipeline()
        if (primitives.any { it is UiDrawPrimitive.RoundedQuad || it is UiDrawPrimitive.ShadowQuad }) ensureRoundedQuadPipeline()
    }

    uiRuns = uploadUiRuns(
        UiRunCoalescer.coalesce(primitives, Renderer.MAX_UI_QUADS),
        VulkanUiMeshUploader(this, swapchainManager.currentFrame),
    )
}

/**
 * Headless/offscreen test hook: renders a full frame's worth of [UiDrawPrimitive]s into [target].
 */
fun Renderer.renderUiToTexture(target: RenderTarget, primitives: List<UiDrawPrimitive>, font: UiFont?) {
    val offscreen = target as OffscreenRenderTarget
    performDrawUi(primitives, font)
    val frameIndex = swapchainManager.currentFrame

    ensureOffscreenQuadPipeline()
    if (font != null) ensureOffscreenGlyphPipeline(font)
    if (primitives.any { it is UiDrawPrimitive.RoundedQuad || it is UiDrawPrimitive.ShadowQuad }) ensureOffscreenRoundedQuadPipeline()

    val quadPipeline = requireNotNull(offscreenQuadRenderPipeline)
    quadPipeline.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    offscreenRoundedQuadRenderPipeline?.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    offscreenGlyphRenderPipeline?.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())

    runOffscreenCommands { commandBuffer ->
        val renderPassInfo = VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = offscreen.framebuffer,
            renderArea = VkRect2D(extent = VkExtent2D(offscreen.width, offscreen.height)),
            pClearValues = arrayOf(clearColorValue, Renderer.clearDepthValue),
        )
        Vulkan.vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE)
        val viewport = VkViewport(width = offscreen.width.toFloat(), height = offscreen.height.toFloat())
        Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(viewport))
        val fullScissor = VkRect2D(extent = VkExtent2D(offscreen.width, offscreen.height))
        Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(fullScissor))

        var runIndex = 0
        while (runIndex < uiRuns.size) {
            when (val run = uiRuns[runIndex]) {
                is DrawRun.QuadRun -> {
                    quadPipeline.bind(commandBuffer)
                    run.mesh.bind(frameIndex, commandBuffer)
                    run.mesh.draw(frameIndex, commandBuffer)
                }
                is DrawRun.RoundedQuadRun -> {
                    offscreenRoundedQuadRenderPipeline?.let { pipeline ->
                        pipeline.bind(commandBuffer)
                        run.mesh.bind(frameIndex, commandBuffer)
                        run.mesh.draw(frameIndex, commandBuffer)
                    }
                }
                is DrawRun.GlyphRun -> {
                    offscreenGlyphRenderPipeline?.let { pipeline ->
                        pipeline.bind(commandBuffer)
                        run.mesh.bind(frameIndex, commandBuffer)
                        run.mesh.draw(frameIndex, commandBuffer)
                    }
                }
                is DrawRun.ClipRun -> {
                    val maxX = offscreen.width
                    val maxY = offscreen.height
                    val x = run.rect.x.toInt().coerceIn(0, maxX)
                    val y = run.rect.y.toInt().coerceIn(0, maxY)
                    val width = run.rect.width.toInt().coerceAtLeast(0).coerceAtMost(maxX - x)
                    val height = run.rect.height.toInt().coerceAtLeast(0).coerceAtMost(maxY - y)
                    val scissor = VkRect2D(offset = VkOffset2D(x, y), extent = VkExtent2D(width, height))
                    Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(scissor))
                }
                is DrawRun.TextureRun -> Unit
            }
            runIndex += 1
        }

        Vulkan.vkCmdEndRenderPass(commandBuffer)
        offscreen.transitionToShaderReadOnly(commandBuffer)
    }
}

/**
 * Headless/offscreen test hook: renders glyph primitives into [target] using the real Vulkan glyph pipeline.
 */
fun Renderer.renderUiGlyphsToTexture(target: RenderTarget, glyphs: List<UiDrawPrimitive.Glyph>, font: UiFont) {
    val offscreen = target as OffscreenRenderTarget
    waitForCurrentFrameResourceSlot()
    val frameIndex = swapchainManager.currentFrame
    ensureOffscreenGlyphPipeline(font)
    val glyphPipeline = requireNotNull(offscreenGlyphRenderPipeline)
    glyphPipeline.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())

    val glyphRunMeshes = buildList {
        var chunkStart = 0
        var glyphRunCount = 0
        while (chunkStart < glyphs.size) {
            val chunkEnd = minOf(chunkStart + Renderer.MAX_UI_QUADS, glyphs.size)
            val mesh = glyphMeshForRun(glyphRunCount)
            val run = UiRunCoalescer.buildGlyphRun(glyphs.subList(chunkStart, chunkEnd))
            mesh.update(frameIndex, run.vertices, run.indices)
            add(mesh)
            glyphRunCount += 1
            chunkStart = chunkEnd
        }
    }

    runOffscreenCommands { commandBuffer ->
        val renderPassInfo = VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = offscreen.framebuffer,
            renderArea = VkRect2D(extent = VkExtent2D(offscreen.width, offscreen.height)),
            pClearValues = arrayOf(clearColorValue, Renderer.clearDepthValue),
        )
        Vulkan.vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE)
        val viewport = VkViewport(width = offscreen.width.toFloat(), height = offscreen.height.toFloat())
        Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(viewport))
        val scissor = VkRect2D(extent = VkExtent2D(offscreen.width, offscreen.height))
        Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(scissor))
        glyphPipeline.bind(commandBuffer)
        glyphRunMeshes.forEach { mesh ->
            mesh.bind(frameIndex, commandBuffer)
            mesh.draw(frameIndex, commandBuffer)
        }
        Vulkan.vkCmdEndRenderPass(commandBuffer)
        offscreen.transitionToShaderReadOnly(commandBuffer)
    }
}

/** Vulkan's mesh allocation for [uploadUiRuns]. Meshes are per-frame-in-flight, so the frame slot
 * is captured once per staging pass rather than read per run. */
private class VulkanUiMeshUploader(
    private val renderer: Renderer,
    private val frameIndex: Int,
) : UiMeshUploader<DynamicMesh> {
    override fun quadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): DynamicMesh =
        renderer.quadMeshForRun(runIndex).also { it.update(frameIndex, vertices, indices) }

    override fun roundedQuadMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): DynamicMesh =
        renderer.roundedQuadMeshForRun(runIndex).also { it.update(frameIndex, vertices, indices) }

    override fun glyphMesh(runIndex: Int, vertices: FloatArray, indices: IntArray): DynamicMesh =
        renderer.glyphMeshForRun(runIndex).also { it.update(frameIndex, vertices, indices) }
}
