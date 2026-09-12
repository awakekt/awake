/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.sortForRecording
import com.awakekt.awake.render.passes.GpuSceneFrame
import com.awakekt.awake.render.passes.RenderPassSlot
import com.awakekt.awake.render.renderer.EnvironmentUniforms
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.RenderTarget
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkImageLayout
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.material.PbrImageViews
import com.awakekt.awake.vulkan.mesh.Mesh
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkBufferCreateInfo
import com.awakekt.awake.vulkan.models.info.VkBufferImageCopy
import com.awakekt.awake.vulkan.models.info.VkBufferUsageFlagBits
import com.awakekt.awake.vulkan.models.info.VkImageLayout2
import com.awakekt.awake.vulkan.models.info.VkMemoryAllocateInfo
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import com.awakekt.awake.vulkan.texture.Texture
import com.awakekt.awake.render.material.Material as RenderMaterial
import com.awakekt.awake.render.mesh.Mesh as RenderMesh

internal fun Renderer.performCreateMesh(geometry: MeshGeometry): RenderMesh =
    Mesh(
        graphicsDevice,
        transferContext::runOneTimeCommands,
        geometry.vertices,
        geometry.indices,
        geometry.format,
        geometry.bounds,
    )

internal fun Renderer.performCreateMaterial(
    texture: TextureAsset?,
    renderTarget: RenderTarget?,
    uniformFloatCount: Int,
    pbrTextures: PbrTextureSet?,
): RenderMaterial {
    require(texture == null || renderTarget == null) { "Pass at most one of texture/renderTarget." }
    val material = Material(graphicsDevice, uniformFloatCount)
    val pbr = PbrImageViews(
        metallicRoughness = pbrImageView(
            pbrTextures?.metallicRoughness,
            Renderer.NEUTRAL_METALLIC_ROUGHNESS,
        ),
        normal = pbrImageView(pbrTextures?.normal, Renderer.NEUTRAL_NORMAL),
        occlusion = pbrImageView(pbrTextures?.occlusion, Renderer.NEUTRAL_OCCLUSION),
        emissive = pbrImageView(pbrTextures?.emissive, Renderer.NEUTRAL_EMISSIVE),
    )
    if (renderTarget != null) {
        val offscreen = renderTarget as OffscreenRenderTarget
        material.createResourcesFromRenderTarget(
            offscreen.sampler,
            offscreen.colorImageView,
            pbr,
        )
    } else {
        material.createResources(uploadTexture(texture ?: Renderer.PLACEHOLDER_TEXTURE), pbr)
    }
    return material
}

internal fun Renderer.uploadTexture(asset: TextureAsset): Texture = Texture(
    graphicsDevice,
    transferContext::runOneTimeCommands,
    asset.data,
    asset.width,
    asset.height,
).let(textureResources::register)

internal fun Renderer.pbrImageView(asset: TextureAsset?, neutral: TextureAsset): Long =
    if (asset != null) {
        uploadTexture(asset).imageView.handle
    } else {
        textureResources.neutral(neutral) {
            Texture(
                graphicsDevice,
                transferContext::runOneTimeCommands,
                neutral.data,
                neutral.width,
                neutral.height,
            )
        }.imageView.handle
    }

/**
 * The frame the on-screen path just drew, when there is no screen.
 *
 * `readPixels` reads a `RenderTarget`, which is the OFFSCREEN path -- a different recording than
 * `draw` produces. This reads what `draw` itself wrote, so a test can check the frame an app
 * actually presents rather than a second rendering of the same scene.
 *
 * Waits on every in-flight fence first: the frame whose image this is may still be executing.
 */
suspend fun Renderer.readPresentedPixels(): TextureAsset {
    require(swapchainManager.isHeadlessPresentable) {
        "readPresentedPixels reads a headless stand-in image; this renderer presents to a surface."
    }
    Vulkan.vkWaitForFences(device, swapchainManager.inFlightFences, true, Long.MAX_VALUE)
    val width = swapchainManager.extent.width
    val height = swapchainManager.extent.height
    // The frame just drawn is the one BEFORE the manager's current slot, which draw() advanced.
    val drawn = (swapchainManager.currentFrame + swapchainManager.imageViews.size - 1) %
            swapchainManager.imageViews.size
    return readImageBytes(swapchainManager.headlessImages[drawn], width, height)
}

internal fun Renderer.performCreateRenderTarget(width: Int, height: Int): RenderTarget {
    lateinit var target: OffscreenRenderTarget
    target = OffscreenRenderTarget(
        graphicsDevice,
        renderPipeline.renderPass,
        width,
        height,
        swapchainManager.imageFormat.value,
        onDestroy = { createdRenderTargets.remove(target) },
    )
    createdRenderTargets += target
    return target
}

