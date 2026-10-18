/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.graphics2d.TextureCompositeMode
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.passes2d.BackdropBlurTarget
import com.awakekt.awake.render.passes2d.DrawRun
import com.awakekt.awake.render.passes2d.SharedUiRenderFeature
import com.awakekt.awake.render.passes2d.StagedDrawRun
import com.awakekt.awake.render.passes2d.UiMeshUploader
import com.awakekt.awake.render.passes2d.UiRunCoalescer
import com.awakekt.awake.render.passes2d.uploadUiRuns
import com.awakekt.awake.render.renderer.UiTargetCompositeMode
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.models.VkClearColorValue
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.pipeline.UiPipelineSet
import com.awakekt.awake.vulkan.pipeline.VulkanUiRunRecorder
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import com.awakekt.awake.vulkan.ui.DynamicMesh

/**
 * UI overlay primitive staging and headless rendering for the Vulkan backend.
 */

/**
 * Stages this frame's UI overlay content by coalescing [primitives] and uploading to dynamic meshes.
 * [font] samples the glyphs that carry no font of their own; every font a glyph run samples has its
 * atlas uploaded here, once, so recording only binds it.
 */
internal fun Renderer.performDrawUi(
    primitives: List<UiDrawPrimitive>,
    font: UiFont?,
    backdrop: BackdropBlurTarget? = swapchainBackdropTarget(),
) {
    waitForCurrentFrameResourceSlot()
    val staged = UiRunCoalescer.coalesce(primitives, Renderer.MAX_UI_QUADS, uiRunCache, backdrop)
    uiBlurCount = staged.count { it is StagedDrawRun.BackdropRun && it.blur != null }
    // Before uploading, so a level rebuilt for a new size never replaces one a staged run names.
    if (uiBlurCount > 0 && backdrop != null) ensureBackdropResources(backdrop.width, backdrop.height)
    uiRuns = uploadUiRuns(staged, VulkanUiMeshUploader(this, swapchainManager.currentFrame), defaultFont = font)
    val hasGlyphs = ensureGlyphRunAtlases()
    if (swapchainManager.imageViews.isNotEmpty()) {
        ensureUiQuadPipeline()
        if (hasGlyphs) ensureGlyphPipeline()
        primitives.filterIsInstance<UiDrawPrimitive.Texture>().map { TextureCompositeMode(it.blendMode, it.premultiplied) }.distinct()
            .forEach(::ensureTextureQuadPipeline)
        if (staged.any { it is StagedDrawRun.RoundedQuadRun }) ensureRoundedQuadPipeline()
        if (uiBlurCount > 0) ensureSwapchainBackdropPipeline()
    }
}

/** Uploads the atlas of every font this frame's glyph runs sample; true when there is one. */
private fun Renderer.ensureGlyphRunAtlases(): Boolean {
    var any = false
    for (index in uiRuns.indices) {
        val run = uiRuns[index]
        if (run is DrawRun.GlyphRun) {
            ensureFontAtlas(run.font)
            any = true
        }
    }
    return any
}

/**
 * Removes the staged textures that sample [target], which is being destroyed. Staged UI is
 * recorded at the next `draw`, so a target freed in between, such as a canvas View of a scene
 * being switched away, would otherwise have its freed image view and sampler written into a
 * descriptor set.
 */
internal fun Renderer.dropStagedUiSampling(target: OffscreenRenderTarget) {
    uiRuns = uiRuns.map { run ->
        if (run !is DrawRun.TextureRun) return@map run
        DrawRun.TextureRun(run.primitives.filterNot { (it.texture as? Material)?.imageViewHandle == target.colorImageView })
    }
}

/**
 * Headless/offscreen test hook: renders a full frame's worth of [UiDrawPrimitive]s into [target],
 * through the same shared, paint-order-preserving UI pass the swapchain uses.
 */
