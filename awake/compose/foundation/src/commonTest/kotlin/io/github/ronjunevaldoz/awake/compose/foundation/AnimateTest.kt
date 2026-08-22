// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.animation.animateFloat
import io.github.ronjunevaldoz.awake.compose.foundation.animation.rememberLoopingPhase
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Spacer
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.current
import io.github.ronjunevaldoz.awake.compose.ui.platform.LocalFrameClock
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.platform.ComposeHost
import io.github.ronjunevaldoz.awake.compose.ui.platform.FrameClock
import io.github.ronjunevaldoz.awake.compose.ui.platform.FrameInput
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val VIEWPORT = 100

/** Runs [content] for [frames] frames of [delta] seconds each, collecting what it returned. */
private fun drive(
    frames: Int,
    delta: Float = 1f / 60f,
    content: context(Composer) (collect: (Float) -> Unit) -> Unit,
): List<Float> {
    val host = ComposeHost()
    val seen = mutableListOf<Float>()
    repeat(frames) {
        host.frame(
            FrameInput(viewportWidth = VIEWPORT, viewportHeight = VIEWPORT, deltaSeconds = delta),
        ) {
            content { seen += it }
            Spacer(Modifier.size(1.dp))
        }
    }
    return seen
}

class FrameClockTest {

    @Test
    fun timeAccumulatesAcrossFrames() {
        val host = ComposeHost()
        repeat(3) {
            host.frame(FrameInput(VIEWPORT, VIEWPORT, deltaSeconds = 0.05f)) { }
        }

        assertEquals(0.05f, host.frameClock.deltaSeconds, 1e-5f)
        assertEquals(0.15f, host.frameClock.totalSeconds, 1e-5f)
    }

    @Test
    fun aStalledFrameIsClampedRatherThanFastForwarded() {
        // A breakpoint or a backgrounded tab hands the next frame a delta measured in seconds.
        // Advancing by it would complete a 200 ms transition between two visible frames.
        val host = ComposeHost()

        host.frame(FrameInput(VIEWPORT, VIEWPORT, deltaSeconds = 30f)) { }

        assertEquals(FrameClock.MAX_DELTA_SECONDS, host.frameClock.deltaSeconds)
    }

    @Test
    fun everyReaderInOnePassSeesTheSameDelta() {
        // Two animations stepping by different deltas in one frame would drift apart for no reason
        // a caller could see.
        val seen = drive(frames = 2, delta = 0.02f) { collect ->
            collect(LocalFrameClock.current.deltaSeconds)
            collect(LocalFrameClock.current.deltaSeconds)
        }

        assertEquals(1, seen.distinct().size, "readers in one pass disagreed about now")
    }
}

class LoopingPhaseTest {

    @Test
    fun itStartsAtZeroAndAdvances() {
        val seen = drive(frames = 3, delta = 0.1f) { collect ->
            collect(rememberLoopingPhase(durationSeconds = 1f))
        }

        assertEquals(listOf(0.1f, 0.2f, 0.3f), seen.map { round2(it) })
    }

    @Test
    fun itWrapsRatherThanGrowing() {
        val seen = drive(frames = 12, delta = 0.1f) { collect ->
            collect(rememberLoopingPhase(durationSeconds = 1f))
        }

        // Which index wraps is float noise -- accumulating 0.1f ten times lands just over 1, not
        // just under -- so assert that a wrap happened, not where.
        assertTrue(seen.all { it in 0f..1f }, "phase left 0..1: ${seen.filter { it !in 0f..1f }}")
        assertTrue(
            seen.zipWithNext().any { (a, b) -> b < a },
            "it never wrapped -- the phase just kept growing: $seen",
        )
    }

    @Test
    fun aPhaseSurvivesTheNextPass() {
        // The whole reason this is `remember` and not a field on a modifier node: the chain is
        // rebuilt every pass, so a node-field phase would reset to zero every frame and the
        // animation would never move. That is the bug `clickable` shipped.
        val seen = drive(frames = 5, delta = 0.1f) { collect ->
            collect(rememberLoopingPhase(durationSeconds = 1f))
        }

        assertTrue(seen.zipWithNext().all { (a, b) -> b > a }, "the phase reset between frames: $seen")
    }
}

class AnimateFloatTest {

    @Test
    fun theFirstPassReturnsTheTargetRatherThanAnimatingIn() {
        val seen = drive(frames = 1) { collect -> collect(animateFloat(target = 1f)) }

        assertEquals(1f, seen.single())
    }

    @Test
    fun itEasesTowardANewTarget() {
        var target = 0f
        val seen = drive(frames = 5, delta = 0.05f) { collect ->
            collect(animateFloat(target, durationSeconds = 0.2f))
            target = 1f
        }

        // Frame 1 pins 0; the target changes after, so frames 2..5 cover 0.05s each of a 0.2s tween.
        assertEquals(listOf(0f, 0.25f, 0.5f, 0.75f, 1f), seen.map { round2(it) })
    }

    @Test
    fun itStopsAtTheTarget() {
        var target = 0f
        val seen = drive(frames = 20, delta = 0.05f) { collect ->
            collect(animateFloat(target, durationSeconds = 0.2f))
            target = 1f
        }

        assertEquals(1f, seen.last(), "it overshot or kept going")
    }

    @Test
    fun anInterruptedTweenRestartsFromWhereItIsNotFromWhereItWasHeaded() {
        // A collapsible toggled twice quickly. Restarting from the old destination would snap the
        // value forward before setting off again, which reads as a stutter.
        var target = 0f
        var frame = 0
        val seen = drive(frames = 4, delta = 0.05f) { collect ->
            collect(animateFloat(target, durationSeconds = 0.2f))
            frame++
            target = if (frame >= 3) 0f else 1f
        }

        // Frame 3 is mid-flight at 0.5; reversing there must continue from 0.5, not jump to 1.
        assertEquals(0.5f, round2(seen[2]))
        assertTrue(seen[3] < seen[2], "it did not turn around: $seen")
        assertTrue(seen[3] >= 0.2f, "it snapped to the old target before reversing: $seen")
    }
}

private fun round2(value: Float): Float = kotlin.math.round(value * 100f) / 100f
