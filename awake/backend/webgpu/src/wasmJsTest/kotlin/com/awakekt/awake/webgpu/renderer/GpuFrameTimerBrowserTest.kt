/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.webgpu.device.createCanvasGpuContext
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import web.dom.document
import web.html.HTMLCanvasElement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Exercises the shipping canvas feature request and actual browser timestamp readback. */
class GpuFrameTimerBrowserTest {
    @Test
    fun canvasRequestsOptionalTimingAndReadsOffscreenPasses() = runTest {
        val errors = mutableListOf<String>()
        val canvas = document.createElement("canvas") as HTMLCanvasElement
        val context = createCanvasGpuContext(canvas, 16, 16) { errors += it.message }
        val device = context.device
        assertEquals(GPUFeatureName.TimestampQuery in context.adapter.features, GPUFeatureName.TimestampQuery in device.features)
        if (GPUFeatureName.TimestampQuery !in device.features) {
            assertNull(GpuFrameTimer.createOrNull(device))
            context.close()
            return@runTest
        }
        val timer = assertNotNull(GpuFrameTimer.createOrNull(device))
        val texture = device.createTexture(
            TextureDescriptor(size = Extent3D(16u, 16u), format = GPUTextureFormat.RGBA8Unorm, usage = GPUTextureUsage.RenderAttachment),
        )
        val view = texture.createView()
        try {
            repeat(3) {
                val encoder = timer.createEncoder()
                encoder.beginRenderPass(
                    RenderPassDescriptor(
                        colorAttachments = listOf(RenderPassColorAttachment(view, GPULoadOp.Clear, GPUStoreOp.Store, clearValue = Color(1.0, 0.0, 0.0, 1.0))),
                    ),
                ).end()
                device.queue.submit(listOf(encoder.finish()))
                timer.submitted(encoder)
            }
            timer.publish()
            // Real event-loop delay: virtual test time must not outrun the browser's mapAsync.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                withTimeout(10_000) {
                    while (timer.lastMs == null) {
                        delay(10)
                        timer.collectCompleted()
                    }
                }
            }
            println("Measured browser WebGPU frame time: ${timer.lastMs} ms")
            assertTrue(assertNotNull(timer.lastMs) >= 0f) // Browser privacy quantization can round short passes to zero.
            assertEquals(emptyList(), errors)
        } finally {
            timer.destroy()
            view.close()
            texture.close()
            context.close()
        }
    }
}
