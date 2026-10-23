/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import com.awakekt.awake.vulkan.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeviceCapabilitiesTest {

    @Test
    fun uninitializedDefaultsAreSafeAndDisabled() {
        val caps = DeviceCapabilities.Uninitialized
        assertEquals(Version(1, 0, 0), caps.apiVersion)
        assertEquals(0, caps.driverVersion)
        assertEquals("Uninitialized", caps.deviceName)
        assertEquals(GpuDeviceClass.Unknown, caps.deviceClass)
        assertFalse(caps.isVulkan13OrHigher)
        assertFalse(caps.isVulkan14OrHigher)
        assertFalse(caps.dynamicRendering)
        assertFalse(caps.synchronization2)
        assertFalse(caps.timelineSemaphores)
        assertFalse(caps.extendedDynamicState)
        assertFalse(caps.pushDescriptors)
        assertTrue(caps.enabledExtensions.isEmpty())
    }

    @Test
    fun detectsVulkan13Capabilities() {
        val caps = DeviceCapabilities(
            apiVersion = Version(1, 3, 0),
            driverVersion = 100,
            deviceName = "Vulkan 1.3 Test Device",
            deviceClass = GpuDeviceClass.Discrete,
            dynamicRendering = true,
            synchronization2 = true,
            timelineSemaphores = true,
            extendedDynamicState = true,
            extendedDynamicState2 = true,
            descriptorIndexing = true,
            bufferDeviceAddress = true,
            drawIndirectCount = true,
            pipelineCreationCacheControl = true,
            pushDescriptors = false,
            dynamicRenderingLocalRead = false,
            maintenance4 = true,
            maintenance5 = false,
            maintenance6 = false,
            scalarBlockLayout = true,
            hostImageCopy = false,
            lineRasterization = false,
            enabledExtensions = setOf("VK_KHR_swapchain"),
        )

        assertTrue(caps.isVulkan13OrHigher)
        assertFalse(caps.isVulkan14OrHigher)
        assertTrue(caps.dynamicRendering)
        assertTrue(caps.synchronization2)
        assertTrue(caps.timelineSemaphores)
        assertTrue(caps.maintenance4)
        assertFalse(caps.pushDescriptors)
    }

    @Test
    fun detectsVulkan14Capabilities() {
        val caps = DeviceCapabilities(
            apiVersion = Version(1, 4, 0),
            driverVersion = 200,
            deviceName = "Vulkan 1.4 Test Device",
            deviceClass = GpuDeviceClass.Discrete,
            dynamicRendering = true,
            synchronization2 = true,
            timelineSemaphores = true,
            extendedDynamicState = true,
            extendedDynamicState2 = true,
            descriptorIndexing = true,
            bufferDeviceAddress = true,
            drawIndirectCount = true,
            pipelineCreationCacheControl = true,
            pushDescriptors = true,
            dynamicRenderingLocalRead = true,
            maintenance4 = true,
            maintenance5 = true,
            maintenance6 = true,
            scalarBlockLayout = true,
            hostImageCopy = true,
            lineRasterization = true,
            enabledExtensions = setOf("VK_KHR_swapchain"),
        )

        assertTrue(caps.isVulkan13OrHigher)
        assertTrue(caps.isVulkan14OrHigher)
        assertTrue(caps.pushDescriptors)
        assertTrue(caps.maintenance5)
        assertTrue(caps.maintenance6)
        assertTrue(caps.dynamicRenderingLocalRead)
        assertTrue(caps.hostImageCopy)
    }
}
