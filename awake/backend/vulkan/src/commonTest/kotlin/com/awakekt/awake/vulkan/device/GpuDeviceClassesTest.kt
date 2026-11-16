/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.render.renderer.RenderQuality
import com.awakekt.awake.render.renderer.RenderQualityPolicy
import com.awakekt.awake.render.renderer.RenderScale
import com.awakekt.awake.vulkan.enums.VkPhysicalDeviceType
import kotlin.test.Test
import kotlin.test.assertEquals

/** A phone's Vulkan GPU, which its driver reports as integrated, classes as Mobile (#1688). */
class GpuDeviceClassesTest {
    @Test
    fun anIntegratedGpuOnAPhoneIsMobileAndOnADesktopIsIntegrated() {
        assertEquals(GpuDeviceClass.Mobile, VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU.toGpuDeviceClass(handheld = true))
        assertEquals(GpuDeviceClass.Mobile, VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU.toGpuDeviceClass(handheld = true))
        assertEquals(GpuDeviceClass.Mobile, VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_OTHER.toGpuDeviceClass(handheld = true))
        assertEquals(GpuDeviceClass.Integrated, VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU.toGpuDeviceClass(handheld = false))
        assertEquals(GpuDeviceClass.Integrated, VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_VIRTUAL_GPU.toGpuDeviceClass(handheld = false))
        assertEquals(GpuDeviceClass.Unknown, VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_OTHER.toGpuDeviceClass(handheld = false))
    }

    @Test
    fun discreteAndSoftwareDevicesKeepTheirClassAnywhere() {
        for (handheld in listOf(true, false)) {
            assertEquals(GpuDeviceClass.Discrete, VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_DISCRETE_GPU.toGpuDeviceClass(handheld))
            assertEquals(GpuDeviceClass.Software, VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_CPU.toGpuDeviceClass(handheld))
        }
    }

    /** The phone in #1688: a 2400 by 1080 panel drew the full 1080 lines as Integrated; as Mobile, 720. */
    @Test
    fun autoOnThePhoneFromTheIssueDrawsSevenHundredTwentyLines() {
        val asBefore = RenderQualityPolicy.renderScale(RenderQuality.Auto, 2400, 1080, GpuDeviceClass.Integrated)
        val asMobile = RenderQualityPolicy.renderScale(
            RenderQuality.Auto,
            2400,
            1080,
            VkPhysicalDeviceType.VK_PHYSICAL_DEVICE_TYPE_INTEGRATED_GPU.toGpuDeviceClass(handheld = true),
        )
        assertEquals(1080, RenderScale.sceneExtent(1080, asBefore))
        assertEquals(720, RenderScale.sceneExtent(1080, asMobile))
        assertEquals(false, runsOnHandheld, "the desktop tests run on a desktop")
    }
}
