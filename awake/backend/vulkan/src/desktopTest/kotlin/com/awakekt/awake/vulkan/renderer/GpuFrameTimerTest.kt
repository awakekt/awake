/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpuFrameTimerTest {
    @Test
    fun aFrameIsReadBackTheNextTimeItsSlotRecords() {
        val graphicsDevice = GraphicsDevice()
        graphicsDevice.createHeadless()
        val transferContext = TransferContext(graphicsDevice)
        val timer = GpuFrameTimer.createOrNull(graphicsDevice, slots = 1)
        try {
            if (timer == null) {
                println("GpuFrameTimerTest: this device's graphics queue writes no timestamps; nothing to time.")
                return
            }
            // Each call submits and waits on a fence, the way a frame slot is waited before reuse.
            transferContext.runOneTimeCommands { commandBuffer ->
                timer.begin(commandBuffer, 0)
                timer.end(commandBuffer, 0)
            }
            assertNull(timer.lastMs, "nothing is read before the slot records again")

            transferContext.runOneTimeCommands { commandBuffer ->
                timer.begin(commandBuffer, 0)
                timer.end(commandBuffer, 0)
            }

            val ms = assertNotNull(timer.lastMs, "the first frame's timestamps were read back")
            assertTrue(ms >= 0f && ms < ONE_SECOND_MS, "an empty frame took $ms ms on the GPU")
        } finally {
            timer?.destroy()
            transferContext.destroy()
            graphicsDevice.destroy()
        }
    }

    private companion object {
        const val ONE_SECOND_MS = 1_000f
    }
}
