/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.webgpu.device.withGpuTiming
import com.awakekt.awake.webgpu.renderer.GpuFrameTimer
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUFeatureName
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Measures actual native GPU query readback; the shared counter separately verifies the exact frame sum. */
class WebGpuFrameTimerTest {
    @Test
    fun optionalTimingReportsCompletedRenderPassesWithoutUncapturedErrors() = runBlocking {
        val errors = mutableListOf<String>()
        val original = glfwContextRenderer(width = 16, height = 16, title = "awake-gpu-timer", onUncapturedError = { errors += it.message })
        assertNull(GpuFrameTimer.createOrNull(original.wgpuContext.device), "an unrequested optional feature must keep timing disabled")
        val context = original.wgpuContext.withGpuTiming { errors += it.message }
        val device = context.device
        if (GPUFeatureName.TimestampQuery !in context.adapter.features) {
            assertNull(GpuFrameTimer.createOrNull(device))
            context.close()
            return@runBlocking
        }
        val timer = assertNotNull(GpuFrameTimer.createOrNull(device))
        val texture = device.createTexture(
            TextureDescriptor(size = Extent3D(16u, 16u), format = GPUTextureFormat.RGBA8Unorm, usage = GPUTextureUsage.RenderAttachment),
        )
        val view = texture.createView()
        try {
            // Two offscreen submissions followed by the presented-frame boundary.
            repeat(3) {
                val encoder = timer.createEncoder()
                repeat(2) {
                    encoder.beginRenderPass(
                        RenderPassDescriptor(
                            colorAttachments = listOf(RenderPassColorAttachment(view, GPULoadOp.Clear, GPUStoreOp.Store, clearValue = Color(1.0, 0.0, 0.0, 1.0))),
                        ),
                    ).end()
                }
                device.queue.submit(listOf(encoder.finish()))
                timer.submitted(encoder)
            }
            timer.publish()
            withTimeout(10_000) {
                while (timer.lastMs == null) {
                    delay(1)
                    timer.collectCompleted()
                }
            }
            println("Measured WebGPU frame time for six passes: ${timer.lastMs} ms")
            assertTrue(assertNotNull(timer.lastMs) > 0f, "the native GPU must report the measured render passes")
            assertEquals(emptyList(), errors)
        } finally {
            timer.destroy()
            view.close()
            texture.close()
            context.close()
        }
    }
}
