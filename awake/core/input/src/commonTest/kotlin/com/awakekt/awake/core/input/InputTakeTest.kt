/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputTakeTest {

    @Test
    fun recordsSparselyAndReplaysExactPressHoldReleaseEdges() {
        val input = Input()
        val recorder = InputRecorder(scenePath = "scenes/harbor.awakescene", seed = 42L)

        // Frame 0: idle
        recorder.recordFrame(input.updateSnapshot(), gameplayCameraYaw = 0.25f, gameplayCameraPitch = -0.3f)

        // Frame 1..3: hold W + primary pointer
        input.setKeyDown(Key.W, true)
        input.setPointer(down = true, x = 320f, y = 180f)
        recorder.recordFrame(input.updateSnapshot(), gameplayCameraYaw = 0.25f, gameplayCameraPitch = -0.3f)
        recorder.recordFrame(input.updateSnapshot(), gameplayCameraYaw = 0.25f, gameplayCameraPitch = -0.3f)
        recorder.recordFrame(input.updateSnapshot(), gameplayCameraYaw = 0.50f, gameplayCameraPitch = -0.3f)

        // Frame 4: release W, scroll wheel once
        input.setKeyDown(Key.W, false)
        input.scrollDeltaY = 2f
        recorder.recordFrame(input.updateSnapshot(), gameplayCameraYaw = 0.50f, gameplayCameraPitch = -0.3f)

        // Frame 5: still holding pointer, scroll cleared
        recorder.recordFrame(input.updateSnapshot(), gameplayCameraYaw = 0.50f, gameplayCameraPitch = -0.3f)

        val take = recorder.finish()
        assertEquals(6, take.totalFrames)
        assertTrue(take.frames.size < take.totalFrames, "Identical frame 2 should be compressed out")

        val replayer = InputReplayer(take)

        // Frame 0
        val f0 = replayer.stepFrame()
        assertFalse(f0.isDown(Key.W))
        assertEquals(0.25f, replayer.currentCameraYaw)

        // Frame 1: W pressed edge + pointer pressed edge
        val f1 = replayer.stepFrame()
        assertTrue(f1.isDown(Key.W))
        assertTrue(f1.wasPressed(Key.W))
        assertTrue(f1.pointerDown)
        assertTrue(f1.pointerPressed)

        // Frame 2: W still held, not newly pressed
        val f2 = replayer.stepFrame()
        assertTrue(f2.isDown(Key.W))
        assertFalse(f2.wasPressed(Key.W))
        assertFalse(f2.pointerPressed)

        // Frame 3: yaw updated to 0.50
        replayer.stepFrame()
        assertEquals(0.50f, replayer.currentCameraYaw)

        // Frame 4: W released edge + scroll pulse
        val f4 = replayer.stepFrame()
        assertFalse(f4.isDown(Key.W))
        assertTrue(Key.W in f4.keysReleased)
        assertEquals(2f, f4.scrollDeltaY)

        // Frame 5: scroll cleared back to 0
        val f5 = replayer.stepFrame()
        assertEquals(0f, f5.scrollDeltaY)
        assertTrue(replayer.isFinished)

        // Frame 6 (past end): releases held pointer cleanly
        val f6 = replayer.stepFrame()
        assertFalse(f6.pointerDown)
        assertTrue(f6.pointerReleased)
    }
}
