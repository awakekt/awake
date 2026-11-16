/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The public renderer names its GPU (#1700): `VkPhysicalDeviceProperties`' name, vendor id and driver
 * version, which only the backend's internal capabilities carried before. CI's GPU is lavapipe, a CPU
 * device that names itself `llvmpipe`.
 */
class RendererDeviceInfoTest {
    @Test
    fun theRendererNamesItsGpuVendorAndDriver() {
        vulkanHeadlessScene(SIZE, SIZE).use { session ->
            val renderer = session.renderer
            val name = assertNotNull(renderer.deviceName, "a Vulkan device always has a name")
            println("RendererDeviceInfo: '$name', vendor 0x${renderer.vendorId?.toString(HEX)}, driver ${renderer.driverVersion}, ${renderer.deviceClass}")
            assertTrue(name.isNotBlank())
            assertNotNull(renderer.vendorId, "a Vulkan device always has a vendor id")
            assertNotNull(renderer.driverVersion)
            if (renderer.deviceClass == GpuDeviceClass.Software) {
                assertTrue(name.startsWith("llvmpipe") || "SwiftShader" in name, "a CPU device is lavapipe or SwiftShader: '$name'")
            }
        }
    }

    private companion object {
        const val SIZE = 16
        const val HEX = 16
    }
}
