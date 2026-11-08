/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlin.test.Test
import kotlin.test.assertEquals

/** [CanvasScaleMode.DensityStep]: the largest pixel step that keeps the short side at least its reference. */
class CanvasDensityStepTest {
    private val desktop = CanvasScaler(mode = CanvasScaleMode.DensityStep, referenceHeight = 720f)
    private val touch = desktop.copy(referenceHeight = 360f)

    @Test
    fun picksTheLargestWholeStepThatKeepsTheShortSide() {
        // 1920 x 1080 at density 1: 1.5 pixels per unit would fit, but the step is whole pixels.
        assertEquals(1f, desktop.scaleFor(1920f, 1080f, density = 1f), TOLERANCE)
        // 3840 x 2160 pixels at density 2: three pixels per unit leaves 720 units, scale 3 / 2.
        assertEquals(1.5f, desktop.scaleFor(1920f, 1080f, density = 2f), TOLERANCE)
    }

    @Test
    fun theShortSideIsTheHeightInLandscapeAndTheWidthInPortrait() {
        val landscape = touch.scaleFor(2400f / 2.75f, 1080f / 2.75f, density = 2.75f)
        val portrait = touch.scaleFor(1080f / 2.75f, 2400f / 2.75f, density = 2.75f)

        // 1080 pixels over 360 units is three pixels per unit, whichever way the phone is held.
        assertEquals(3f / 2.75f, landscape, TOLERANCE)
        assertEquals(landscape, portrait, TOLERANCE)
    }

    @Test
    fun theStepIsPixelsPerUnitSoHalfStepsAreAvailable() {
        val halves = desktop.copy(pixelsPerUnit = 0.5f)

        assertEquals(1.5f, halves.scaleFor(1920f, 1080f, density = 1f), TOLERANCE)
        // 1000 pixels / 720 units is 1.39 pixels per unit: down to 1, not up to 1.5.
        assertEquals(1f, halves.scaleFor(1600f, 1000f, density = 1f), TOLERANCE)
    }

    @Test
    fun aScreenExactlyOnAStepTakesThatStep() {
        assertEquals(2f, desktop.scaleFor(2560f, 1440f, density = 1f), TOLERANCE)
    }

    @Test
    fun aScreenSmallerThanTheReferenceGetsOneStep() {
        assertEquals(1f, desktop.scaleFor(800f, 480f, density = 1f), TOLERANCE)
        assertEquals(0.5f, desktop.scaleFor(400f, 240f, density = 2f), TOLERANCE)
    }

    @Test
    fun snapIsIgnoredButTheLimitsAndThePlayerStillApply() {
        val snapped = desktop.copy(snap = CanvasScaleSnap.WholeStep)
        assertEquals(1.5f, snapped.scaleFor(1920f, 1080f, density = 2f), TOLERANCE)

        val limited = desktop.copy(maxScale = 1.25f, playerScale = 2f)
        assertEquals(2.5f, limited.scaleFor(1920f, 1080f, density = 2f), TOLERANCE)
    }

    @Test
    fun anUnsizedBoxGivesOneStep() {
        assertEquals(0.5f, desktop.scaleFor(0f, 0f, density = 2f), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
