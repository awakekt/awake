/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.gen

import cnames.structs.VkCommandBuffer_T
import cnames.structs.VkDevice_T
import cnames.structs.VkFence_T
import cnames.structs.VkPipelineCache_T
import cnames.structs.VkPipeline_T
import cnames.structs.VkQueue_T
import cnames.structs.VkSemaphore_T
import cnames.structs.VkSwapchainKHR_T
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.enums.flags.VkAccessFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.models.info.VkDeviceCreateInfo
import com.awakekt.awake.vulkan.models.info.VkDeviceQueueCreateInfo
import com.awakekt.awake.vulkan.models.info.VkIndexType
import com.awakekt.awake.vulkan.models.physicaldevice.VkPhysicalDeviceFeatures
import com.awakekt.awake.vulkan.utils.VkResultException
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UIntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.set
import kotlinx.cinterop.toCPointer
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.MoltenVK.VK_PIPELINE_BIND_POINT_GRAPHICS
import platform.MoltenVK.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
import platform.MoltenVK.VK_STRUCTURE_TYPE_MEMORY_BARRIER
import platform.MoltenVK.VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO
import platform.MoltenVK.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR
import platform.MoltenVK.VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
import platform.MoltenVK.VK_STRUCTURE_TYPE_SUBMIT_INFO
import platform.MoltenVK.VK_SUBPASS_CONTENTS_INLINE
import platform.MoltenVK.VK_SUCCESS
import platform.MoltenVK.VkCommandBufferVar
import platform.MoltenVK.VkFenceVar
import platform.MoltenVK.VkPipelineCacheVar
import platform.MoltenVK.VkSemaphoreVar
import platform.MoltenVK.VkSwapchainKHRVar
import platform.MoltenVK.VkClearValue as NativeVkClearValue
import platform.MoltenVK.VkCommandBufferBeginInfo as NativeVkCommandBufferBeginInfo
import platform.MoltenVK.VkMemoryBarrier as NativeVkMemoryBarrier
import platform.MoltenVK.VkPipelineCacheCreateInfo as NativeVkPipelineCacheCreateInfo
import platform.MoltenVK.VkPresentInfoKHR as NativeVkPresentInfoKHR
import platform.MoltenVK.VkRect2D as NativeVkRect2D
import platform.MoltenVK.VkRenderPassBeginInfo as NativeVkRenderPassBeginInfo
import platform.MoltenVK.VkSubmitInfo as NativeVkSubmitInfo
import platform.MoltenVK.VkViewport as NativeVkViewport
import platform.MoltenVK.vkBeginCommandBuffer as nativeVkBeginCommandBuffer
import platform.MoltenVK.vkCmdBeginRenderPass as nativeVkCmdBeginRenderPass
import platform.MoltenVK.vkCmdBindPipeline as nativeVkCmdBindPipeline
import platform.MoltenVK.vkCmdPipelineBarrier as nativeVkCmdPipelineBarrier
import platform.MoltenVK.vkCmdSetScissor as nativeVkCmdSetScissor
import platform.MoltenVK.vkCmdSetViewport as nativeVkCmdSetViewport
import platform.MoltenVK.vkCreatePipelineCache as nativeVkCreatePipelineCache
import platform.MoltenVK.vkGetPipelineCacheData as nativeVkGetPipelineCacheData
import platform.MoltenVK.vkQueuePresentKHR as nativeVkQueuePresentKHR
import platform.MoltenVK.vkQueueSubmit as nativeVkQueueSubmit
import platform.MoltenVK.vkResetFences as nativeVkResetFences
import platform.MoltenVK.vkWaitForFences as nativeVkWaitForFences