internal fun Renderer.performRenderToTexture(
    target: RenderTarget,
    input: GpuPassInput,
) {
    val offscreen = target as OffscreenRenderTarget
    val sceneRect = sceneViewport?.clampedTo(offscreen.width.toFloat(), offscreen.height.toFloat())
    val materialUsage = mutableMapOf<RenderMaterial, Int>()
    val preparedOpaque = prepareGpuDraws(
        commandBuffers.size,
        input.viewProjection,
        input.cameraEye,
        input.opaqueDraws,
        isTransparent = false,
        materialUsage,
    )
    val preparedTransparent = prepareGpuDraws(
        commandBuffers.size,
        input.viewProjection,
        input.cameraEye,
        input.transparentDraws,
        isTransparent = true,
        materialUsage,
    )
    val preparedDrawCalls = preparedOpaque + preparedTransparent
    val sorted = sortForRecording(preparedDrawCalls)

    runOffscreenCommands { commandBuffer ->
        recordDepthPrePass(commandBuffer, preparedDrawCalls, null)
        recordSceneDepthPass(commandBuffer, preparedDrawCalls, cameraDepthPass(input.viewProjection))
        offscreen.prepareForColorAttachment(commandBuffer)
        val renderPassInfo = VkRenderPassBeginInfo(
            renderPass = renderPipeline.renderPass,
            framebuffer = offscreen.framebuffer,
            renderArea = VkRect2D(extent = VkExtent2D(offscreen.width, offscreen.height)),
            pClearValues = arrayOf(clearColorValue, Renderer.clearDepthValue),
        )
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            renderPassInfo,
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        val viewport = sceneRect?.toVkViewport()
            ?: VkViewport(
                width = offscreen.width.toFloat(),
                height = offscreen.height.toFloat(),
            )
        Vulkan.vkCmdSetViewport(commandBuffer, 0, arrayOf(viewport))
        val scissor = sceneRect?.toVkScissor() ?: VkRect2D(
            extent = VkExtent2D(
                offscreen.width,
                offscreen.height,
            ),
        )
        Vulkan.vkCmdSetScissor(commandBuffer, 0, arrayOf(scissor))

        recordSharedPassFeatures(
            RenderPassSlot.Scene,
            RendererFrameContext(
                renderer = this,
                commandBuffer = commandBuffer,
                frameIndex = commandBuffers.size,
                groupedDrawCalls = sorted.opaqueByPipeline,
                transparentDrawCalls = sorted.transparent,
                primaryPipeline = pipelineFor(renderPipeline.vertexFormat) ?: renderPipeline,
                viewProjection = input.viewProjection,
                cameraEye = input.cameraEye,
            ),
        )
        Vulkan.vkCmdEndRenderPass(commandBuffer)
        offscreen.transitionToShaderReadOnly(commandBuffer)
    }
}

internal fun Renderer.performRenderToTexture(
    target: RenderTarget,
    camera: Lens,
    drawCalls: List<DrawCall>,
    light: SceneLight,
    environment: EnvironmentUniforms = EnvironmentUniforms(
        showSky = showEnvironment,
        horizonColor = horizonColor,
        zenithColor = zenithColor,
        fogDensity = fogDensity,
        fogColor = fogColor,
        shadowsEnabled = shadowsEnabled,
    ),
) {
    val offscreen = target as OffscreenRenderTarget
    val sceneRect = sceneViewport?.clampedTo(offscreen.width.toFloat(), offscreen.height.toFloat())
    val aspect = sceneRect?.aspect ?: (offscreen.width.toFloat() / offscreen.height.toFloat())
    val frame = GpuSceneFrame(
        lens = camera,
        drawCalls = drawCalls,
        light = light,
        environment = environment,
    )
    performRenderToTexture(target, frame.toPassInput(clipSpace, aspect))
}

internal suspend fun Renderer.performReadPixels(target: RenderTarget): TextureAsset {
    val offscreen = target as OffscreenRenderTarget
    return readImageBytes(
        offscreen.colorImage,
        offscreen.width,
        offscreen.height,
        from = VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
    )
}

/**
 * Copies one image into host memory, transitioning it there and back.
 *
 * [from] is the layout the image is already in and returns to: an offscreen target's colour
 * attachment is left readable by a shader, while a headless stand-in for a presented image is
 * left where the frame loop put it.
 */
internal suspend fun Renderer.readImageBytes(
    image: Long,
    width: Int,
    height: Int,
    from: Int = VkImageLayout.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR.value,
): TextureAsset {
    val byteSize = (width * height * 4).toLong()
    val stagingBuffer = VulkanBuffers.vkCreateBuffer(
        device,
        VkBufferCreateInfo(
            size = byteSize,
            usage = VkBufferUsageFlagBits.VK_BUFFER_USAGE_TRANSFER_DST_BIT,
        ),
    )
    val stagingRequirements = VulkanBuffers.vkGetBufferMemoryRequirements(device, stagingBuffer)
    val stagingMemoryTypeIndex = VulkanBuffers.findMemoryType(
        physicalDevice,
        stagingRequirements.memoryTypeBits,
        VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT or
                VkMemoryPropertyFlagBits.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
    )
    val stagingMemory = VulkanBuffers.vkAllocateMemory(
        device,
        VkMemoryAllocateInfo(
            allocationSize = stagingRequirements.size,
            memoryTypeIndex = stagingMemoryTypeIndex,
        ),
    )
    VulkanBuffers.vkBindBufferMemory(device, stagingBuffer, stagingMemory, 0)

    val pixels: ByteArray
    try {
        runOffscreenCommands { commandBuffer ->
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image,
                from,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            )
            VulkanImages.vkCmdCopyImageToBuffer(
                commandBuffer,
                image,
                stagingBuffer,
                VkBufferImageCopy(imageWidth = width, imageHeight = height),
            )
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                image,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                from,
            )
        }
        pixels = VulkanBuffers.readBufferMemoryBytes(device, stagingMemory, 0, byteSize.toInt())
    } finally {
        VulkanBuffers.vkDestroyBuffer(device, stagingBuffer)
        VulkanBuffers.vkFreeMemory(device, stagingMemory)
    }
    return TextureAsset(pixels, width, height)
}
