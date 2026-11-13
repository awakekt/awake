/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.device.drivePendingWork
import com.awakekt.awake.webgpu.ui.DynamicMesh
import com.awakekt.awake.webgpu.ui.MeshUploadStaging
import com.awakekt.awake.webgpu.ui.writeMeshFloats
import com.awakekt.awake.webgpu.ui.writeMeshInts
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertSame

/**
 * #1408: UI mesh uploads go through kept host memory rather than a new buffer per array per upload,
 * land the exact bytes, and a retained run (same array instances) writes nothing.
 */
class WebGpuMeshUploadStagingTest {
    @Test
    fun meshArraysLandExactlyAndReuseOneHostBuffer() = runBlocking {
        val context = glfwContextRenderer(width = 1, height = 1, title = "awake-mesh-upload")
        val graphicsDevice = GraphicsDevice().apply { create(context.wgpuContext) }
        val device = context.wgpuContext.device
        val buffer = device.createBuffer(
            BufferDescriptor(size = CAPACITY.toULong(), usage = GPUBufferUsage.CopyDst or GPUBufferUsage.MapRead),
        )
        val staging = MeshUploadStaging()
        try {
            device.queue.writeMeshFloats(buffer, FloatArray(CAPACITY / Float.SIZE_BYTES) { 9f }, staging)
            val host = staging.hostBytes
            val floats = floatArrayOf(1f, -0.5f, Float.MAX_VALUE, 0f)
            device.queue.writeMeshFloats(buffer, floats, staging)
            assertSame(host, staging.hostBytes, "a smaller upload reuses the kept host buffer")
            val ints = intArrayOf(7, -3)
            device.queue.writeMeshInts(buffer, ints, staging)
            assertSame(host, staging.hostBytes, "indices reuse it too")

            val expected = FloatArray(CAPACITY / Float.SIZE_BYTES) { 9f }
            floats.copyInto(expected)
            val bytes = fastArrayBufferOf(expected).toByteArray()
            fastArrayBufferOf(ints).toByteArray().copyInto(bytes)
            device.queue.submit(emptyList())
            coroutineScope {
                val mapping = async { buffer.mapAsync(GPUMapMode.Read) }
                device.drivePendingWork { mapping.isCompleted }
                mapping.await().getOrThrow()
            }
            assertContentEquals(bytes, buffer.getMappedRange().toByteArray())
            buffer.unmap()
        } finally {
            buffer.close()
            graphicsDevice.destroy()
        }
    }

    @Test
    fun aRetainedRunWritesOnceAndAForcedRunWritesOnlyWhatChanged() = runBlocking {
        val context = glfwContextRenderer(width = 1, height = 1, title = "awake-mesh-upload-count")
        val graphicsDevice = GraphicsDevice().apply { create(context.wgpuContext) }
        val mesh = DynamicMesh(graphicsDevice, maxQuads = 4)
        try {
            val vertices = FloatArray(4 * DynamicMesh.FLOATS_PER_VERTEX) { it.toFloat() }
            val indices = intArrayOf(0, 1, 2, 2, 3, 0)
            repeat(FRAMES) { mesh.update(vertices, indices) }
            assertEquals(1, mesh.uploadCount, "the same immutable arrays upload once")
            repeat(FRAMES) { mesh.update(vertices, indices, forceUpload = true) }
            assertEquals(1, mesh.uploadCount, "pooled arrays whose contents did not change write nothing (#1545)")
            val floatsBefore = mesh.uploadedFloats
            repeat(FRAMES) { frame ->
                vertices[DynamicMesh.FLOATS_PER_VERTEX + 1] = 100f + frame
                mesh.update(vertices, indices, forceUpload = true)
            }
            assertEquals(1 + FRAMES, mesh.uploadCount, "a pooled array whose contents changed writes every time")
            assertEquals(FRAMES.toLong(), mesh.uploadedFloats - floatsBefore, "and writes only the changed float")
        } finally {
            mesh.destroy()
            graphicsDevice.destroy()
        }
    }

    private companion object {
        const val CAPACITY = 64
        const val FRAMES = 5
    }
}
