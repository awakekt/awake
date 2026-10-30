/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.enums.VkFormat
import com.awakekt.awake.vulkan.pipeline.createSceneRenderPass
import com.awakekt.awake.vulkan.swapchain.SwapchainManager
import com.awakekt.awake.vulkan.texture.OffscreenRenderTarget
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A 4x render target holds only its resolved 1x colour until a scene pass binds its framebuffer:
 * UI and image passes draw into that colour alone, so a target used only as a UI layer never
 * reserves the multisampled colour and depth a scene pass needs.
 *
 * Measured on lavapipe at 256 x 256: 262,144 bytes of 1x colour before the scene framebuffer,
 * then 1,048,576 bytes each of 4x colour and 4x depth after it, 2,359,296 bytes in all.
 * Negative control: building the scene attachments in the constructor, as before, reports all three
 * images at construction and fails the first assertion.
 */
class OffscreenTargetAttachmentsTest {
    @Test
    fun multisampledAttachmentsWaitForTheFirstScenePass() {
        val graphicsDevice = GraphicsDevice().apply { createHeadless() }
        val swapchainManager = SwapchainManager(graphicsDevice, 1).apply { createHeadless(SIZE, SIZE) }
        val renderPass = createSceneRenderPass(graphicsDevice, swapchainManager, SAMPLES)
        val format = swapchainManager.imageFormat.value
        val target = OffscreenRenderTarget(graphicsDevice, renderPass, SIZE, SIZE, format, sampleCount = SAMPLES)
        try {
            val uiOnly = target.attachmentAllocations
            println("UI-only 4x target: $uiOnly")
            assertEquals(listOf(format to 1), uiOnly.map { it.format to it.samples }, "A UI-only target holds its 1x colour alone")

            assertTrue(target.framebuffer != 0L)
            val scene = target.attachmentAllocations
            println("Scene 4x target: $scene, ${scene.sumOf { it.bytes }} bytes")
            assertEquals(
                listOf(format to 1, format to SAMPLES, VkFormat.VK_FORMAT_D32_SFLOAT.value to SAMPLES),
                scene.map { it.format to it.samples },
                "A scene pass adds the multisampled colour and depth",
            )
            assertTrue(scene.drop(1).all { it.bytes >= uiOnly.single().bytes * SAMPLES }, "Each 4x attachment takes at least four times the colour")
            val framebuffer = target.framebuffer
            assertEquals(scene, target.attachmentAllocations, "The scene attachments are built once")
            assertEquals(framebuffer, target.framebuffer)
        } finally {
            target.destroy()
            com.awakekt.awake.vulkan.Vulkan.vkDestroyRenderPass(graphicsDevice.device, renderPass)
            graphicsDevice.destroy()
        }
    }

    private companion object {
        const val SIZE = 256
        const val SAMPLES = 4
    }
}
