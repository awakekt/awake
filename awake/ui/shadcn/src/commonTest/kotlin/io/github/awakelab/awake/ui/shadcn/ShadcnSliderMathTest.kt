/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ui.shadcn

import io.github.awakelab.awake.ui.shadcn.components.ShadcnSliderThumb
import io.github.awakelab.awake.ui.shadcn.components.ShadcnSliderTrack
import io.github.awakelab.awake.ui.shadcn.components.shadcnNearerThumb
import io.github.awakelab.awake.ui.shadcn.components.shadcnSliderFraction
import io.github.awakelab.awake.ui.shadcn.components.shadcnSliderKeyStep
import io.github.awakelab.awake.ui.shadcn.components.shadcnSliderSnap
import io.github.awakelab.awake.ui.shadcn.components.shadcnSliderTrack
import io.github.awakelab.awake.ui.shadcn.components.shadcnSliderValueAt
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The degenerate inputs are the point of this suite: every one of them divides by something a
 * caller can legitimately make zero, and the old slider's "knob cut at start and end" bug was the
 * track inset being absent rather than any of the arithmetic being wrong.
 */
class ShadcnSliderMathTest {

    @Test
    fun theTrackIsInsetByHalfAThumbOnEachSide() {
        // The bug this rule exists for: a thumb centred at fraction 0 sits half outside the slot
        // and any clipping parent cuts it.
        val track = shadcnSliderTrack(slotX = 0f, slotWidth = 200f, thumbSize = 16f)

        assertEquals(8f, track.x, "the track did not start half a thumb in")
        assertEquals(184f, track.width, "the track did not lose a thumb's width in total")
    }

    @Test
    fun aTrackWithNoThumbIsNotInset() {
        val track = shadcnSliderTrack(slotX = 10f, slotWidth = 100f, thumbSize = 0f)

        assertEquals(10f, track.x)
        assertEquals(100f, track.width, "a progress bar lost width to a thumb it does not have")
    }

    @Test
    fun aThumbWiderThanItsSlotClampsToZeroRatherThanGoingNegative() {
        val track = shadcnSliderTrack(slotX = 0f, slotWidth = 10f, thumbSize = 40f)

        assertEquals(0f, track.width, "a negative width would invert every later comparison")
    }

    @Test
    fun pointerMappingUsesTheInsetTrackSoTheThumbLandsUnderThePointer() {
        val track = shadcnSliderTrack(slotX = 0f, slotWidth = 200f, thumbSize = 16f)

        assertEquals(0f, shadcnSliderValueAt(track.x, track, 0f, 100f), "the track start was not 0")
        assertEquals(
            100f,
            shadcnSliderValueAt(track.x + track.width, track, 0f, 100f),
            "the track end was not the maximum",
        )
        assertEquals(50f, shadcnSliderValueAt(track.x + track.width / 2f, track, 0f, 100f))
    }

    @Test
    fun aPointerOutsideTheTrackClampsRatherThanExtrapolating() {
        val track = ShadcnSliderTrack(x = 10f, width = 100f)

        assertEquals(0f, shadcnSliderValueAt(-500f, track, 0f, 100f))
        assertEquals(100f, shadcnSliderValueAt(500f, track, 0f, 100f))
    }

    @Test
    fun aZeroWidthTrackAnswersWithItsMinimumInsteadOfDividingByZero() {
        val track = ShadcnSliderTrack(x = 10f, width = 0f)

        assertEquals(5f, shadcnSliderValueAt(50f, track, 5f, 100f), "a measured-to-nothing slider threw")
    }

    @Test
    fun fractionIsZeroForAnEmptyRangeRatherThanNaN() {
        assertEquals(0f, shadcnSliderFraction(7f, 7f, 7f), "min == max produced a non-finite fraction")
    }

    @Test
    fun fractionClampsOutsideItsRange() {
        assertEquals(0f, shadcnSliderFraction(-10f, 0f, 100f))
        assertEquals(1f, shadcnSliderFraction(110f, 0f, 100f))
        assertEquals(0.25f, shadcnSliderFraction(25f, 0f, 100f))
    }

    @Test
    fun stepsCountThePositionsBetweenTheEndsTheWayComposeDoes() {
        // Compose's `Slider(steps = 3)` gives quarters, so 3 steps means 4 intervals.
        assertEquals(0f, shadcnSliderSnap(10f, 0f, 100f, steps = 3))
        assertEquals(25f, shadcnSliderSnap(30f, 0f, 100f, steps = 3))
        assertEquals(100f, shadcnSliderSnap(90f, 0f, 100f, steps = 3))
    }

    @Test
    fun zeroStepsIsContinuousAndLeavesTheValueAlone() {
        assertEquals(37.5f, shadcnSliderSnap(37.5f, 0f, 100f, steps = 0))
        assertEquals(37.5f, shadcnSliderSnap(37.5f, 0f, 100f, steps = -1))
    }

    @Test
    fun aKeyStepIsOnePercentOfTheRangeWithoutSteps() {
        assertEquals(1f, shadcnSliderKeyStep(0f, 100f, steps = 0))
    }

    @Test
    fun aKeyStepIsOneSnapIntervalWithSteps() {
        // 3 steps means 4 intervals, matching shadcnSliderSnap.
        assertEquals(25f, shadcnSliderKeyStep(0f, 100f, steps = 3))
    }

    @Test
    fun aKeyStepOnACollapsedRangeIsZero() {
        assertEquals(0f, shadcnSliderKeyStep(7f, 7f, steps = 0))
    }

    @Test
    fun theNearerThumbWins() {
        assertEquals(ShadcnSliderThumb.Start, shadcnNearerThumb(pointerX = 20f, startX = 10f, endX = 90f))
        assertEquals(ShadcnSliderThumb.End, shadcnNearerThumb(pointerX = 80f, startX = 10f, endX = 90f))
    }

    @Test
    fun anExactTieMovesTheStartThumb() {
        // Both thumbs stacked at one point is reachable -- drag one onto the other. Something has to
        // win deterministically, or a tie picks by float noise and the range jitters.
        assertEquals(ShadcnSliderThumb.Start, shadcnNearerThumb(pointerX = 50f, startX = 40f, endX = 60f))
        assertEquals(ShadcnSliderThumb.Start, shadcnNearerThumb(pointerX = 50f, startX = 50f, endX = 50f))
    }
}
