/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.commands

import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.device.GraphicsDevice
import io.github.awakelab.awake.vulkan.enums.VkCommandBufferLevel
import io.github.awakelab.awake.vulkan.enums.flags.VkCommandBufferUsageFlagBits
import io.github.awakelab.awake.vulkan.enums.flags.VkCommandPoolCreateFlagBits
import io.github.awakelab.awake.vulkan.handles.CommandPoolHandle
import io.github.awakelab.awake.vulkan.models.info.VkCommandBufferAllocateInfo
import io.github.awakelab.awake.vulkan.models.info.VkCommandBufferBeginInfo
import io.github.awakelab.awake.vulkan.models.info.VkCommandPoolCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkFenceCreateInfo
import io.github.awakelab.awake.vulkan.models.info.VkSubmitInfo
import io.github.awakelab.awake.vulkan.utils.findQueueFamilies

/**
 * Phase 2 (renderer abstraction): owns the command pool and the graphics queue used for
 * one-time (upload/transfer) command buffers -- extracted verbatim from
 * `VulkanApplication`'s `createCommandPool`/`runOneTimeCommands` functions. This is the
 * collaborator [io.github.awakelab.awake.vulkan.mesh.Mesh] and
 * [io.github.awakelab.awake.vulkan.texture.Texture] previously received as a bare
 * `((commandBuffer: Long) -> Unit) -> Unit` lambda -- passing this instead (or its bound
 * `::runOneTimeCommands` method reference, which is call-compatible) gives callers a real
 * object to depend on instead of a floating function.
 *
 * [commandPool] is also exposed directly because `VulkanApplication` still allocates its
 * per-frame render command buffers from the same pool (a swapchain-frame concern, not a
 * transfer concern, so it isn't moved here).
 */
class TransferContext(graphicsDevice: GraphicsDevice) {
    private val graphicsDevice = graphicsDevice
    private val device get() = graphicsDevice.device
    private val physicalDevice get() = graphicsDevice.physicalDevice
    private val surface get() = graphicsDevice.surface
    private val graphicsQueue get() = graphicsDevice.graphicsQueue

    var commandPool: CommandPoolHandle = CommandPoolHandle(0)
    private var uploadCommandBuffer: Long = 0
    private var uploadFence: Long = 0

    init {
        val (graphicsFamily, _) = findQueueFamilies(physicalDevice, surface)

        val poolInfo = VkCommandPoolCreateInfo(
            flags = VkCommandPoolCreateFlagBits.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT.value,
            queueFamilyIndex = graphicsFamily!!,
        )

        commandPool = CommandPoolHandle(Vulkan.vkCreateCommandPool(device, poolInfo))
    }

    /** Runs [block] on one resettable upload command buffer. Uploads are synchronous here, so
     * the fence makes the buffer safe to reset and reuse on the next call without retaining one
     * command buffer for every mesh or texture loaded during the session. */
    fun runOneTimeCommands(block: (Long) -> Unit) {
        ensureUploadResources()
        Vulkan.vkResetCommandBuffer(uploadCommandBuffer, 0)
        Vulkan.vkBeginCommandBuffer(
            uploadCommandBuffer,
            VkCommandBufferBeginInfo(
                flags = VkCommandBufferUsageFlagBits.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT.value,
            ),
        )
        block(uploadCommandBuffer)
        Vulkan.vkEndCommandBuffer(uploadCommandBuffer)

        Vulkan.vkResetFences(device, longArrayOf(uploadFence))
        Vulkan.vkQueueSubmit(
            graphicsQueue,
            arrayOf(VkSubmitInfo(pCommandBuffers = arrayOf(uploadCommandBuffer))),
            uploadFence,
        )
        Vulkan.vkWaitForFences(device, longArrayOf(uploadFence), true, Long.MAX_VALUE)
    }

    fun destroy() {
        if (uploadFence != 0L) Vulkan.vkDestroyFence(device, uploadFence)
        Vulkan.vkDestroyCommandPool(device, commandPool.handle)
    }

    private fun ensureUploadResources() {
        if (uploadCommandBuffer != 0L) return
        uploadCommandBuffer = Vulkan.vkAllocateCommandBuffers(
            device,
            VkCommandBufferAllocateInfo(
                commandPool = commandPool.handle,
                level = VkCommandBufferLevel.VK_COMMAND_BUFFER_LEVEL_PRIMARY,
                commandBufferCount = 1,
            ),
        )
        uploadFence = Vulkan.vkCreateFence(device, VkFenceCreateInfo())
    }
}