fun Renderer.renderUiToTexture(target: RenderTarget, primitives: List<UiDrawPrimitive>, font: UiFont?) {
    val offscreen = target as OffscreenRenderTarget
    performDrawUi(primitives, font, BackdropBlurTarget(backdropBlurQuality, offscreen.width, offscreen.height))
    val frameIndex = swapchainManager.currentFrame

    ensureOffscreenQuadPipeline()
    if (ensureGlyphRunAtlases()) ensureOffscreenGlyphPipeline()
    primitives.filterIsInstance<UiDrawPrimitive.Texture>().map { TextureCompositeMode(it.blendMode, it.premultiplied) }.distinct()
        .forEach(::ensureOffscreenTexturePipeline)
    if (uiRuns.any { it is DrawRun.RoundedQuadRun }) ensureOffscreenRoundedQuadPipeline()

    val pipelines = UiPipelineSet(
        quad = requireNotNull(offscreenQuadRenderPipeline),
        glyph = offscreenGlyphRenderPipeline,
        textures = offscreenTextureRenderPipelines,
        roundedQuad = offscreenRoundedQuadRenderPipeline,
        fontAtlases = fontAtlases,
        backdrop = if (uiBlurCount > 0) offscreenBackdropTarget(offscreen) else null,
    )
    pipelines.quad.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    pipelines.roundedQuad?.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    pipelines.backdrop?.composite?.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    pipelines.glyph?.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    pipelines.textures.values.forEach {
        it.writeScreenSize(offscreen.width.toFloat(), offscreen.height.toFloat())
    }

    runOffscreenCommands { commandBuffer ->
        offscreen.prepareForColorAttachment(commandBuffer)
        val renderPassInfo = VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = offscreen.framebuffer,
            renderArea = VkRect2D(extent = VkExtent2D(offscreen.width, offscreen.height)),
            // A graphics layer is sampled over the parent UI, so its untouched pixels must stay
            // transparent rather than inherit the application's scene clear colour.
            pClearValues = arrayOf(VkClearColorValue.rgba(0f, 0f, 0f, 0f), Renderer.clearDepthValue),
        )
        Vulkan.vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE)
        val viewport = VkViewport(width = offscreen.width.toFloat(), height = offscreen.height.toFloat())
        Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(viewport))
        val fullScissor = VkRect2D(extent = VkExtent2D(offscreen.width, offscreen.height))
        Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(fullScissor))

        SharedUiRenderFeature().recordCommands(
            runs = uiRuns,
            surfaceWidth = offscreen.width,
            surfaceHeight = offscreen.height,
            recorder = VulkanUiRunRecorder(commandBuffer, frameIndex, statsCounter, pipelines, ::textureMeshForPrimitive),
        )

        Vulkan.vkCmdEndRenderPass(commandBuffer)
        offscreen.transitionToShaderReadOnly(commandBuffer)
    }
}

/** Samples [source] and [destination] into [output] without ever attaching a sampled target. */
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
        destinationTarget.width == sourceTarget.width &&
            sourceTarget.width == outputTarget.width &&
            destinationTarget.height == sourceTarget.height &&
            sourceTarget.height == outputTarget.height,
    ) {
        "UI target composites require equal source, destination, and output dimensions."
    }
    require(outputTarget !== destinationTarget && outputTarget !== sourceTarget) {
        "UI target composite output must not alias a sampled input target."
    }
    ensureTargetCompositePipeline()
    val pipeline = requireNotNull(uiTargetCompositePipeline)
    val frameIndex = swapchainManager.currentFrame
    runOffscreenCommands { commandBuffer ->
        outputTarget.prepareForColorAttachment(commandBuffer)
        val renderPassInfo = VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = outputTarget.framebuffer,
            renderArea = VkRect2D(extent = VkExtent2D(outputTarget.width, outputTarget.height)),
            pClearValues = arrayOf(VkClearColorValue.rgba(0f, 0f, 0f, 0f), Renderer.clearDepthValue),
        )
        Vulkan.vkCmdBeginRenderPass(commandBuffer, renderPassInfo, VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE)
        Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(VkViewport(width = outputTarget.width.toFloat(), height = outputTarget.height.toFloat())))
        Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(VkRect2D(extent = VkExtent2D(outputTarget.width, outputTarget.height))))
        pipeline.bindTargetComposite(
            commandBuffer = commandBuffer,
            frameIndex = frameIndex,
            sourceSampler = sourceTarget.sampler,
            sourceImageView = sourceTarget.colorImageView,
            destinationSampler = destinationTarget.sampler,
            destinationImageView = destinationTarget.colorImageView,
            mode = mode,
        )
        Vulkan.vkCmdDraw(commandBuffer, 3, 1, 0, 0)
        statsCounter.recordDraw(3)
        Vulkan.vkCmdEndRenderPass(commandBuffer)
        outputTarget.transitionToShaderReadOnly(commandBuffer)
    }
}

/**
 * Headless/offscreen test hook: renders glyph primitives into [target] using the real Vulkan glyph pipeline.
 */
fun Renderer.renderUiGlyphsToTexture(target: RenderTarget, glyphs: List<UiDrawPrimitive.Glyph>, font: UiFont) {
    val offscreen = target as OffscreenRenderTarget
    waitForCurrentFrameResourceSlot()
    val frameIndex = swapchainManager.currentFrame
    ensureOffscreenGlyphPipeline()
    val glyphPipeline = requireNotNull(offscreenGlyphRenderPipeline)
    val atlas = ensureFontAtlas(font)
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
        offscreen.prepareForColorAttachment(commandBuffer)
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
        glyphPipeline.bindFontAtlas(commandBuffer, font, atlas)
        glyphRunMeshes.forEach { mesh ->
            mesh.bind(frameIndex, commandBuffer)
            mesh.draw(frameIndex, commandBuffer, statsCounter)
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

    override fun backdropMesh(runIndex: Int, vertices: FloatArray, indices: IntArray, blurPass: Boolean): DynamicMesh {
        val mesh = if (blurPass) renderer.backdropPassMeshForRun(runIndex) else renderer.backdropMeshForRun(runIndex)
        return mesh.also { it.update(frameIndex, vertices, indices) }
    }
}
