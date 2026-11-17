/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.test.Test
import kotlin.test.assertEquals

/** [CanvasSubpixelTracker] eases once per frame, however many times a frame draws (#1512). */
class CanvasSubpixelTrackerTest {
    /** The positions of an element gliding by a tenth of a pixel a frame, then holding still to settle. */
    private val path: List<Float> = List(12) { 10f + it * 0.1f } + List(10) { 11.1f }

    @Test
    fun aSecondDrawInAFrameShowsWhatTheFirstDid() {
        val once = CanvasSubpixelTracker()
        val twice = CanvasSubpixelTracker()
        path.forEachIndexed { index, px ->
            val frame = index + 1L
            once.step(frame, px, px, px.toInt(), px.toInt())
            twice.step(frame, px, px, px.toInt(), px.toInt())
            twice.step(frame, px, px, px.toInt(), px.toInt())
            assertEquals(once.residualX, twice.residualX, "frame $frame")
            assertEquals(once.residualY, twice.residualY, "frame $frame")
        }
    }

    @Test
    fun aSecondDrawAtANewPlaceStepsFromTheLastFrame() {
        val direct = CanvasSubpixelTracker()
        val redrawn = CanvasSubpixelTracker()
        path.forEachIndexed { index, px ->
            val frame = index + 1L
            direct.step(frame, px, px, px.toInt(), px.toInt())
            // A first pass placed it elsewhere, and a second pass in the same frame placed it here.
            redrawn.step(frame, px + 0.37f, px, (px + 0.37f).toInt(), px.toInt())
            redrawn.step(frame, px, px, px.toInt(), px.toInt())
            assertEquals(direct.residualX, redrawn.residualX, "frame $frame")
        }
    }

    @Test
    fun aClockNoHostAdvancesStepsOnEveryDraw() {
        val unclocked = CanvasSubpixelTracker()
        val clocked = CanvasSubpixelTracker()
        path.forEachIndexed { index, px ->
            unclocked.step(0L, px, px, px.toInt(), px.toInt())
            clocked.step(index + 1L, px, px, px.toInt(), px.toInt())
            assertEquals(clocked.residualX, unclocked.residualX, "draw $index")
        }
    }
}
