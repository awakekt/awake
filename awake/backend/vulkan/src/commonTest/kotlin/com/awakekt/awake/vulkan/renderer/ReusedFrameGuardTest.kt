/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The first batch prepared after a reused frame waits for every slot; one prepared each frame never does (#1555). */
class ReusedFrameGuardTest {

    @Test
    fun framesPreparedEveryTimeNeverWaitForOtherSlots() {
        val guard = ReusedFrameGuard()
        repeat(5) {
            assertFalse(guard.preparing(), "frame $it was prepared, as was every one before it")
            guard.frameSubmitted()
        }
    }

    @Test
    fun theBatchAfterAReusedFrameWaitsForEverySlotOnce() {
        val guard = ReusedFrameGuard()
        guard.preparing()
        guard.frameSubmitted()
        // Two frames handed back unprepared.
        guard.frameSubmitted()
        guard.frameSubmitted()

        assertTrue(guard.preparing(), "a reused frame may still read the slot about to be written")
        guard.frameSubmitted()
        assertFalse(guard.preparing(), "and the frame after that was prepared")
    }

    @Test
    fun aSecondBatchInTheSameFrameDoesNotWait() {
        val guard = ReusedFrameGuard()
        guard.preparing()
        guard.frameSubmitted()
        guard.frameSubmitted()
        assertTrue(guard.preparing())
        assertFalse(guard.preparing(), "nothing was submitted between the two batches")
    }
}
