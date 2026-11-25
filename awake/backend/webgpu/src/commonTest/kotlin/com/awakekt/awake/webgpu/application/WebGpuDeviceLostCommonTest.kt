/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.application

import com.awakekt.awake.webgpu.device.GraphicsDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Portable device-lost value and guard checks that run on both JVM and WasmJS without a GPU. */
class WebGpuDeviceLostCommonTest {
    @Test
    fun reasonStringsMapCaseInsensitivelyAndFallBackToUnknown() {
        assertEquals(WebGpuDeviceLostReason.Destroyed, WebGpuDeviceLostReason.of("destroyed"))
        assertEquals(WebGpuDeviceLostReason.Destroyed, WebGpuDeviceLostReason.of("Destroyed"))
        assertEquals(WebGpuDeviceLostReason.Unknown, WebGpuDeviceLostReason.of("unknown"))
        assertEquals(WebGpuDeviceLostReason.Unknown, WebGpuDeviceLostReason.of(""))
        assertEquals(WebGpuDeviceLostReason.Unknown, WebGpuDeviceLostReason.of("future-reason"))
    }

    @Test
    fun deviceLostExceptionCarriesLossAndFormatsMessage() {
        val loss = WebGpuDeviceLost(WebGpuDeviceLostReason.Unknown, "GPU process crashed")
        val exception = WebGpuDeviceLostException(loss)

        assertSame(loss, exception.loss)
        assertEquals("WebGPU device lost (Unknown): GPU process crashed", exception.message)
    }

    @Test
    fun graphicsDeviceLostStateTransitionsOnceAndIsIdempotent() {
        val device = GraphicsDevice()

        assertFalse(device.isDeviceLost)
        device.markDeviceLost()
        assertTrue(device.isDeviceLost)
        device.markDeviceLost()
        assertTrue(device.isDeviceLost)
    }
}
