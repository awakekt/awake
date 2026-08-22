// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan.renderer

import io.github.ronjunevaldoz.awake.core.math.Lens
import io.github.ronjunevaldoz.awake.render.material.Material as RenderMaterial
import io.github.ronjunevaldoz.awake.render.mesh.Mesh as RenderMesh
import io.github.ronjunevaldoz.awake.core.geometry.MeshGeometry
import io.github.ronjunevaldoz.awake.render.renderer.DEFAULT_SCENE_LIGHT
import io.github.ronjunevaldoz.awake.render.renderer.SceneLight
import io.github.ronjunevaldoz.awake.render.renderer.DrawCall
import io.github.ronjunevaldoz.awake.render.texture.PbrTextureSet
import io.github.ronjunevaldoz.awake.render.texture.RenderTarget
import io.github.ronjunevaldoz.awake.render.texture.TextureAsset
import io.github.ronjunevaldoz.awake.vulkan.Vulkan
import io.github.ronjunevaldoz.awake.vulkan.enums.VkSubpassContents
import io.github.ronjunevaldoz.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import io.github.ronjunevaldoz.awake.vulkan.gen.VulkanBuffers
import io.github.ronjunevaldoz.awake.vulkan.gen.VulkanImages
import io.github.ronjunevaldoz.awake.vulkan.material.Material
import io.github.ronjunevaldoz.awake.vulkan.material.PbrImageViews
import io.github.ronjunevaldoz.awake.vulkan.mesh.Mesh
import io.github.ronjunevaldoz.awake.vulkan.models.VkExtent2D
import io.github.ronjunevaldoz.awake.vulkan.models.VkRect2D
import io.github.ronjunevaldoz.awake.vulkan.models.VkViewport
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkBufferCreateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkBufferImageCopy
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkBufferUsageFlagBits
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkImageLayout2
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkMemoryAllocateInfo
import io.github.ronjunevaldoz.awake.vulkan.models.info.VkRenderPassBeginInfo
import io.github.ronjunevaldoz.awake.vulkan.texture.OffscreenRenderTarget
import io.github.ronjunevaldoz.awake.vulkan.texture.Texture

internal fun Renderer.performCreateMesh(geometry: MeshGeometry): RenderMesh =
    Mesh(
        graphicsDevice,
        transferContext::runOneTimeCommands,
        geometry.vertices,
        geometry.indices,
        geometry.format,
    )

internal fun Renderer.performCreateMaterial(
    texture: TextureAsset?,
    renderTarget: RenderTarget?,
    uniformFloatCount: Int,
    pbrTextures: PbrTextureSet?,
): RenderMaterial {
    require(texture == null || renderTarget == null) { "Pass at most one of texture/renderTarget." }
    val material = Material(graphicsDevice, uniformFloatCount, shadowMap)
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
).also { createdTextures += it }

internal fun Renderer.pbrImageView(asset: TextureAsset?, neutral: TextureAsset): Long =
    if (asset != null) {
        uploadTexture(asset).imageView.handle
    } else {
        neutralPbrTextures.getOrPut(neutral) { uploadTexture(neutral) }.imageView.handle
    }

internal fun Renderer.performCreateRenderTarget(width: Int, height: Int): RenderTarget {
    val target = OffscreenRenderTarget(
        graphicsDevice,
        renderPipeline.renderPass,
        width,
        height,
        swapchainManager.imageFormat.value,
    )
    createdRenderTargets += target
    return target
}

internal fun Renderer.performRenderToTexture(
    target: RenderTarget,
    camera: Lens,
    drawCalls: List<DrawCall>,
    light: SceneLight,
) {
    val offscreen = target as OffscreenRenderTarget
    val sceneRect =
        sceneViewport?.clampedTo(offscreen.width.toFloat(), offscreen.height.toFloat())
    val aspect = sceneRect?.aspect ?: (offscreen.width.toFloat() / offscreen.height.toFloat())
    val viewProjection = camera.viewProjectionMatrix(aspect, clipSpace)
    val preparedDrawCalls = prepareDrawCalls(
        frameIndex = commandBuffers.size,
        viewProjection = viewProjection,
        drawCalls = drawCalls,
        light = light,
        lightViewProjection = if (shadowMap != null) lightViewProjection(light) else null,
        cameraPosition = camera.eye,
    )

    runOffscreenCommands { commandBuffer ->
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
        commandRecorder.commandBuffer = commandBuffer
        preparedDrawCalls.groupBy { it.pipeline }.forEach { (pipeline, group) ->
            commandRecorder.bindPipeline(pipeline)
            recordDrawCalls(commandBuffer, group)
        }
        Vulkan.vkCmdEndRenderPass(commandBuffer)
        offscreen.transitionToShaderReadOnly(commandBuffer)
    }
}

internal suspend fun Renderer.performReadPixels(target: RenderTarget): TextureAsset {
    val offscreen = target as OffscreenRenderTarget
    val byteSize = (offscreen.width * offscreen.height * 4).toLong()
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
                offscreen.colorImage,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            )
            VulkanImages.vkCmdCopyImageToBuffer(
                commandBuffer,
                offscreen.colorImage,
                stagingBuffer,
                VkBufferImageCopy(imageWidth = offscreen.width, imageHeight = offscreen.height),
            )
            VulkanImages.vkTransitionImageLayout(
                commandBuffer,
                offscreen.colorImage,
                VkImageLayout2.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                VkImageLayout2.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
            )
        }
        pixels = VulkanBuffers.readBufferMemoryBytes(device, stagingMemory, 0, byteSize.toInt())
    } finally {
        VulkanBuffers.vkDestroyBuffer(device, stagingBuffer)
        VulkanBuffers.vkFreeMemory(device, stagingMemory)
    }
    return TextureAsset(pixels, offscreen.width, offscreen.height)
}
