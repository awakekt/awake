/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.renderer

import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.math.Lens
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.render.command.sortForRecording
import io.github.awakelab.awake.render.passes.RenderPassSlot
import io.github.awakelab.awake.render.renderer.shadowCascades
import io.github.awakelab.awake.render.renderer.DrawCall
import io.github.awakelab.awake.render.renderer.SceneLight
import io.github.awakelab.awake.render.texture.PbrTextureSet
import io.github.awakelab.awake.render.texture.RenderTarget
import io.github.awakelab.awake.render.texture.TextureAsset
import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.enums.VkSubpassContents
import io.github.awakelab.awake.vulkan.enums.flags.VkMemoryPropertyFlagBits
import io.github.awakelab.awake.vulkan.gen.VulkanBuffers
import io.github.awakelab.awake.vulkan.enums.VkImageLayout
import io.github.awakelab.awake.vulkan.gen.VulkanImages
import io.github.awakelab.awake.vulkan.material.Material
import io.github.awakelab.awake.vulkan.material.PbrImageViews
import io.github.awakelab.awake.vulkan.mesh.Mesh
import io.github.awakelab.awake.vulkan.models.VkExtent2D
import io.github.awakelab.awake.vulkan.models.VkRect2D
import io.github.awakelab.awake.vulkan.models.VkViewport
import io.github.awakelab.awake.vulkan.models.info.VkBufferCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkBufferImageCopy
import io.github.awakelab.awake.vulkan.models.info.VkBufferUsageFlagBits
import io.github.awakelab.awake.vulkan.models.info.VkImageLayout2
import io.github.awakelab.awake.vulkan.models.info.VkMemoryAllocateInfo
import io.github.awakelab.awake.vulkan.models.info.VkRenderPassBeginInfo
import io.github.awakelab.awake.vulkan.texture.OffscreenRenderTarget
import io.github.awakelab.awake.vulkan.texture.Texture
import io.github.awakelab.awake.render.material.Material as RenderMaterial
import io.github.awakelab.awake.render.mesh.Mesh as RenderMesh

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

/**
 * The same Scene-slot feature list the swapchain path records, rather than a second hand-rolled
 * grouping loop.
 *
 * That loop had drifted: it grouped every prepared draw by pipeline with no transparency
 * separation, so an offscreen render drew transparent surfaces in arbitrary order, and it
 * recorded no features at all -- no sky, no debug lines. StudioCameraPreview renders through
 * here, which is why its inset never showed either.
 */
private fun Renderer.recordOffscreenScene(
    commandBuffer: Long,
    preparedDrawCalls: List<PreparedDrawCall>,
    viewProjection: Mat4,
    camera: Lens,
    light: SceneLight,
) {
    val sorted = sortForRecording(preparedDrawCalls)
    // Bind before the first feature; VulkanCommandRecorder rebinds it after pipeline transitions
    // because a one-set content pipeline invalidates set 1.
    bindDepthSet(commandBuffer)
    recordSharedPassFeatures(
        RenderPassSlot.Scene,
        RendererFrameContext(
            renderer = this,
            commandBuffer = commandBuffer,
            frameIndex = commandBuffers.size,
            groupedDrawCalls = sorted.opaqueByPipeline,
            transparentDrawCalls = sorted.transparent,
            primaryPipeline = pipelineFor(renderPipeline.vertexFormat) ?: renderPipeline,
            viewProjection = viewProjection,
            cameraEye = camera.eye,
            light = light,
        ),
    )
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
        cascades = if (depthTarget != null) light.shadowCascades() else null,
        cameraPosition = camera.eye,
    )

    // Before the colour pass, exactly as the swapchain path does it: the scene's fragment shader
    // samples this frame's shadow map, so its depth content has to be complete first. Its absence
    // here is why an offscreen render used to show unshadowed geometry -- and why nothing could
    // pixel-test the shadow map at all.
    runOffscreenCommands { commandBuffer ->
        recordDepthPrePass(commandBuffer, preparedDrawCalls, light.shadowCascades())
        recordSceneDepthPass(commandBuffer, preparedDrawCalls, cameraDepthPass(viewProjection))
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
        commandRecorder.commandBuffer = commandBuffer
        recordOffscreenScene(commandBuffer, preparedDrawCalls, viewProjection, camera, light)
        Vulkan.vkCmdEndRenderPass(commandBuffer)
        offscreen.transitionToShaderReadOnly(commandBuffer)
    }
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
