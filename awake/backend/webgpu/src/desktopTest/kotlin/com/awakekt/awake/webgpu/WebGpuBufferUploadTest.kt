/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.device.drivePendingWork
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals

class WebGpuBufferUploadTest {
    @Test
    fun smallUploadsPreserveTheRestOfALargerBuffer() = runBlocking {
        val context = glfwContextRenderer(width = 1, height = 1, title = "awake-buffer-upload")
        val graphicsDevice = GraphicsDevice().apply { create(context.wgpuContext) }
        val device = context.wgpuContext.device
        val buffer = device.createBuffer(
            BufferDescriptor(size = CAPACITY.toULong(), usage = GPUBufferUsage.CopyDst or GPUBufferUsage.MapRead),
        )
        try {
            val expected = ByteArray(CAPACITY) { SENTINEL }
            device.queue.writeBufferData(buffer, 0uL, fastArrayBufferOf(expected))
            val uploads = listOf(
                fastArrayBufferOf(floatArrayOf(1f, 0.5f, -2f, 4f)),
                fastArrayBufferOf(intArrayOf(7, -3, 0, 42)),
                fastArrayBufferOf(byteArrayOf(1, 2, 3, 4)),
            )
            uploads.forEachIndexed { index, data ->
                val offset = (index + 1) * UPLOAD_SPACING
                device.queue.writeBufferData(buffer, offset.toULong(), data)
                data.toByteArray().copyInto(expected, offset)
            }
            device.queue.submit(emptyList())
            coroutineScope {
                val mapping = async { buffer.mapAsync(GPUMapMode.Read) }
                device.drivePendingWork { mapping.isCompleted }
                mapping.await().getOrThrow()
            }
            assertContentEquals(expected, buffer.getMappedRange().toByteArray())
            buffer.unmap()
        } finally {
            buffer.close()
            graphicsDevice.destroy()
        }
    }

    private companion object {
        const val CAPACITY = 1024
        const val UPLOAD_SPACING = 32
        const val SENTINEL: Byte = 85
    }
}
