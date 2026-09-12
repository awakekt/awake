/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.commands

import com.awakekt.awake.render.command.GpuUploadLease
import com.awakekt.awake.render.command.GpuUploadLeaseState
import com.awakekt.awake.webgpu.device.GraphicsDevice
import io.ygdrasil.webgpu.glfwContextRenderer
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebGpuUploadContextTest {

    @Test
    fun runUploadTransitionsLeaseToCompletedAndReleasesPayload() = runBlocking {
        val context = glfwContextRenderer(width = 1, height = 1, title = "test-upload")
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        val uploadContext = WebGpuUploadContext(graphicsDevice)
        try {
            var released = false
            val lease = GpuUploadLease("webgpu-payload", byteCount = 14) { released = true }

            var blockInvoked = false
            uploadContext.runUpload(lease) { payload ->
                blockInvoked = true
                assertEquals("webgpu-payload", payload)
                assertEquals(GpuUploadLeaseState.Submitted, lease.state)
            }

            assertTrue(blockInvoked)
            assertEquals(GpuUploadLeaseState.Completed, lease.state)
            assertTrue(released)
        } finally {
            graphicsDevice.destroy()
        }
    }

    @Test
    fun runUploadTransitionsLeaseToFailedAndReleasesPayloadOnDeviceError() = runBlocking {
        val context = glfwContextRenderer(width = 1, height = 1, title = "test-upload-fail")
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.create(context.wgpuContext)
        val uploadContext = WebGpuUploadContext(graphicsDevice)
        try {
            var released = false
            val lease = GpuUploadLease("webgpu-fail-payload", byteCount = 19) { released = true }

            assertFailsWith<RuntimeException> {
                uploadContext.runUpload(lease) {
                    throw RuntimeException("Simulated device loss during WebGPU upload")
                }
            }

            assertEquals(GpuUploadLeaseState.Failed, lease.state)
            assertTrue(released)
        } finally {
            graphicsDevice.destroy()
        }
    }
}
