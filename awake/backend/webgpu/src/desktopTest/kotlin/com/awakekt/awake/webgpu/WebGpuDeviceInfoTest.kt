/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.render.pipeline.GpuDeviceClass
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The public renderer names its GPU from the adapter's info (#1700). wgpu-native reports it; a browser
 * may not, which is why [com.awakekt.awake.render.pipeline.GpuDevice.deviceName] is nullable. WebGPU
 * names the vendor rather than numbering it, so the vendor id stays null.
 */
class WebGpuDeviceInfoTest {
    @Test
    fun theRendererNamesItsAdapter() = webGpuHeadlessUi().use { session ->
        val renderer = session.renderer
        val name = assertNotNull(renderer.deviceName, "wgpu-native reports its adapter")
        println("WebGpuDeviceInfo: '$name', ${renderer.deviceClass}")
        assertTrue(name.isNotBlank())
        assertNull(renderer.vendorId, "WebGPU reports no PCI vendor id")
        if (renderer.deviceClass == GpuDeviceClass.Software) {
            assertTrue(name.startsWith("llvmpipe") || "SwiftShader" in name, "a CPU adapter is lavapipe or SwiftShader: '$name'")
        }
    }
}
