/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ui.shadcn.components.SHADCN_RESIZE_KEY_STEP_PX
import com.awakekt.awake.ui.shadcn.components.ShadcnPanelLimits
import com.awakekt.awake.ui.shadcn.components.shadcnDragFraction
import com.awakekt.awake.ui.shadcn.components.shadcnEvenFractions
import com.awakekt.awake.ui.shadcn.components.shadcnMoveSplit
import com.awakekt.awake.ui.shadcn.components.shadcnResizeKeyDelta
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Conservation is the whole spec: whatever the limits do, the pair's sum does not move. The
 * clamping cases carry it, because that is the only place a naive implementation loses width -- and
 * losing a little per drag is invisible per frame and obvious after a minute.
 */
class ShadcnResizableMathTest {

    private val open = ShadcnPanelLimits(0f, 1f)

    private fun assertConserved(before: Float, after: Float, split: com.awakekt.awake.ui.shadcn.components.ShadcnPanelSplit) {
        assertEquals(before + after, split.before + split.after, 1e-5f, "the pair's total drifted")
    }

    @Test
    fun anUnclampedDragMovesTheSplitByExactlyTheDelta() {
        val split = shadcnMoveSplit(0.5f, 0.5f, rawDelta = 0.1f, open, open)

        assertEquals(0.6f, split.before, 1e-5f)
        assertEquals(0.4f, split.after, 1e-5f)
        assertConserved(0.5f, 0.5f, split)
    }

    @Test
    fun draggingBackwardsIsTheSameRuleMirrored() {
        val split = shadcnMoveSplit(0.5f, 0.5f, rawDelta = -0.2f, open, open)

        assertEquals(0.3f, split.before, 1e-5f)
        assertEquals(0.7f, split.after, 1e-5f)
        assertConserved(0.5f, 0.5f, split)
    }

    @Test
    fun theGrowingPanelStopsAtItsOwnMaximum() {
        val split = shadcnMoveSplit(0.5f, 0.5f, rawDelta = 0.4f, ShadcnPanelLimits(0f, 0.6f), open)

        assertEquals(0.6f, split.before, 1e-5f, "the panel grew past its maximum")
        assertConserved(0.5f, 0.5f, split)
    }

    @Test
    fun theShrinkingPanelStopsAtItsOwnMinimumAndTheOtherStopsWithIt() {
        // The case a naive per-side clamp gets wrong: `after` stops at 0.45 but `before` keeps its
        // full 0.2, and the group silently gains 0.15.
        val split = shadcnMoveSplit(0.5f, 0.5f, rawDelta = 0.2f, open, ShadcnPanelLimits(0.45f, 1f))

        assertEquals(0.45f, split.after, 1e-5f, "the panel shrank past its minimum")
        assertEquals(0.55f, split.before, 1e-5f, "the growing panel did not stop with it")
        assertConserved(0.5f, 0.5f, split)
    }

    @Test
    fun bothPanelsClampingStillConserves() {
        // Both limits bite at once. `after` clamps harder, so it sets the movement.
        val split = shadcnMoveSplit(
            beforeFraction = 0.5f,
            afterFraction = 0.5f,
            rawDelta = 0.3f,
            beforeLimits = ShadcnPanelLimits(0f, 0.65f),
            afterLimits = ShadcnPanelLimits(0.45f, 1f),
        )

        assertEquals(0.45f, split.after, 1e-5f)
        assertEquals(0.55f, split.before, 1e-5f)
        assertConserved(0.5f, 0.5f, split)
    }

    @Test
    fun theReDerivedMoveNeverPushesTheGrowingPanelPastItsOwnLimit() {
        // Why one re-derivation is enough rather than needing a second pass: `after`'s clamp only
        // ever shrinks the movement, and `before` was already feasible at the larger one.
        val limits = ShadcnPanelLimits(0.2f, 0.6f)
        val split = shadcnMoveSplit(0.5f, 0.5f, rawDelta = 0.5f, limits, ShadcnPanelLimits(0.48f, 1f))

        assertTrue(split.before <= limits.max + 1e-5f, "the re-derived move overshot before's maximum")
        assertTrue(split.before >= limits.min - 1e-5f, "the re-derived move undershot before's minimum")
        assertConserved(0.5f, 0.5f, split)
    }

    @Test
    fun aDragAgainstAPanelAlreadyAtItsLimitChangesNothing() {
        val split = shadcnMoveSplit(0.6f, 0.4f, rawDelta = 0.2f, ShadcnPanelLimits(0f, 0.6f), open)

        assertEquals(0.6f, split.before, 1e-5f)
        assertEquals(0.4f, split.after, 1e-5f, "a no-op drag still moved something")
    }

    @Test
    fun aCollapsedGroupYieldsAZeroFractionRatherThanDividingByIt() {
        // The frame a group measures to nothing still delivers pointer events. A division here
        // writes a non-finite fraction that no later drag can recover from.
        assertEquals(0f, shadcnDragFraction(50f, 0f))
        assertEquals(0f, shadcnDragFraction(50f, -10f))
    }

    @Test
    fun aDragFractionIsPixelsOverTheAxis() {
        assertEquals(0.25f, shadcnDragFraction(100f, 400f), 1e-5f)
    }

    @Test
    fun evenFractionsSumToOne() {
        listOf(1, 2, 3, 7).forEach { n ->
            assertEquals(1f, shadcnEvenFractions(n).sum(), 1e-5f, "$n panels did not sum to the whole")
        }
    }

    @Test
    fun aGroupWithNoPanelsIsEmptyRatherThanAnError() {
        assertEquals(emptyList(), shadcnEvenFractions(0))
        assertEquals(emptyList(), shadcnEvenFractions(-3))
    }

    @Test
    fun arrowKeysNudgeAlongTheGroupsOwnAxis() {
        assertEquals(SHADCN_RESIZE_KEY_STEP_PX, shadcnResizeKeyDelta(Key.ArrowRight, horizontal = true))
        assertEquals(-SHADCN_RESIZE_KEY_STEP_PX, shadcnResizeKeyDelta(Key.ArrowLeft, horizontal = true))
        assertEquals(SHADCN_RESIZE_KEY_STEP_PX, shadcnResizeKeyDelta(Key.ArrowDown, horizontal = false))
        assertEquals(-SHADCN_RESIZE_KEY_STEP_PX, shadcnResizeKeyDelta(Key.ArrowUp, horizontal = false))
    }

    @Test
    fun theCrossAxisArrowsAreNotClaimed() {
        // A vertical group must leave left/right alone, or it eats the arrow keys of whatever is
        // focused inside it.
        assertNull(shadcnResizeKeyDelta(Key.ArrowRight, horizontal = false))
        assertNull(shadcnResizeKeyDelta(Key.ArrowUp, horizontal = true))
    }

    @Test
    fun homeAndEndSaturateThroughTheSameClampRatherThanASecondCodePath() {
        val end = shadcnResizeKeyDelta(Key.End, horizontal = true)!!
        val limits = ShadcnPanelLimits(0.2f, 0.8f)
        val split = shadcnMoveSplit(0.5f, 0.5f, shadcnDragFraction(end, 400f), limits, open)

        assertEquals(0.8f, split.before, 1e-5f, "End did not reach the maximum")
        assertConserved(0.5f, 0.5f, split)
    }

    @Test
    fun anUnrelatedKeyIsLeftUnconsumed() {
        assertNull(shadcnResizeKeyDelta(Key.Space, horizontal = true))
        assertNull(shadcnResizeKeyDelta(Key.Enter, horizontal = true))
    }
}
