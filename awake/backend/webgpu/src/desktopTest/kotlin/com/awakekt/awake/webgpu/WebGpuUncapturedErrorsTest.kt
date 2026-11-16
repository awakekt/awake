/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.webgpu.renderer.Renderer
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The harness that makes a WebGPU validation error fail its test (#1592). wgpu reports the error
 * through the device's callback rather than throwing where it was made, so before this a test that
 * printed one, or threw it on the callback's thread, still passed.
 *
 * The error here is provoked on purpose, so it is recorded without the printed marker the build's
 * backstop fails the run on.
 */
class WebGpuUncapturedErrorsTest {
    @Test
    fun aValidationErrorInASessionFailsItsTestWhenTheSessionCloses() {
        val session = webGpuHeadlessUi(onUncapturedError = WebGpuUncapturedErrors::record)
        val device = (session.renderer as Renderer).graphicsDevice.wgpuContext.device
        // MapRead may pair only with CopyDst: a vertex buffer that maps for reading is invalid.
        device.createBuffer(BufferDescriptor(size = 16u, usage = GPUBufferUsage.MapRead or GPUBufferUsage.Vertex)).close()

        val failure = assertFailsWith<IllegalStateException> { session.close() }
        assertTrue("webGpuHeadlessUi's session raised 1 uncaptured WebGPU error" in failure.message.orEmpty(), "${failure.message}")
        assertEquals(emptyList(), WebGpuUncapturedErrors.drain(), "the failure took the errors with it")
    }

    @Test
    fun aCleanSessionClosesQuietly() {
        webGpuHeadlessUi().use { session -> (session.renderer as Renderer).graphicsDevice.wgpuContext.device }
    }
}
