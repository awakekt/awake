/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.models.info

import io.github.awakelab.awake.vulkan.VkArray
import io.github.awakelab.awake.vulkan.VkHandle
import io.github.awakelab.awake.vulkan.VkHandleRef
import io.github.awakelab.awake.vulkan.enums.VkResult
import io.github.awakelab.awake.vulkan.enums.VkStructureType

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
