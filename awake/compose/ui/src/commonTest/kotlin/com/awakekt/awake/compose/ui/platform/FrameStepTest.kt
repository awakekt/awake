/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

import kotlin.test.Test
import kotlin.test.assertEquals

/** A [FrameStep] hands each frame's delta out once, however many passes the frame runs (#1764). */
class FrameStepTest {
    private val clock = FrameClock()
    private val step = FrameStep()

    @Test
    fun aSecondPassInOneFrameAdvancesNothing() {
        clock.advance(0.02f)
        assertEquals(0.02f, step.delta(clock), "the frame's first pass")
        assertEquals(0f, step.delta(clock), "a subcomposition or the skip check, in the same frame")
    }

    @Test
    fun theNextFrameAdvancesAgain() {
        clock.advance(0.02f)
        step.delta(clock)
        clock.advance(0.03f)
        assertEquals(0.03f, step.delta(clock))
    }

    @Test
    fun aFrameNoPassAskedIsNotOwed() {
        clock.advance(0.02f)
        step.delta(clock)
        clock.advance(0.03f)
        clock.advance(0.04f)
        assertEquals(0.04f, step.delta(clock), "a frame the content skipped is not paid back later")
    }
}
