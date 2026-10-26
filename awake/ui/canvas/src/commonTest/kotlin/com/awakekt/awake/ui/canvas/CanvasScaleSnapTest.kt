/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import kotlinx.serialization.json.Json
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [CanvasScaler.snap] rounds the rule's scale to a step, before the limits and the player's multiplier. */
class CanvasScaleSnapTest {

    /** A 1017 dp tall window against the default 720 reference gives 1.4125, matching by height. */
    private val byHeight = CanvasScaler(match = 1f)

    @Test
    fun noSnapDrawsAtTheScaleTheRuleGives() {
        assertEquals(1.4125f, byHeight.scaleFor(1808f, 1017f, density = 1f), TOLERANCE)
        assertEquals(CanvasScaleSnap.None, CanvasScaler().snap)
    }

    @Test
    fun aHalfStepRoundsToTheNearestHalf() {
        val half = byHeight.copy(snap = CanvasScaleSnap.HalfStep)

        assertEquals(1.5f, half.scaleFor(1808f, 1017f, density = 1f), TOLERANCE)
        assertEquals(1.5f, half.scaleFor(1280f, 1050f, density = 1f), TOLERANCE, "1.4583")
        assertEquals(1f, half.scaleFor(1280f, 800f, density = 1f), TOLERANCE, "1.1111")
        assertEquals(2f, half.scaleFor(1920f, 1440f, density = 1f), TOLERANCE, "2.0 is a step already")
    }

    @Test
    fun aWholeStepRoundsToTheNearestWholeNumber() {
        val whole = byHeight.copy(snap = CanvasScaleSnap.WholeStep)

        assertEquals(1f, whole.scaleFor(1808f, 1017f, density = 1f), TOLERANCE)
        assertEquals(2f, whole.scaleFor(1280f, 1100f, density = 1f), TOLERANCE, "1.5278")
        assertEquals(3f, whole.scaleFor(1280f, 2000f, density = 1f), TOLERANCE, "2.7778")
    }

    @Test
    fun aScaleUnderOneStepIsLeftAsItIs() {
        val half = byHeight.copy(snap = CanvasScaleSnap.HalfStep)
        val whole = byHeight.copy(snap = CanvasScaleSnap.WholeStep)

        // Rounded, a canvas shrunk to 0.4 would become 0, or grow to 0.5 or 1, past the box it fits.
        assertEquals(0.4f, half.scaleFor(1280f, 288f, density = 1f), TOLERANCE)
        assertEquals(0.4f, whole.scaleFor(1280f, 288f, density = 1f), TOLERANCE)
        assertEquals(0.75f, whole.scaleFor(1280f, 540f, density = 1f), TOLERANCE)
    }

    @Test
    fun theLimitsHoldTheSnappedScaleAndThePlayerMultipliesItAfterThem() {
        val half = byHeight.copy(snap = CanvasScaleSnap.HalfStep)

        assertEquals(1.25f, half.copy(maxScale = 1.25f).scaleFor(1808f, 1017f, density = 1f), TOLERANCE, "1.5 held to the limit")
        assertEquals(1.5f, half.copy(minScale = 1.25f).scaleFor(1808f, 1017f, density = 1f), TOLERANCE)
        assertEquals(1.65f, half.copy(playerScale = 1.1f).scaleFor(1808f, 1017f, density = 1f), TOLERANCE, "the player's 10% is not snapped away")
    }

    @Test
    fun everyModeCanBeSnapped() {
        val pixels = CanvasScaler(mode = CanvasScaleMode.ConstantPixelSize, pixelsPerUnit = 3f, snap = CanvasScaleSnap.WholeStep)
        val physical = CanvasScaler(mode = CanvasScaleMode.ConstantPhysicalSize, snap = CanvasScaleSnap.HalfStep)

        // 3 pixels per unit at 2 per dp is 1.5 dp: whole is 2.
        assertEquals(2f, pixels.scaleFor(800f, 600f, density = 2f), TOLERANCE)
        // 400 dpi at 2 pixels per dp is 1.25 of a dp, which rounds up to 1.5.
        assertEquals(1.5f, physical.scaleFor(800f, 600f, density = 2f, dpi = 400f), TOLERANCE)
    }

    @Test
    fun noStepMovesALayoutByMoreThanHalfAStepInProportion() {
        for (snap in listOf(CanvasScaleSnap.HalfStep, CanvasScaleSnap.WholeStep)) {
            val snapped = byHeight.copy(snap = snap)
            var height = 720f
            while (height <= 4000f) {
                val exact = byHeight.scaleFor(1280f, height, density = 1f)
                val ratio = snapped.scaleFor(1280f, height, density = 1f) / exact

                assertTrue(abs(ratio - 1f) <= snap.step / 2f / exact + TOLERANCE, "$snap at height $height: layout ratio $ratio")
                height += 37f
            }
        }
    }

    @Test
    fun aSnapIsSavedAsJsonWithItsDefaultLeftOut() {
        val json = Json { encodeDefaults = false }

        assertEquals("{}", json.encodeToString(CanvasScaler.serializer(), CanvasScaler()))
        val text = json.encodeToString(CanvasScaler.serializer(), CanvasScaler(snap = CanvasScaleSnap.HalfStep))

        assertEquals("""{"snap":"HalfStep"}""", text)
        assertEquals(CanvasScaleSnap.HalfStep, json.decodeFromString(CanvasScaler.serializer(), text).snap)
        assertEquals(CanvasScaleSnap.None, json.decodeFromString(CanvasScaler.serializer(), "{}").snap)
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}
