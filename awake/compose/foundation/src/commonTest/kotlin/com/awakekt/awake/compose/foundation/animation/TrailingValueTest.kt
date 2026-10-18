/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.animation

import kotlin.test.Test
import kotlin.test.assertEquals

class TrailingValueTest {

    @Test
    fun itHoldsThenDrainsAndNeverPassesTheValue() {
        val trail = TrailingValue(1f)

        assertEquals(1f, trail.advance(0.5f, 0.3f, holdSeconds = 0.5f, drainPerSecond = 1f), "held")
        assertEquals(0.9f, trail.advance(0.5f, 0.3f, holdSeconds = 0.5f, drainPerSecond = 1f), 1e-5f, "only the 0.1 s past the hold drains")
        assertEquals(0.5f, trail.advance(0.5f, 2f, holdSeconds = 0.5f, drainPerSecond = 1f), "it stops at the value")
    }

    @Test
    fun aRiseIsFollowedAtOnceAndANewDropHoldsAgain() {
        val trail = TrailingValue(0.2f)

        assertEquals(0.8f, trail.advance(0.8f, 0.1f))
        trail.advance(0.6f, 0.6f, holdSeconds = 0.5f, drainPerSecond = 1f)
        val draining = trail.value
        assertEquals(draining, trail.advance(0.3f, 0.1f, holdSeconds = 0.5f, drainPerSecond = 1f), "another drop holds where it is")
    }

    @Test
    fun snapToPutsItOnTheValue() {
        val trail = TrailingValue(1f)
        trail.snapTo(0.25f)

        assertEquals(0.25f, trail.value)
        assertEquals(0.25f, trail.advance(0.25f, 1f))
    }
}