/** iOS MoltenVK cinterop actual of [VulkanCommands]. */
@Suppress("TooManyFunctions")
@OptIn(ExperimentalForeignApi::class)
actual object VulkanCommands {
    /** Begins recording into [commandBuffer] with raw `VkCommandBufferUsageFlags` [flags]. */
    actual fun vkBeginCommandBuffer(commandBuffer: Long, flags: Int) = memScoped {
        val beginInfo = alloc<NativeVkCommandBufferBeginInfo>().apply {
            sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO
            pNext = null
            this.flags = flags.toUInt()
            pInheritanceInfo = null
        }
        val result = nativeVkBeginCommandBuffer(commandBuffer.toCPointer<VkCommandBuffer_T>(), beginInfo.ptr)
        check(result == VK_SUCCESS) { "vkBeginCommandBuffer failed: $result" }
    }

    /** Binds graphics [pipeline] (`VK_PIPELINE_BIND_POINT_GRAPHICS`) on [commandBuffer]. */
    actual fun vkCmdBindGraphicsPipeline(commandBuffer: Long, pipeline: Long) {
        nativeVkCmdBindPipeline(
            commandBuffer.toCPointer<VkCommandBuffer_T>(),
            VK_PIPELINE_BIND_POINT_GRAPHICS,
            pipeline.toCPointer<VkPipeline_T>(),
        )
    }

    /** Sets viewport 0 on [commandBuffer] from scalar coordinates. */
    actual fun vkCmdSetViewportRect(
        commandBuffer: Long,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        minDepth: Float,
        maxDepth: Float,
    ) = memScoped {
        val viewport = alloc<NativeVkViewport>().apply {
            this.x = x
            this.y = y
            this.width = width
            this.height = height
            this.minDepth = minDepth
            this.maxDepth = maxDepth
        }
        nativeVkCmdSetViewport(commandBuffer.toCPointer<VkCommandBuffer_T>(), 0u, 1u, viewport.ptr)
    }

    /** Sets scissor 0 on [commandBuffer] from scalar integer coordinates. */
    actual fun vkCmdSetScissorRect(commandBuffer: Long, x: Int, y: Int, width: Int, height: Int) = memScoped {
        val rect = alloc<NativeVkRect2D>().apply {
            offset.x = x
            offset.y = y
            extent.width = width.toUInt()
            extent.height = height.toUInt()
        }
        nativeVkCmdSetScissor(commandBuffer.toCPointer<VkCommandBuffer_T>(), 0u, 1u, rect.ptr)
    }

    /** Begins an inline render pass at origin `(0, 0)` with size `[width] x [height]` and no clear values. */
    actual fun vkCmdBeginRenderPassEmpty(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
    ) = memScoped {
        val beginInfo = alloc<NativeVkRenderPassBeginInfo>().apply {
            sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
            pNext = null
            this.renderPass = renderPass.toCPointer()
            this.framebuffer = framebuffer.toCPointer()
            renderArea.offset.x = 0
            renderArea.offset.y = 0
            renderArea.extent.width = width.toUInt()
            renderArea.extent.height = height.toUInt()
            clearValueCount = 0u
            pClearValues = null
        }
        nativeVkCmdBeginRenderPass(
            commandBuffer.toCPointer<VkCommandBuffer_T>(),
            beginInfo.ptr,
            VK_SUBPASS_CONTENTS_INLINE,
        )
    }

    /** Begins an inline render pass at origin `(0, 0)` clearing attachment 0 to RGBA `(r, g, b, a)`. */
    @Suppress("LongParameterList")
    actual fun vkCmdBeginRenderPassColor(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
    ) = memScoped {
        val clear = alloc<NativeVkClearValue>().apply {
            color.float32[0] = r
            color.float32[1] = g
            color.float32[2] = b
            color.float32[3] = a
        }
        val beginInfo = alloc<NativeVkRenderPassBeginInfo>().apply {
            sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
            pNext = null
            this.renderPass = renderPass.toCPointer()
            this.framebuffer = framebuffer.toCPointer()
            renderArea.offset.x = 0
            renderArea.offset.y = 0
            renderArea.extent.width = width.toUInt()
            renderArea.extent.height = height.toUInt()
            clearValueCount = 1u
            pClearValues = clear.ptr
        }
        nativeVkCmdBeginRenderPass(
            commandBuffer.toCPointer<VkCommandBuffer_T>(),
            beginInfo.ptr,
            VK_SUBPASS_CONTENTS_INLINE,
        )
    }

    /** Begins an inline render pass at origin `(0, 0)` clearing attachment 0 to depth [depth] and stencil 0. */
    @Suppress("LongParameterList")
    actual fun vkCmdBeginRenderPassDepth(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
        depth: Float,
    ) = memScoped {
        val clear = alloc<NativeVkClearValue>().apply {
            depthStencil.depth = depth
            depthStencil.stencil = 0u
        }
        val beginInfo = alloc<NativeVkRenderPassBeginInfo>().apply {
            sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
            pNext = null
            this.renderPass = renderPass.toCPointer()
            this.framebuffer = framebuffer.toCPointer()
            renderArea.offset.x = 0
            renderArea.offset.y = 0
            renderArea.extent.width = width.toUInt()
            renderArea.extent.height = height.toUInt()
            clearValueCount = 1u
            pClearValues = clear.ptr
        }
        nativeVkCmdBeginRenderPass(
            commandBuffer.toCPointer<VkCommandBuffer_T>(),
            beginInfo.ptr,
            VK_SUBPASS_CONTENTS_INLINE,
        )
    }

    /** Begins an inline render pass clearing attachment 0 to RGBA `(r, g, b, a)` and attachment 1 to depth [depth]. */
    @Suppress("LongParameterList")
    actual fun vkCmdBeginRenderPassColorDepth(
        commandBuffer: Long,
        renderPass: Long,
        framebuffer: Long,
        width: Int,
        height: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        depth: Float,
    ) = memScoped {
        val clears = allocArray<NativeVkClearValue>(2) { index: Int ->
            if (index == 0) {
                color.float32[0] = r
                color.float32[1] = g
                color.float32[2] = b
                color.float32[3] = a
            } else {
                depthStencil.depth = depth
                depthStencil.stencil = 0u
            }
        }
        val beginInfo = alloc<NativeVkRenderPassBeginInfo>().apply {
            sType = VK_STRUCTURE_TYPE_RENDER_PASS_BEGIN_INFO
            pNext = null
            this.renderPass = renderPass.toCPointer()
            this.framebuffer = framebuffer.toCPointer()
            renderArea.offset.x = 0
            renderArea.offset.y = 0
            renderArea.extent.width = width.toUInt()
            renderArea.extent.height = height.toUInt()
            clearValueCount = 2u
            pClearValues = clears
        }
        nativeVkCmdBeginRenderPass(
            commandBuffer.toCPointer<VkCommandBuffer_T>(),
            beginInfo.ptr,
            VK_SUBPASS_CONTENTS_INLINE,
        )
    }

    /** Waits for a single [fence] on [device] up to [timeout] nanoseconds. */
    actual fun vkWaitForFence(device: Long, fence: Long, timeout: Long) = memScoped {
        val fenceVar = alloc<VkFenceVar>().apply { value = fence.toCPointer() }
        nativeVkWaitForFences(device.toCPointer<VkDevice_T>(), 1u, fenceVar.ptr, 1u, timeout.toULong())
        Unit
    }

    /** Resets a single [fence] on [device]. */
    actual fun vkResetFence(device: Long, fence: Long) = memScoped {
        val fenceVar = alloc<VkFenceVar>().apply { value = fence.toCPointer() }
        nativeVkResetFences(device.toCPointer<VkDevice_T>(), 1u, fenceVar.ptr)
        Unit
    }

    /** Submits a single [commandBuffer] to [queue] with optional wait/signal semaphores and [fence]. */
    @Suppress("LongParameterList")
    actual fun vkQueueSubmitSingle(
        queue: Long,
        commandBuffer: Long,
        waitSemaphore: Long,
        waitStageMask: Int,
        signalSemaphore: Long,
        fence: Long,
    ) = memScoped {
        val cmdVar = alloc<VkCommandBufferVar>().apply { value = commandBuffer.toCPointer() }
        val waitSemVar = if (waitSemaphore != 0L) alloc<VkSemaphoreVar>().apply { value = waitSemaphore.toCPointer() } else null
        val waitStageVar = if (waitSemaphore != 0L) alloc<UIntVar>().apply { value = waitStageMask.toUInt() } else null
        val signalSemVar = if (signalSemaphore != 0L) alloc<VkSemaphoreVar>().apply { value = signalSemaphore.toCPointer() } else null
        val submitInfo = alloc<NativeVkSubmitInfo>().apply {
            sType = VK_STRUCTURE_TYPE_SUBMIT_INFO
            pNext = null
            waitSemaphoreCount = if (waitSemVar != null) 1u else 0u
            pWaitSemaphores = waitSemVar?.ptr
            pWaitDstStageMask = waitStageVar?.ptr
            commandBufferCount = 1u
            pCommandBuffers = cmdVar.ptr
            signalSemaphoreCount = if (signalSemVar != null) 1u else 0u
            pSignalSemaphores = signalSemVar?.ptr
        }
        val result = nativeVkQueueSubmit(
            queue.toCPointer<VkQueue_T>(),
            1u,
            submitInfo.ptr,
            if (fence != 0L) fence.toCPointer<VkFence_T>() else null,
        )
        check(result == VK_SUCCESS) { "vkQueueSubmit failed: $result" }
    }

    /** Presents [imageIndex] of [swapchain] on [queue], answering `VK_SUBOPTIMAL_KHR` and `VK_ERROR_OUT_OF_DATE_KHR`. */
    actual fun vkQueuePresentSingleStatus(
        queue: Long,
        swapchain: Long,
        imageIndex: Int,
        waitSemaphore: Long,
    ): VkResult = memScoped {
        val waitSemVar = if (waitSemaphore != 0L) alloc<VkSemaphoreVar>().apply { value = waitSemaphore.toCPointer() } else null
        val swapchainVar = alloc<VkSwapchainKHRVar>().apply { value = swapchain.toCPointer() }
        val indexVar = alloc<UIntVar>().apply { value = imageIndex.toUInt() }
        val presentInfo = alloc<NativeVkPresentInfoKHR>().apply {
            sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR
            pNext = null
            waitSemaphoreCount = if (waitSemVar != null) 1u else 0u
            pWaitSemaphores = waitSemVar?.ptr
            swapchainCount = 1u
            pSwapchains = swapchainVar.ptr
            pImageIndices = indexVar.ptr
            pResults = null
        }
        val result = nativeVkQueuePresentKHR(queue.toCPointer<VkQueue_T>(), presentInfo.ptr)
        val expected = result == VK_SUCCESS ||
            result == VkResult.VK_SUBOPTIMAL_KHR.value ||
            result == VkResult.VK_ERROR_OUT_OF_DATE_KHR.value
        if (!expected) {
            val vkResult = VkResult.entries.firstOrNull { it.value == result }
                ?: error("vkQueuePresentKHR returned an unknown VkResult: $result")
            throw VkResultException("vkQueuePresentKHR failed: $result", vkResult)
        }
        vkResultFromPresentCode(result)
    }

    /** Creates a `VkPipelineCache` optionally seeded with [initialData] bytes. */
    actual fun vkCreatePipelineCacheFromBytes(device: Long, initialData: ByteArray?): Long = memScoped {
        val cacheVar = alloc<VkPipelineCacheVar>()
        val result = if (initialData != null && initialData.isNotEmpty()) {
            initialData.usePinned { pinned ->
                val createInfo = alloc<NativeVkPipelineCacheCreateInfo>().apply {
                    sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO
                    pNext = null
                    flags = 0u
                    initialDataSize = initialData.size.toULong()
                    pInitialData = pinned.addressOf(0)
                }
                nativeVkCreatePipelineCache(device.toCPointer(), createInfo.ptr, null, cacheVar.ptr)
            }
        } else {
            val createInfo = alloc<NativeVkPipelineCacheCreateInfo>().apply {
                sType = VK_STRUCTURE_TYPE_PIPELINE_CACHE_CREATE_INFO
                pNext = null
                flags = 0u
                initialDataSize = 0u
                pInitialData = null
            }
            nativeVkCreatePipelineCache(device.toCPointer(), createInfo.ptr, null, cacheVar.ptr)
        }
        check(result == VK_SUCCESS) { "vkCreatePipelineCache failed: $result" }
        cacheVar.value!!.rawValue.toLong()
    }

    /** Exports the current contents of [pipelineCache] as raw bytes. */
    actual fun vkGetPipelineCacheData(device: Long, pipelineCache: Long): ByteArray = memScoped {
        val sizeVar = alloc<ULongVar>()
        val first = nativeVkGetPipelineCacheData(
            device.toCPointer(),
            pipelineCache.toCPointer<VkPipelineCache_T>(),
            sizeVar.ptr,
            null,
        )
        check(first == VK_SUCCESS) { "vkGetPipelineCacheData(size) failed: $first" }
        val byteCount = sizeVar.value.toInt()
        if (byteCount <= 0) return@memScoped ByteArray(0)
        val out = ByteArray(byteCount)
        out.usePinned { pinned ->
            val second = nativeVkGetPipelineCacheData(
                device.toCPointer(),
                pipelineCache.toCPointer<VkPipelineCache_T>(),
                sizeVar.ptr,
                pinned.addressOf(0),
            )
            check(second == VK_SUCCESS) { "vkGetPipelineCacheData(read) failed: $second" }
        }
        out
    }

    /** Queries supported Vulkan 1.2/1.3 core features; MoltenVK keeps conservative 1.0/1.1 path. */
    actual fun vkQueryModernFeatures(instance: Long, physicalDevice: Long, apiVersion: Int): Int = 0

    /** Creates a logical `VkDevice` enabling only the Vulkan 1.0 features in [features10Mask]. */
    @Suppress("LongParameterList")
    actual fun vkCreateLogicalDeviceWithFeatures(
        physicalDevice: Long,
        queueFamilyIndices: IntArray,
        enabledExtensions: Array<String>,
        features10Mask: Int,
        apiVersion: Int,
        modernFeaturesMask: Int,
    ): Long {
        val queueInfos = queueFamilyIndices.map { index ->
            VkDeviceQueueCreateInfo(queueFamilyIndex = index, queueCount = 1, pQueuePriorities = floatArrayOf(1.0f))
        }.toTypedArray()
        val features = VkPhysicalDeviceFeatures(
            fullDrawIndexUint32 = (features10Mask and VulkanFeatures10.FULL_DRAW_INDEX_UINT32) != 0,
            multiDrawIndirect = (features10Mask and VulkanFeatures10.MULTI_DRAW_INDIRECT) != 0,
            depthClamp = (features10Mask and VulkanFeatures10.DEPTH_CLAMP) != 0,
            fillModeNonSolid = (features10Mask and VulkanFeatures10.FILL_MODE_NON_SOLID) != 0,
            samplerAnisotropy = (features10Mask and VulkanFeatures10.SAMPLER_ANISOTROPY) != 0,
            textureCompressionETC2 = (features10Mask and VulkanFeatures10.TEXTURE_COMPRESSION_ETC2) != 0,
            textureCompressionASTC_LDR = (features10Mask and VulkanFeatures10.TEXTURE_COMPRESSION_ASTC_LDR) != 0,
            textureCompressionBC = (features10Mask and VulkanFeatures10.TEXTURE_COMPRESSION_BC) != 0,
        )
        return Vulkan.vkCreateDevice(
            physicalDevice,
            VkDeviceCreateInfo(
                pQueueCreateInfos = queueInfos,
                pEnabledFeatures = arrayOf(features),
                ppEnabledExtensionNames = enabledExtensions,
            ),
        )
    }

    /** Single-call fast path combining non-zero state binds with `vkCmdDrawIndexed`. */
    @Suppress("LongParameterList")
    actual fun vkCmdDrawIndexedWithState(
        commandBuffer: Long,
        pipeline: Long,
        pipelineLayout: Long,
        descriptorSet0: Long,
        vertexBuffer0: Long,
        indexBuffer: Long,
        indexCount: Int,
        instanceCount: Int,
        firstIndex: Int,
    ) {
        if (pipeline != 0L) vkCmdBindGraphicsPipeline(commandBuffer, pipeline)
        if (descriptorSet0 != 0L) {
            VulkanDescriptors.vkCmdBindDescriptorSet(commandBuffer, pipelineLayout, 0, descriptorSet0)
        }
        if (vertexBuffer0 != 0L) VulkanBuffers.vkCmdBindVertexBuffer(commandBuffer, 0, vertexBuffer0, 0L)
        if (indexBuffer != 0L) {
            VulkanBuffers.vkCmdBindIndexBuffer(commandBuffer, indexBuffer, 0L, VkIndexType.VK_INDEX_TYPE_UINT32)
        }
        VulkanBuffers.vkCmdDrawIndexed(commandBuffer, indexCount, instanceCount, firstIndex, 0, 0)
    }

    /** Single-call fast path combining non-zero state binds with non-indexed `vkCmdDraw`. */
    @Suppress("LongParameterList")
    actual fun vkCmdDrawWithState(
        commandBuffer: Long,
        pipeline: Long,
        pipelineLayout: Long,
        descriptorSet0: Long,
        vertexBuffer0: Long,
        vertexCount: Int,
        instanceCount: Int,
    ) {
        if (pipeline != 0L) vkCmdBindGraphicsPipeline(commandBuffer, pipeline)
        if (descriptorSet0 != 0L) {
            VulkanDescriptors.vkCmdBindDescriptorSet(commandBuffer, pipelineLayout, 0, descriptorSet0)
        }
        if (vertexBuffer0 != 0L) VulkanBuffers.vkCmdBindVertexBuffer(commandBuffer, 0, vertexBuffer0, 0L)
        Vulkan.vkCmdDraw(commandBuffer, vertexCount, instanceCount, 0, 0)
    }

    /** Records a `TRANSFER_WRITE -> VERTEX_INPUT | VERTEX_SHADER | FRAGMENT_SHADER` global memory barrier. */
    actual fun vkCmdTransferToGraphicsBarrier(commandBuffer: Long) = memScoped {
        val barrier = alloc<NativeVkMemoryBarrier>().apply {
            sType = VK_STRUCTURE_TYPE_MEMORY_BARRIER
            pNext = null
            srcAccessMask = VkAccessFlagBits.VK_ACCESS_TRANSFER_WRITE_BIT.value.toUInt()
            dstAccessMask = (
                VkAccessFlagBits.VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT.value or
                    VkAccessFlagBits.VK_ACCESS_INDEX_READ_BIT.value or
                    VkAccessFlagBits.VK_ACCESS_UNIFORM_READ_BIT.value or
                    VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value
                ).toUInt()
        }
        val dstStage = (
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_VERTEX_INPUT_BIT.value or
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_VERTEX_SHADER_BIT.value or
                VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value
            ).toUInt()
        nativeVkCmdPipelineBarrier(
            commandBuffer.toCPointer(),
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_TRANSFER_BIT.value.toUInt(),
            dstStage,
            0u,
            1u,
            barrier.ptr,
            0u,
            null,
            0u,
            null,
        )
    }
}
