/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.device

import com.awakekt.awake.vulkan.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GraphicsDeviceCapabilitiesTest {

    @Test
    fun headlessDevicePopulatesCapabilitiesAndResetsOnTeardown() {
        val device = GraphicsDevice()
        assertEquals(DeviceCapabilities.Uninitialized, device.capabilities)
        assertEquals(Version(1, 0, 0), device.instanceVersion)

        device.createHeadless()
        try {
            assertTrue(device.instanceVersion >= Version(1, 1, 0), "instance version must be at least 1.1")
            val caps = device.capabilities
            assertNotEquals(DeviceCapabilities.Uninitialized, caps)
            assertTrue(caps.apiVersion >= Version(1, 0, 0))
            assertTrue(caps.deviceName.isNotBlank())
            println("Active Vulkan Device: ${caps.deviceName} (API ${caps.apiVersion}, driver ${caps.driverVersion})")
            println("Capabilities: dynamicRendering=${caps.dynamicRendering}, sync2=${caps.synchronization2}, timelineSemaphores=${caps.timelineSemaphores}")
        } finally {
            device.destroy()
        }

        assertEquals(DeviceCapabilities.Uninitialized, device.capabilities)
        assertEquals(Version(1, 0, 0), device.instanceVersion)
    }
}
