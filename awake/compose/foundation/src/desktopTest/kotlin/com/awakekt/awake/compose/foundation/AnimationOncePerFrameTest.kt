/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.animation.animateFloat
import com.awakekt.awake.compose.foundation.animation.animateTrailing
import com.awakekt.awake.compose.foundation.animation.rememberLoopingPhase
import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * An animation advances once a frame, however many times the frame composes it (#1764).
 *
 * A production frame composes twice when placement writes state that composition read: the host
 * composes and lays out again in the same frame, so nothing is drawn a frame late. An animation that
 * added the frame's delta on each pass ran twice as fast on such frames, with no verifier involved.
 */
class AnimationOncePerFrameTest {
    private val placedFrame = mutableStateOf(0)
    private var frame = 0
    private var compositions = 0
    private var phase = 0f
    private var eased = 0f
    private var trailing = 0f

    @Test
    fun aFrameThatComposesTwiceAdvancesEachAnimationOnce() {
        val host = ComposeHost()
        repeat(FRAMES) {
            frame = it + 1
            host.frame(FrameInput(200, 100, deltaSeconds = DELTA)) {
                compositions++
                // Read in composition, written by placement below: the host composes again this frame.
                placedFrame.value
                phase = rememberLoopingPhase(PERIOD)
                eased = animateFloat(if (frame > 1) 1f else 0f, durationSeconds = EASE)
                trailing = animateTrailing(if (frame > 1) 0f else 1f, holdSeconds = 0f, drainPerSecond = DRAIN)
                Box(Modifier.size(10.dp).onPlaced { _, _, _, _ -> placedFrame.value = frame })
            }
        }
        assertTrue(compositions >= 2 * FRAMES - 1, "each frame composed twice: $compositions compositions in $FRAMES frames")
        assertTrue(host.placementRecompositions >= FRAMES - 1, "placement recomposed ${host.placementRecompositions} frames")

        // The clock's first frame advances too: FRAMES deltas in all, each counted once.
        assertEquals(FRAMES * DELTA / PERIOD, phase, TOLERANCE, "the looping phase")
        assertEquals((FRAMES - 1) * DELTA / EASE, eased, TOLERANCE, "the eased value, since its target changed")
        assertEquals(1f - (FRAMES - 1) * DELTA * DRAIN, trailing, TOLERANCE, "the trailing value's drain")
    }

    private companion object {
        const val FRAMES = 10
        const val DELTA = 0.01f
        const val PERIOD = 2.4f
        const val EASE = 0.5f
        const val DRAIN = 1.5f
        const val TOLERANCE = 1e-4f
    }
}
