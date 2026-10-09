/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.models.info

import com.awakekt.awake.vulkan.VkArray
import com.awakekt.awake.vulkan.VkHandle
import com.awakekt.awake.vulkan.VkHandleRef
import com.awakekt.awake.vulkan.enums.VkResult
import com.awakekt.awake.vulkan.enums.VkStructureType

/**
 * Parameters for presenting swapchain images (`VkPresentInfoKHR`).
 *
 * @property sType Identifies the structure to the driver; leave it at its default.
 * @property pNext Reserved for extension chaining. The binding forwards this field as a raw pointer
 * rather than marshalling a structure, so leave it `null`.
 * @property pWaitSemaphores The raw handles of the semaphores to wait for before presenting.
 * @property pSwapchains The raw handles of the swapchains to present to.
 * @property pImageIndices For each swapchain, the index of the image to present.
 * @property pResults Per-swapchain result codes written by the driver, or `null` to ignore them.
 */
class VkPresentInfoKHR(
    val sType: VkStructureType = VkStructureType.VK_STRUCTURE_TYPE_PRESENT_INFO_KHR,
    val pNext: Any? = null,
    @field:VkHandleRef("VkSemaphore")
    @VkArray("waitSemaphoreCount")
    val pWaitSemaphores: Array<VkHandle>? = null, // Use LongArray for Vulkan handles
    @field:VkHandleRef("VkSwapchainKHR")
    @VkArray("swapchainCount")
    val pSwapchains: Array<VkHandle>? = null, // Use LongArray for Vulkan handles
    val pImageIndices: IntArray? = null,
    val pResults: Array<VkResult>? = null, // Use IntArray for VkResult
)
