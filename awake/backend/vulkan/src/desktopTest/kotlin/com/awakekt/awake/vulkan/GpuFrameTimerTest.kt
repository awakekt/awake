/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan

import com.awakekt.awake.vulkan.commands.TransferContext
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.renderer.GpuFrameTimer
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Compares a frame's GPU total to its independently read offscreen queries. */
class GpuFrameTimerTest {
    @Test
    fun aFrameIncludesAllCompletedOffscreenMeasurements() {
        val graphics = GraphicsDevice().apply { createHeadless() }
        val transfer = TransferContext(graphics)
        val timer = GpuFrameTimer.createOrNull(graphics, 1)
        if (timer == null) {
            transfer.destroy()
            graphics.destroy()
            return
        }
        try {
            var offscreenMs = 0.0
            repeat(50) {
                var ticket = -1L
                transfer.runOneTimeCommands { commandBuffer ->
                    ticket = timer.beginOffscreen(commandBuffer)
                    timer.endOffscreen(commandBuffer, ticket)
                }
                offscreenMs += assertNotNull(timer.completeOffscreen(ticket))
            }
            transfer.runOneTimeCommands { commandBuffer ->
                timer.begin(commandBuffer, 0)
                timer.end(commandBuffer, 0)
            }
            transfer.runOneTimeCommands { commandBuffer ->
                timer.begin(commandBuffer, 0)
                val measured = assertNotNull(timer.lastMs)
                println("Measured Vulkan frame: $measured ms; offscreen submissions: $offscreenMs ms")
                assertTrue(offscreenMs > 0.0)
                assertTrue(measured.toDouble() + 0.000001 >= offscreenMs, "the frame must include every offscreen submission")
                timer.end(commandBuffer, 0)
            }
        } finally {
            timer.destroy()
            transfer.destroy()
            graphics.destroy()
        }
    }
}
