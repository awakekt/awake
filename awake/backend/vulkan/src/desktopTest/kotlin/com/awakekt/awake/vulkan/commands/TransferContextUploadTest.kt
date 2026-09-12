/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.commands

import com.awakekt.awake.render.command.GpuUploadLease
import com.awakekt.awake.render.command.GpuUploadLeaseState
import com.awakekt.awake.vulkan.device.GraphicsDevice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TransferContextUploadTest {

    @Test
    fun runUploadTransitionsLeaseToCompletedAndReleasesPayload() {
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.createHeadless()
        val transferContext = TransferContext(graphicsDevice)
        try {
            var released = false
            val lease = GpuUploadLease("bytes", byteCount = 5) { released = true }

            var blockInvoked = false
            transferContext.runUpload(lease) { _, payload ->
                blockInvoked = true
                assertEquals("bytes", payload)
                assertEquals(GpuUploadLeaseState.Submitted, lease.state)
            }

            assertTrue(blockInvoked)
            assertEquals(GpuUploadLeaseState.Completed, lease.state)
            assertTrue(released)
        } finally {
            transferContext.destroy()
            graphicsDevice.destroy()
        }
    }

    @Test
    fun runUploadTransitionsLeaseToFailedAndReleasesPayloadOnDeviceError() {
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.createHeadless()
        val transferContext = TransferContext(graphicsDevice)
        try {
            var released = false
            val lease = GpuUploadLease("device-loss-payload", byteCount = 19) { released = true }

            assertFailsWith<RuntimeException> {
                transferContext.runUpload(lease) { _, _ ->
                    throw RuntimeException("Simulated device loss during transfer submission")
                }
            }

            assertEquals(GpuUploadLeaseState.Failed, lease.state)
            assertTrue(released)
        } finally {
            transferContext.destroy()
            graphicsDevice.destroy()
        }
    }
}
