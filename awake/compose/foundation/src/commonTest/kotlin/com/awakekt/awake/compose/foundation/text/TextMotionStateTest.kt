/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.absoluteOffset
import com.awakekt.awake.compose.foundation.layout.offset
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.LayoutDirection
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.compose.ui.unit.sp
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextMotionStateTest {
    @Test
    fun smoothSizingHoldsAcrossUnitScaleAndDecaysAfterSettleFrames() {
        val state = TextMotionState()
        assertFalse(state.onMeasure(rawEmPx = 14f, styleScale = 1f))
        assertFalse(state.onMeasure(rawEmPx = 14f, styleScale = 1f))

        // Animated em-size change activates smoothSizing and holds through unit scale before decaying.
        assertTrue(state.onMeasure(rawEmPx = 14.2f, styleScale = 1f))
        repeat(TextMotionState.SETTLE_FRAMES - 1) {
            assertTrue(state.onMeasure(rawEmPx = 14.2f, styleScale = 1f))
        }
        assertFalse(state.onMeasure(rawEmPx = 14.2f, styleScale = 1f))

        // Starting directly at an explicit styleScale != 1f is at rest, not an animation.
        val scaledStart = TextMotionState()
        assertFalse(scaledStart.onMeasure(rawEmPx = 21.6f, styleScale = 0.9f))
        repeat(TextMotionState.SETTLE_FRAMES + 2) {
            assertFalse(scaledStart.onMeasure(rawEmPx = 21.6f, styleScale = 0.9f))
        }
    }

    @Test
    fun constantStyleScaleStaysAtRestAndChangingItHoldsThenDecays() {
        val state = TextMotionState()
        repeat(3) { assertFalse(state.onMeasure(rawEmPx = 24f, styleScale = 2f)) }

        // A scale change with the em compensated to the same pixel size still counts as motion.
        assertTrue(state.onMeasure(rawEmPx = 24f, styleScale = 2.1f))
        repeat(TextMotionState.SETTLE_FRAMES - 1) {
            assertTrue(state.onMeasure(rawEmPx = 24f, styleScale = 2.1f))
        }
        assertFalse(state.onMeasure(rawEmPx = 24f, styleScale = 2.1f))
        assertFalse(state.onMeasure(rawEmPx = 24f, styleScale = 2.1f))
    }

    @Test
    fun resolvePaintHoldsDistanceFieldAcrossScopeScaleAndTracksDirectResiduals() {
        val state = TextMotionState()
        state.onMeasure(rawEmPx = 24f, styleScale = 1f)

        val scaled = state.resolvePaint(isScopeScaled = true, isScopeAligned = false, offsetX = 10.3f, offsetY = 4.2f)
        assertTrue(scaled.forceDistanceField)
        assertFalse(scaled.subpixelTranslated)
        assertEquals(0f, scaled.residualX)

        // Single unscaled frame mid-animation (e.g. Modifier.scale(1f) elided) stays on distance field.
        val unitFrame = state.resolvePaint(isScopeScaled = false, isScopeAligned = true, offsetX = 10f, offsetY = 4f)
        assertTrue(unitFrame.forceDistanceField)
        repeat(TextMotionState.SETTLE_FRAMES - 1) {
            state.resolvePaint(isScopeScaled = false, isScopeAligned = true, offsetX = 10f, offsetY = 4f)
        }
        val settled = state.resolvePaint(isScopeScaled = false, isScopeAligned = true, offsetX = 10f, offsetY = 4f)
        assertFalse(settled.forceDistanceField)
        assertFalse(settled.subpixelTranslated)

        // Subpixel direct offset activates subpixelTranslated on both axes.
        val moved = state.resolvePaint(isScopeScaled = false, isScopeAligned = true, offsetX = 10.25f, offsetY = 4.35f)
        assertFalse(moved.forceDistanceField)
        assertTrue(moved.subpixelTranslated)
        assertEquals(0.25f, moved.residualX, 1e-4f)
        assertEquals(0.35f, moved.residualY, 1e-4f)
    }

    @Test
    fun subpixelOffsetTrackerHandlesFractionalLeadInWholePixelStepsAndImmediateIntegerStop() {
        val tracker = SubpixelOffsetTracker()
        // Static fractional start snaps immediately with zero residual.
        tracker.step(pxX = 0.4f, pxY = -0.3f, intX = 0, intY = 0)
        assertEquals(0f, tracker.residualX)
        assertEquals(0f, tracker.residualY)

        // Starting fractional motion from a snapped fractional rest blends lead-in smoothly from 0f toward 0.5f.
        tracker.step(pxX = 0.5f, pxY = -0.2f, intX = 1, intY = 0)
        val renderedX = 1f + tracker.residualX
        assertTrue(renderedX > 0f && renderedX < 0.5f, "renderedX=$renderedX should ease smoothly from 0f toward 0.5f")
        for (i in 2..10) {
            val px = 0.4f + i * 0.1f
            tracker.step(pxX = px, pxY = 0f, intX = kotlin.math.round(px).toInt(), intY = 0)
        }

        // Whole-pixel step while carrying a residual preserves smoothness; whole-pixel step on integer clears lead-in.
        tracker.step(pxX = 2.3f, pxY = 0f, intX = 2, intY = 0)
        tracker.step(pxX = 3.3f, pxY = 0f, intX = 3, intY = 0)
        assertEquals(0.3f, tracker.residualX, 1e-3f)

        // Stopping directly on an exact integer clears active state immediately.
        tracker.step(pxX = 4.0f, pxY = 0f, intX = 4, intY = 0)
        tracker.step(pxX = 4.0f, pxY = 0f, intX = 4, intY = 0)
        assertEquals(0f, tracker.residualX)

        tracker.resetTo(5.2f, 1.8f)
        assertEquals(0f, tracker.residualX)
        assertEquals(0f, tracker.residualY)
    }

    @Test
    fun rtlOffsetAndTwoAxisAbsoluteOffsetMirrorSubpixelResidualsCorrectly() {
        val style = TextStyle(size = 14.sp)
        val rtlHost = ComposeHost(density = 1f, layoutDirection = LayoutDirection.Rtl)
        var step = 0f
        fun rtlGlyphX(): Float = rtlHost.frame(FrameInput(200, 60)) {
            Box(Modifier.offset(x = step.dp, y = (step * 0.5f).dp)) { Text("Harbor", style = style) }
        }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>().first().x

        val rtl0 = rtlGlyphX()
        step = 0.2f
        val rtl1 = rtlGlyphX()
        // Positive start offset in RTL moves left by -0.2 px (plus -0.5 px subpixel quad border = -0.7 px on first moving frame).
        assertTrue(rtl1 < rtl0, "RTL positive offset must shift glyph x left: $rtl0 -> $rtl1")

        val absHost = ComposeHost(density = 1f)
        val abs0 = absHost.frame(FrameInput(200, 60)) {
            Box(Modifier.absoluteOffset(x = 0.dp, y = 0.dp)) { Text("Harbor", style = style) }
        }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>().first()
        val abs1 = absHost.frame(FrameInput(200, 60)) {
            Box(Modifier.absoluteOffset(x = 0.2f.dp, y = 0.3f.dp)) { Text("Harbor", style = style) }
        }.primitives.filterIsInstance<UiDrawPrimitive.Glyph>().first()
        assertTrue(abs(abs1.x - (abs0.x - 0.5f + 0.2f)) < 1e-3f)
        assertTrue(abs(abs1.y - (abs0.y - 0.5f + 0.3f)) < 1e-3f)
    }
}
