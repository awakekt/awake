/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.scene.rendering.animation.AnimationUpdateRate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AnimationUpdateRateTest {
    @Test
    fun everyFrameNeverHolds() {
        assertEquals(0f, AnimationUpdateRate.EveryFrame.intervalFor(0f))
        assertEquals(0f, AnimationUpdateRate.EveryFrame.intervalFor(1_000f))
    }

    @Test
    fun byDistancePosesEveryFrameWithinNearAndThrottlesBeyondFar() {
        val rate = AnimationUpdateRate.byDistance(near = 10f, far = 30f, farInterval = 0.5f)

        assertEquals(0f, rate.intervalFor(5f), "within near: every frame")
        assertEquals(0f, rate.intervalFor(10f), "at near: still every frame")
        assertEquals(0.25f, rate.intervalFor(20f), 1e-5f, "midway: half the far interval")
        assertEquals(0.5f, rate.intervalFor(30f), 1e-5f, "at far: the full interval")
        assertEquals(0.5f, rate.intervalFor(100f), 1e-5f, "beyond far: the full interval")
    }

    @Test
    fun byDistanceWithAZeroBandSnapsAtNear() {
        val rate = AnimationUpdateRate.byDistance(near = 10f, far = 10f, farInterval = 0.5f)

        assertEquals(0f, rate.intervalFor(10f))
        assertEquals(0.5f, rate.intervalFor(10.001f), 1e-5f)
    }

    @Test
    fun byDistanceRejectsAnInvalidBand() {
        assertFailsWith<IllegalArgumentException> { AnimationUpdateRate.byDistance(near = -1f, far = 10f, farInterval = 1f) }
        assertFailsWith<IllegalArgumentException> { AnimationUpdateRate.byDistance(near = 10f, far = 5f, farInterval = 1f) }
        assertFailsWith<IllegalArgumentException> { AnimationUpdateRate.byDistance(near = 0f, far = 10f, farInterval = -1f) }
    }
}
