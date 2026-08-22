// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan.renderer

import io.github.ronjunevaldoz.awake.render.passes2d.UiPipelineKind
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.vulkan.Vulkan
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkFilter
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkFramebufferCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkSamplerAddressMode
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkSamplerCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkSamplerMipmapMode
import io.github.ronjunevaldoz.awake.vulkan.texture.Texture
import io.github.ronjunevaldoz.awake.vulkan.ui.UiRenderPipeline

/**
 * Lazy construction of every UI-overlay graphics pipeline [Renderer.drawUi]/
 * [Renderer.renderUiGlyphsToTexture] need -- each is built only on the first call that
 * actually needs it.
 */

internal fun Renderer.ensureUiQuadPipeline() {
    if (uiRenderPipeline != null) return
    val pipeline = UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiShaders.vertex,
        fragShaderCode = uiShaders.fragment,
        kind = UiPipelineKind.Quad,
    )
    uiRenderPipeline = pipeline
    uiFramebuffers = buildUiFramebuffers(pipeline.renderPass)
}

internal fun Renderer.buildUiFramebuffers(renderPass: Long): List<Long> = swapchainManager.imageViews.map { imageView ->
    Vulkan.vkCreateFramebuffer(
        device,
        VkFramebufferCreateInfo(
            renderPass = renderPass,
            pAttachments = arrayOf(imageView),
            width = swapchainManager.extent.width,
            height = swapchainManager.extent.height,
            layers = 1,
        ),
    )
}.toList()

internal fun Renderer.ensureGlyphPipeline(font: UiFont) {
    if (currentUiFont !== font) {
        uiGlyphRenderPipeline?.destroy()
        uiGlyphRenderPipeline = null
        offscreenGlyphRenderPipeline?.destroy()
        offscreenGlyphRenderPipeline = null
        fontTexture?.destroy()
        fontTexture = null
        currentUiFont = font
    }
    if (uiGlyphRenderPipeline != null) return
    val renderPass = requireNotNull(uiRenderPipeline) { "ensureUiQuadPipeline() must run first." }.renderPass
    uiGlyphRenderPipeline = buildUiGlyphRenderPipeline(renderPass, font)
}

internal fun Renderer.ensureOffscreenGlyphPipeline(font: UiFont) {
    if (currentUiFont !== font) {
        offscreenGlyphRenderPipeline?.destroy()
        offscreenGlyphRenderPipeline = null
    }
    if (offscreenGlyphRenderPipeline != null) return
    offscreenGlyphRenderPipeline = buildUiGlyphRenderPipeline(renderPipeline.renderPass, font)
}

private fun Renderer.buildUiGlyphRenderPipeline(renderPass: Long, font: UiFont): UiRenderPipeline =
    UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiGlyphShaders.vertex,
        fragShaderCode = uiGlyphShaders.fragment,
        kind = UiPipelineKind.Glyph,
        hasTexture = true,
        externalRenderPass = renderPass,
        fixedTexture = ensureFontTexture(font),
    )

internal fun Renderer.ensureFontTexture(font: UiFont): Texture {
    fontTexture?.let { return it }
    val glyphFilter = VkFilter.VK_FILTER_LINEAR
    return Texture(
        graphicsDevice,
        transferContext::runOneTimeCommands,
        font.atlasPixelsRgba,
        font.atlasWidth,
        font.atlasHeight,
        samplerCreateInfo = VkSamplerCreateInfo(
            magFilter = glyphFilter,
            minFilter = glyphFilter,
            addressModeU = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
            addressModeV = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
            addressModeW = VkSamplerAddressMode.VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE,
            mipmapMode = VkSamplerMipmapMode.VK_SAMPLER_MIPMAP_MODE_NEAREST,
        ),
    ).also { fontTexture = it }
}

internal fun Renderer.ensureTextureQuadPipeline() {
    if (uiTextureRenderPipeline != null) return
    val renderPass = requireNotNull(uiRenderPipeline) { "ensureUiQuadPipeline() must run first." }.renderPass
    uiTextureRenderPipeline = UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiTextureShaders.vertex,
        fragShaderCode = uiTextureShaders.fragment,
        kind = UiPipelineKind.Texture,
        hasTexture = true,
        externalRenderPass = renderPass,
        framesInFlight = maxFramesInFlight,
    )
}

internal fun Renderer.ensureRoundedQuadPipeline() {
    if (uiRoundedQuadRenderPipeline != null) return
    val renderPass = requireNotNull(uiRenderPipeline) { "ensureUiQuadPipeline() must run first." }.renderPass
    uiRoundedQuadRenderPipeline = UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiRoundedQuadShaders.vertex,
        fragShaderCode = uiRoundedQuadShaders.fragment,
        kind = UiPipelineKind.RoundedQuad,
        externalRenderPass = renderPass,
    )
}

internal fun Renderer.ensureOffscreenQuadPipeline() {
    if (offscreenQuadRenderPipeline != null) return
    offscreenQuadRenderPipeline = UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiShaders.vertex,
        fragShaderCode = uiShaders.fragment,
        kind = UiPipelineKind.Quad,
        externalRenderPass = renderPipeline.renderPass,
    )
}

internal fun Renderer.ensureOffscreenRoundedQuadPipeline() {
    if (offscreenRoundedQuadRenderPipeline != null) return
    offscreenRoundedQuadRenderPipeline = UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiRoundedQuadShaders.vertex,
        fragShaderCode = uiRoundedQuadShaders.fragment,
        kind = UiPipelineKind.RoundedQuad,
        externalRenderPass = renderPipeline.renderPass,
    )
}
