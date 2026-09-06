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
