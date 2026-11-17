/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

/**
 * Hands out each frame's [FrameClock.deltaSeconds] once, to state that advances while composing.
 *
 * A frame can compose the same content more than once: a subcomposition such as
 * `BoxWithConstraints` runs again during measure, and `ComposeHost.verifySkippedCompositions`
 * composes a skipped frame a second time to compare. An animation that added the delta on every pass
 * moved further in such a frame than in real time, and the second pass disagreed with the first, so
 * the check reported a stale skip that was not there (#1764). Keep one per animation, beside its
 * state, and advance it by [delta]: the first pass of a frame gets the frame's delta, any later pass
 * in the same frame gets 0.
 */
class FrameStep {
    /** The frame that last got its delta; none yet, since the clock counts frames from 1. */
    private var lastFrame = -1L

    /**
     * This frame's delta from [clock] the first time it is asked in a frame, 0 after that. The clock is
     * read every time, so a host that skips unchanged passes still sees the read.
     */
    fun delta(clock: FrameClock): Float {
        val delta = clock.deltaSeconds
        if (clock.frame == lastFrame) return 0f
        lastFrame = clock.frame
        return delta
    }
}
