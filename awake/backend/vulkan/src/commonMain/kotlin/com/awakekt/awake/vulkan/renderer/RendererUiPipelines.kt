/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.asset.shaders.program
import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.graphics2d.TextureCompositeMode
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.passes2d.UiPipelineKind
import com.awakekt.awake.render.pipeline.UiPipelineDescriptor
import com.awakekt.awake.render.pipeline.UiPipelineVariant
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.render.texture.TextureFiltering
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.models.info.VkFilter
import com.awakekt.awake.vulkan.models.info.VkFramebufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerAddressMode
import com.awakekt.awake.vulkan.models.info.VkSamplerCreateInfo
import com.awakekt.awake.vulkan.models.info.VkSamplerMipmapMode
import com.awakekt.awake.vulkan.texture.Texture
import com.awakekt.awake.vulkan.ui.UiRenderPipeline

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
        descriptor = uiPipelineDescriptor(UiPipelineKind.Quad),
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

/** Builds the swapchain glyph pipeline once. It holds no font: each font binds its own atlas. */
internal fun Renderer.ensureGlyphPipeline() {
    if (uiGlyphRenderPipeline != null) return
    val renderPass = requireNotNull(uiRenderPipeline) { "ensureUiQuadPipeline() must run first." }.renderPass
    uiGlyphRenderPipeline = buildUiGlyphRenderPipeline(renderPass)
}

/** [ensureGlyphPipeline]'s offscreen counterpart, for [renderUiToTexture]. */
internal fun Renderer.ensureOffscreenGlyphPipeline() {
    if (offscreenGlyphRenderPipeline != null) return
    offscreenGlyphRenderPipeline = buildUiGlyphRenderPipeline(imagePassExecutor.ensureOffscreenClearRenderPass(swapchainManager.imageFormat))
}

/**
 * Builds the swapchain decorated glyph pipeline (#1061) once, when this renderer was given its shaders. Like
 * [ensureGlyphPipeline]'s, it holds no font.
 */
internal fun Renderer.ensureDecoratedGlyphPipeline() {
    if (uiDecoratedGlyphRenderPipeline != null) return
    val renderPass = requireNotNull(uiRenderPipeline) { "ensureUiQuadPipeline() must run first." }.renderPass
    uiDecoratedGlyphRenderPipeline = buildUiDecoratedGlyphRenderPipeline(renderPass)
}

/** [ensureDecoratedGlyphPipeline]'s offscreen counterpart, for [renderUiToTexture]. */
internal fun Renderer.ensureOffscreenDecoratedGlyphPipeline() {
    if (offscreenDecoratedGlyphRenderPipeline != null) return
    offscreenDecoratedGlyphRenderPipeline =
        buildUiDecoratedGlyphRenderPipeline(imagePassExecutor.ensureOffscreenClearRenderPass(swapchainManager.imageFormat))
}

private fun Renderer.buildUiDecoratedGlyphRenderPipeline(renderPass: Long): UiRenderPipeline? =
    uiDecoratedGlyphShaders?.let { shaders ->
        UiRenderPipeline(
            graphicsDevice = graphicsDevice,
            swapchainManager = swapchainManager,
            vertShaderCode = shaders.vertex,
            fragShaderCode = shaders.fragment,
            descriptor = uiPipelineDescriptor(UiPipelineKind.DecoratedGlyph),
            externalRenderPass = renderPass,
        )
    }

private fun Renderer.buildUiGlyphRenderPipeline(renderPass: Long): UiRenderPipeline =
    UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiGlyphShaders.vertex,
        fragShaderCode = uiGlyphShaders.fragment,
        descriptor = uiPipelineDescriptor(UiPipelineKind.Glyph),
        externalRenderPass = renderPass,
    )

/**
 * [font]'s atlas, uploaded the first time any glyph pipeline needs it and shared by all of them.
 * Keyed by identity and released with the renderer; a font no longer drawn stays uploaded.
 */
internal fun Renderer.ensureFontAtlas(font: UiFont): Texture = fontAtlases.getOrPut(font) {
    val glyphFilter = VkFilter.VK_FILTER_LINEAR
    Texture(
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
        // The atlas is a distance field: a box-filtered mip averages the distances, erodes thin
        // stems, and the sampler's implicit LOD (log2(32 texels per em / px)) reads mip 1-2 at UI sizes.
        filtering = TextureFiltering.BaseLevelLinear,
    ).let(textureResources::register)
}

internal fun Renderer.ensureImagePassTexture(asset: TextureAsset): Texture = imagePassTextures.getOrPut(asset) {
    Texture(
        graphicsDevice,
        transferContext::runOneTimeCommands,
        asset.data,
        asset.width,
        asset.height,
    ).let(textureResources::register)
}

