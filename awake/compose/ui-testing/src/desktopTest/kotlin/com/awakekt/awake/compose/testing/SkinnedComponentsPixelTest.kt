/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.testing

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.sheet.SliceInsets
import com.awakekt.awake.compose.foundation.skin.LocalSkin
import com.awakekt.awake.compose.foundation.skin.SheetRect
import com.awakekt.awake.compose.foundation.skin.Skin
import com.awakekt.awake.compose.foundation.skin.SkinnedBar
import com.awakekt.awake.compose.foundation.skin.SkinnedBarColors
import com.awakekt.awake.compose.foundation.skin.SkinnedButton
import com.awakekt.awake.compose.foundation.skin.SkinnedButtonColors
import com.awakekt.awake.compose.foundation.skin.SkinnedFrame
import com.awakekt.awake.compose.foundation.skin.SkinnedKeys
import com.awakekt.awake.compose.foundation.skin.skin
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.provides
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.graphics.FillDirection
import com.awakekt.awake.compose.ui.graphics.ImageBitmap
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.color.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The skinned button, frame and gauge draw from a skin, and from their tokens without one. */
class SkinnedComponentsPixelTest {
    // Six 4 x 4 cells in a row, one solid colour each: the button's four states, the bar track, the bar fill.
    private val sheet = ImageBitmap(
        24,
        4,
        ByteArray(24 * 4 * 4) { i ->
            val colour = ART[(i / 4) % 24 / 4]
            val channel = i % 4
            if (channel == 3) 255.toByte() else ((colour shr (16 - 8 * channel)) and 0xFF).toByte()
        },
    )
    private val full: Skin = skin(sheet) {
        states(SkinnedKeys.ButtonFace, SheetRect(0, 0, 16, 4))
        frame(SkinnedKeys.BarTrack, SheetRect(16, 0, 4, 4), SliceInsets(1))
        bar(SkinnedKeys.BarFill, SheetRect(20, 0, 4, 4), SliceInsets(1))
        frame(SkinnedKeys.Frame, SheetRect(16, 0, 4, 4), SliceInsets(1))
    }
    private val onlyFrame: Skin = skin(sheet) { frame(SkinnedKeys.Frame, SheetRect(16, 0, 4, 4), SliceInsets(1)) }
    private val colors = SkinnedButtonColors(Color(1f, 0f, 0f), Color(0f, 1f, 0f), Color(0f, 0f, 1f), Color(1f, 1f, 0f))

    private fun rgbAt(session: ComposeTestSession, size: Int, x: Int, y: Int): Int =
        rgbAt(session.frame(), size, x, y)

    private fun rgbAt(frame: ComposeComponentFrame, size: Int, x: Int, y: Int): Int {
        val px = frame.primitives.rasterize(size, size)
        val o = (y * size + x) * 4
        return ((px[o].toInt() and 0xFF) shl 16) or ((px[o + 1].toInt() and 0xFF) shl 8) or (px[o + 2].toInt() and 0xFF)
    }

    private fun buttonStates(skin: Skin?, expected: List<Int>) {
        for (density in listOf(1f, 1.5f)) {
            var enabled = true
            val session = composeTestSession(width = 24, height = 24, density = density) {
                CompositionLocalProvider(LocalSkin provides skin) {
                    SkinnedButton({}, Modifier.size(8.dp).testTag("b"), enabled, colors = colors)
                }
            }
            val mid = (4 * density).toInt()
            assertEquals(expected[0], rgbAt(session, 24, mid, mid), "normal at $density")
            assertEquals(expected[1], rgbAt(session.hover("b"), 24, mid, mid), "hovered at $density")
            val press = FrameInput(24, 24, pointerX = mid, pointerY = mid, pointerDown = true)
            assertEquals(expected[2], rgbAt(session.frame(press), 24, mid, mid), "pressed at $density")
            enabled = false
            assertEquals(expected[3], rgbAt(session.frame(press), 24, mid, mid), "disabled at $density")
        }
    }

    @Test
    fun aSkinnedButtonShowsItsFourStatesFromTheSkin() = buttonStates(full, ART.take(4))

