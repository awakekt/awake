/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * One batch of work submitted to a queue (`VkSubmitInfo`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property pWaitSemaphores The raw handles of the semaphores to wait for before executing the
 * batch.
 * @property pWaitDstStageMask For each wait semaphore, the pipeline stages that must wait for it.
 * @property pCommandBuffers The raw handles of the command buffers to execute.
 * @property pSignalSemaphores The raw handles of the semaphores to signal when the batch finishes.
 */
class VkSubmitInfo(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_SUBMIT_INFO,
    val pNext: Any? = null,
    @field:VkHandleRef("VkSemaphore")
    @field:VkArray("waitSemaphoreCount")
    val pWaitSemaphores: Array<VkHandle>? = null,
    val pWaitDstStageMask: IntArray? = null,
    @field:VkHandleRef("VkCommandBuffer")
    @field:VkArray("commandBufferCount")
    val pCommandBuffers: Array<VkHandle>? = null,
    @field:VkHandleRef("VkSemaphore")
    @field:VkArray("signalSemaphoreCount")
    val pSignalSemaphores: Array<VkHandle>? = null,
)