internal fun Renderer.ensureTextureQuadPipeline(mode: TextureCompositeMode) {
    if (uiTextureRenderPipelines.containsKey(mode)) return
    val renderPass = requireNotNull(uiRenderPipeline) { "ensureUiQuadPipeline() must run first." }.renderPass
    uiTextureRenderPipelines[mode] = UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiTextureShaders.vertex,
        fragShaderCode = uiTextureShaders.fragment,
        descriptor = uiPipelineDescriptor(UiPipelineKind.Texture, mode.blendMode, mode.premultiplied),
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
        descriptor = uiPipelineDescriptor(UiPipelineKind.RoundedQuad),
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
        descriptor = uiPipelineDescriptor(UiPipelineKind.Quad),
        externalRenderPass = imagePassExecutor.ensureOffscreenClearRenderPass(swapchainManager.imageFormat),
    )
}

internal fun Renderer.ensureOffscreenRoundedQuadPipeline() {
    if (offscreenRoundedQuadRenderPipeline != null) return
    offscreenRoundedQuadRenderPipeline = UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiRoundedQuadShaders.vertex,
        fragShaderCode = uiRoundedQuadShaders.fragment,
        descriptor = uiPipelineDescriptor(UiPipelineKind.RoundedQuad),
        externalRenderPass = imagePassExecutor.ensureOffscreenClearRenderPass(swapchainManager.imageFormat),
    )
}

internal fun Renderer.ensureOffscreenTexturePipeline(mode: TextureCompositeMode) {
    if (offscreenTextureRenderPipelines.containsKey(mode)) return
    offscreenTextureRenderPipelines[mode] = UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = uiTextureShaders.vertex,
        fragShaderCode = uiTextureShaders.fragment,
        descriptor = uiPipelineDescriptor(UiPipelineKind.Texture, mode.blendMode, mode.premultiplied),
        externalRenderPass = imagePassExecutor.ensureOffscreenClearRenderPass(swapchainManager.imageFormat),
        framesInFlight = maxFramesInFlight,
    )
}

/** Builds the no-geometry fullscreen two-sampler composite pipeline only when requested. */
internal fun Renderer.ensureTargetCompositePipeline() {
    if (uiTargetCompositePipeline != null) return
    val shaders = requireNotNull(uiTargetCompositeShaders) {
        "This renderer was built without ui_target_composite shader resources."
    }
    uiTargetCompositePipeline = UiRenderPipeline(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        vertShaderCode = shaders.vertex,
        fragShaderCode = shaders.fragment,
        externalRenderPass = imagePassExecutor.ensureOffscreenClearRenderPass(swapchainManager.imageFormat),
        descriptor = UiPipelineDescriptor(
            variant = UiPipelineVariant.TargetComposite,
            vertexFormat = VertexFormats2D.Quad,
        ),
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

internal fun Renderer.activeUiPipelineTargets(): List<com.awakekt.awake.vulkan.pipeline.VulkanUiPipelineTarget> = buildList {
    uiRenderPipeline?.let { add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiQuad.vulkan.program())) }
    offscreenQuadRenderPipeline?.let { add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiQuad.vulkan.program())) }

    uiGlyphRenderPipeline?.let { add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiGlyph.vulkan.program())) }
    offscreenGlyphRenderPipeline?.let { add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiGlyph.vulkan.program())) }
    uiDecoratedGlyphRenderPipeline?.let {
        add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiDecoratedGlyph.vulkan.program()))
    }
    offscreenDecoratedGlyphRenderPipeline?.let {
        add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiDecoratedGlyph.vulkan.program()))
    }

    uiRoundedQuadRenderPipeline?.let { add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiRoundedQuad.vulkan.program())) }
    offscreenRoundedQuadRenderPipeline?.let { add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiRoundedQuad.vulkan.program())) }

    uiTextureRenderPipelines.values.forEach { add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiTexture.vulkan.program())) }
    offscreenTextureRenderPipelines.values.forEach { add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiTexture.vulkan.program())) }

    uiTargetCompositePipeline?.let { add(vulkanUiTarget(it, com.awakekt.awake.asset.shaders.EngineShaderSets.UiTargetComposite.vulkan.program())) }
}

private fun vulkanUiTarget(
    pipeline: UiRenderPipeline,
    program: com.awakekt.awake.render.pipeline.ShaderProgram,
): com.awakekt.awake.vulkan.pipeline.VulkanUiPipelineTarget = object : com.awakekt.awake.vulkan.pipeline.VulkanUiPipelineTarget {
    override val identity: Any = pipeline
    override val program: com.awakekt.awake.render.pipeline.ShaderProgram = program
    override val bindingsByGroup: Map<Int, com.awakekt.awake.render.pipeline.GroupBindings> =
        checkNotNull(program.bindingsByGroup) { "UI shader program must declare bindingsByGroup" }

    override fun buildPipeline(
        shaders: com.awakekt.awake.vulkan.pipeline.ShaderPair,
        vertexEntryPoint: String,
        fragmentEntryPoint: String,
    ): Long = pipeline.buildPipeline(shaders, vertexEntryPoint, fragmentEntryPoint)

    override fun swapIn(newPipeline: Long) = pipeline.swapIn(newPipeline)

    override fun destroyPipeline(handle: Long) = pipeline.destroyPipeline(handle)
}