    @Test
    fun withNoSkinTheButtonShowsItsTokenColours() = buttonStates(null, TOKENS)

    @Test
    fun aSkinMissingTheButtonPartFallsBackToTokens() = buttonStates(onlyFrame, TOKENS)

    @Test
    fun aDisabledButtonTakesNoClick() {
        var clicks = 0
        val session = composeTestSession(width = 24, height = 24) {
            SkinnedButton({ clicks++ }, Modifier.size(8.dp).testTag("b"), enabled = false)
        }
        session.frame()
        session.click("b")
        assertEquals(0, clicks)
        val enabled = composeTestSession(width = 24, height = 24) {
            SkinnedButton({ clicks++ }, Modifier.size(8.dp).testTag("b"))
        }
        enabled.frame()
        enabled.click("b")
        assertEquals(1, clicks)
    }

    private fun gaugeProbe(skin: Skin?, direction: FillDirection, value: Float, density: Float): List<Int> {
        val horizontal = !direction.vertical
        val session = composeTestSession(width = 48, height = 48, density = density) {
            CompositionLocalProvider(LocalSkin provides skin) {
                SkinnedBar(
                    { value },
                    Modifier.size(if (horizontal) 20.dp else 4.dp, if (horizontal) 4.dp else 20.dp),
                    direction,
                    colors = SkinnedBarColors(Color(0f, 0f, 0f), Color(1f, 1f, 1f)),
                )
            }
        }
        val length = (20 * density).toInt()
        val across = (2 * density).toInt()
        // Probe at a quarter and at three quarters along the gauge, from its filling edge.
        return listOf(length / 4, length * 3 / 4).map { along ->
            if (horizontal) {
                rgbAt(session, 48, if (direction == FillDirection.LeftToRight) along else length - along, across)
            } else {
                rgbAt(session, 48, across, if (direction == FillDirection.TopToBottom) along else length - along)
            }
        }
    }

    @Test
    fun aSkinnedGaugeFillsFromItsEdgeAtEveryValueDensityAndAxis() {
        for (density in listOf(1f, 1.5f)) {
            for (direction in listOf(FillDirection.LeftToRight, FillDirection.BottomToTop)) {
                val track = ART[4]
                val fill = ART[5]
                assertEquals(listOf(track, track), gaugeProbe(full, direction, 0f, density), "0 $direction $density")
                assertEquals(listOf(fill, track), gaugeProbe(full, direction, 0.5f, density), "0.5 $direction $density")
                assertEquals(listOf(fill, fill), gaugeProbe(full, direction, 1f, density), "1 $direction $density")
            }
        }
    }

    @Test
    fun aGaugeWithNoSkinFillsFromItsTokenColours() {
        val black = 0
        val white = 0xFFFFFF
        for (direction in FillDirection.entries) {
            assertEquals(listOf(black, black), gaugeProbe(null, direction, 0f, 1f), "0 $direction")
            assertEquals(listOf(white, black), gaugeProbe(null, direction, 0.5f, 1f), "0.5 $direction")
            assertEquals(listOf(white, white), gaugeProbe(null, direction, 1f, 1f), "1 $direction")
        }
    }

    @Test
    fun aFramePadsItsContentByTheSliceInsetsUnlessOverridden() {
        fun contentLeft(skin: Skin?, density: Float): Int {
            val session = composeTestSession(width = 48, height = 48, density = density) {
                CompositionLocalProvider(LocalSkin provides skin) {
                    SkinnedFrame(Modifier.size(24.dp)) { Box(Modifier.size(4.dp).testTag("c")) }
                }
            }
            return session.frame().onNodeWithTag("c").getBoundsInRoot().left
        }
        assertEquals(1, contentLeft(full, 1f), "one source pixel of inset at 1 px per dp")
        assertTrue(contentLeft(full, 1.5f) in 1..2, "1.5 px, rounded")
        assertEquals(0, contentLeft(null, 1f), "no skin, no inset")
    }

    private companion object {
        val ART = listOf(0xC03030, 0x30C030, 0x3030C0, 0x808080, 0x202020, 0xE0E040)
        val TOKENS = listOf(0xFF0000, 0x00FF00, 0x0000FF, 0xFFFF00)
    }
}
