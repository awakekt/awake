/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.LocalDensity
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [CanvasScaler.scaleFor] works a canvas's scale out from its box, and [CanvasOverlay] draws at it. */
class CanvasScalerTest {

    @Test
    fun scaleWithScreenSizeFollowsTheWidthTheHeightOrABlend() {
        val byWidth = CanvasScaler(referenceWidth = 1000f, referenceHeight = 500f, match = 0f)
        val byHeight = byWidth.copy(match = 1f)
        val halfway = byWidth.copy(match = 0.5f)

        assertEquals(2f, byWidth.scaleFor(2000f, 500f, density = 1f), TOLERANCE)
        assertEquals(1f, byHeight.scaleFor(2000f, 500f, density = 1f), TOLERANCE)
        // Twice as wide and as tall as designed: a log-scale halfway is the square root of 2 times 1.
        assertEquals(kotlin.math.sqrt(2f), halfway.scaleFor(2000f, 500f, density = 1f), TOLERANCE)
    }

    @Test
    fun scaleWithScreenSizeDoesNotDependOnTheDensity() {
        val scaler = CanvasScaler(referenceWidth = 400f, referenceHeight = 300f, match = 0f)

        assertEquals(scaler.scaleFor(800f, 600f, density = 1f), scaler.scaleFor(800f, 600f, density = 3f), TOLERANCE)
    }

    @Test
    fun constantPixelSizeUndoesTheDensity() {
        val scaler = CanvasScaler(mode = CanvasScaleMode.ConstantPixelSize, pixelsPerUnit = 2f)

        assertEquals(1f, scaler.scaleFor(800f, 600f, density = 2f), TOLERANCE)
        assertEquals(0.5f, scaler.scaleFor(800f, 600f, density = 4f), TOLERANCE)
    }

    @Test
    fun constantPhysicalSizeFollowsTheScreensDotsPerInch() {
        val scaler = CanvasScaler(mode = CanvasScaleMode.ConstantPhysicalSize, referenceDpi = 160f)

        assertEquals(1f, scaler.scaleFor(800f, 600f, density = 2f), TOLERANCE, "a density reported truly is already physical")
        assertEquals(0.75f, scaler.scaleFor(800f, 600f, density = 2f, dpi = 240f), TOLERANCE, "240 dpi at 2 pixels per dp")
    }

    @Test
    fun theLimitsHoldTheRuleAndThePlayerMultipliesAfterThem() {
        val scaler = CanvasScaler(referenceWidth = 100f, referenceHeight = 100f, match = 0f, minScale = 0.5f, maxScale = 2f)

        assertEquals(2f, scaler.scaleFor(1000f, 100f, density = 1f), TOLERANCE)
        assertEquals(0.5f, scaler.scaleFor(10f, 100f, density = 1f), TOLERANCE)
        assertEquals(3f, scaler.copy(playerScale = 1.5f).scaleFor(1000f, 100f, density = 1f), TOLERANCE)
    }

    @Test
    fun aBoxWithNoSizeDrawsAtOne() {
        assertEquals(1f, CanvasScaler().scaleFor(0f, 0f, density = 1f), TOLERANCE)
    }

    @Test
    fun theOverlayDrawsAtTheScaleItsBoxGives() {
        val element = CanvasElement().apply { offsetX = 10f; offsetY = 10f; width = 100f; height = 40f }
        val scaler = CanvasScaler(referenceWidth = 800f, referenceHeight = 600f, match = 0f)

        val out = ComposeHost().frame(FrameInput(400, 300)) { CanvasOverlay(listOf(CanvasItem(1, element)), scale = 3f, scaler = scaler) }

        val box = assertNotNull(out.semantics.findTag("canvas-element-1"))
        assertEquals(listOf(5, 5, 50, 20), listOf(box.x, box.y, box.width, box.height), "half the reference: half the size, whatever scale says")
    }

    @Test
    fun theOverlayMeasuresItsBoxInTheHostsDp() {
        val element = CanvasElement().apply { offsetX = 0f; offsetY = 0f; width = 100f; height = 40f }
        val scaler = CanvasScaler(referenceWidth = 200f, referenceHeight = 150f, match = 0f)

        // 400 pixels at 2 per dp is a 200 dp box: the reference, so a unit is a dp, two pixels.
        val out = ComposeHost().frame(FrameInput(400, 300)) {
            CompositionLocalProvider(LocalDensity provides 2f) { CanvasOverlay(listOf(CanvasItem(1, element)), scaler = scaler) }
        }

        assertEquals(200, assertNotNull(out.semantics.findTag("canvas-element-1")).width)
    }

    @Test
    fun aScalerIsSavedAsJsonWithItsDefaultsLeftOut() {
        val json = Json { encodeDefaults = false }
        val scaler = CanvasScaler(mode = CanvasScaleMode.ConstantPixelSize, pixelsPerUnit = 3f)

        val text = json.encodeToString(CanvasScaler.serializer(), scaler)

        assertEquals("""{"mode":"ConstantPixelSize","pixelsPerUnit":3.0}""", text)
        assertEquals(scaler, json.decodeFromString(CanvasScaler.serializer(), text))
        assertEquals(CanvasScaler(), json.decodeFromString(CanvasScaler.serializer(), "{}"))
    }

    @Test
    fun problemsNameEachSettingOutOfRange() {
        assertEquals(emptyList(), CanvasScaler().problems())
        val wrong = CanvasScaler(referenceWidth = 0f, match = 2f, pixelsPerUnit = 0f, minScale = 2f, maxScale = 1f, playerScale = 0f)

        val problems = wrong.problems()

        assertEquals(5, problems.size, problems.toString())
        assertTrue(problems.any { "maxScale" in it })
    }

    private companion object {
        const val TOLERANCE = 1e-4f
    }
}

private fun List<SemanticsNode>.findTag(tag: String): SemanticsNode? =
    firstNotNullOfOrNull { if (it.testTag == tag) it else it.children.findTag(tag) }
