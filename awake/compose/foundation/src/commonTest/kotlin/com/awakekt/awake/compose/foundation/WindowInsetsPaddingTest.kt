/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.fillMaxSize
import com.awakekt.awake.compose.foundation.layout.imePadding
import com.awakekt.awake.compose.foundation.layout.safeDrawingPadding
import com.awakekt.awake.compose.foundation.layout.systemBarsPadding
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.layout.onPlaced
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.EdgeInsets
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.WindowInsets
import kotlin.test.Test
import kotlin.test.assertEquals

class WindowInsetsPaddingTest {
    private data class Placed(val x: Int, val y: Int, val width: Int, val height: Int)

    private fun placed(insets: WindowInsets, density: Float = 1f, pad: context(Composer) Modifier.() -> Modifier): Placed {
        var result = Placed(-1, -1, -1, -1)
        val host = ComposeHost(density = density)
        host.windowInsets = insets
        host.frame(FrameInput(viewportWidth = 400, viewportHeight = 800)) {
            // The padded box's own placement spans its padding; the child shows what is left inside it.
            Box(Modifier.pad().fillMaxSize()) {
                Box(Modifier.fillMaxSize().onPlaced { x, y, w, h -> result = Placed(x, y, w, h) })
            }
        }
        return result
    }

    private val bars = EdgeInsets(left = 10f, top = 24f, right = 10f, bottom = 16f)

    @Test
    fun noInsetsLeavesTheNodeFillingTheWindow() {
        val none = WindowInsets.None
        assertEquals(Placed(0, 0, 400, 800), placed(none) { imePadding() })
        assertEquals(Placed(0, 0, 400, 800), placed(none) { safeDrawingPadding() })
        assertEquals(Placed(0, 0, 400, 800), placed(none) { systemBarsPadding() })
    }

    @Test
    fun imePaddingLiftsTheNodeByTheKeyboardAtSeveralHeights() {
        for (height in listOf(0f, 120f, 300f, 520f)) {
            val insets = WindowInsets(bars, EdgeInsets(bottom = height))
            assertEquals(Placed(0, 0, 400, 800 - height.toInt()), placed(insets) { imePadding() }, "keyboard $height")
        }
    }

    @Test
    fun systemBarsPaddingIgnoresTheKeyboard() {
        val insets = WindowInsets(bars, EdgeInsets(bottom = 300f))
        assertEquals(Placed(10, 24, 380, 760), placed(insets) { systemBarsPadding() })
    }

    @Test
    fun safeDrawingPaddingTakesTheLargerOfBarsAndKeyboardPerSide() {
        val hidden = WindowInsets(bars, EdgeInsets.None)
        assertEquals(Placed(10, 24, 380, 760), placed(hidden) { safeDrawingPadding() })
        val shown = WindowInsets(bars, EdgeInsets(bottom = 300f))
        assertEquals(Placed(10, 24, 380, 476), placed(shown) { safeDrawingPadding() })
        val short = WindowInsets(bars, EdgeInsets(bottom = 8f))
        assertEquals(Placed(10, 24, 380, 760), placed(short) { safeDrawingPadding() })
    }

    @Test
    fun insetsAreDpSoTheyScaleWithDensity() {
        val insets = WindowInsets(EdgeInsets.None, EdgeInsets(bottom = 100f))
        assertEquals(Placed(0, 0, 400, 600), placed(insets, density = 2f) { imePadding() })
    }

    @Test
    fun theKeyboardComingAndGoingIsSeenOnTheNextFrame() {
        val host = ComposeHost()
        var height = -1
        val frame = {
            host.frame(FrameInput(viewportWidth = 400, viewportHeight = 800)) {
                Box(Modifier.imePadding().fillMaxSize()) {
                    Box(Modifier.fillMaxSize().onPlaced { _, _, _, h -> height = h })
                }
            }
        }
        frame()
        assertEquals(800, height)
        host.windowInsets = WindowInsets(EdgeInsets.None, EdgeInsets(bottom = 250f))
        frame()
        assertEquals(550, height)
        host.windowInsets = WindowInsets.None
        frame()
        assertEquals(800, height)
    }
}
