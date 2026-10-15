/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.math2d.Vec2
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [CanvasElement.consumePressAt]: where a tap landed on an element, in its own units -- what a
 * map picture needs to turn a click into a place.
 */
class CanvasPressPointTest {

    private val items = mutableListOf<CanvasItem>()
    private val host = ComposeHost()

    private fun element(kind: CanvasElementKind, configure: CanvasElement.() -> Unit): CanvasElement =
        CanvasElement().apply { this.kind = kind }.apply(configure).also { items += CanvasItem(items.size + 1, it) }

    /** A press and release at pixel ([x], [y]), after a frame that places the tree. */
    private fun tap(x: Int, y: Int, scale: Float = 1f) {
        fun frame(down: Boolean = false, pressed: Boolean = false, released: Boolean = false) =
            host.frame(
                FrameInput(WIDTH, HEIGHT, pointerX = x, pointerY = y, pointerDown = down, pointerPressed = pressed, pointerReleased = released),
            ) { CanvasOverlay(items, scale = scale) }
        frame()
        frame(down = true, pressed = true)
        frame(released = true)
    }

    private fun assertNear(expected: Vec2, actual: Vec2?) {
        assertNotNull(actual, "the tap was not reported")
        assertEquals(expected.x, actual.x, TOLERANCE, "x of $actual")
        assertEquals(expected.y, actual.y, TOLERANCE, "y of $actual")
    }

    @Test
    fun aTapOnAScaledImageReportsThePointInTheImagesOwnUnits() {
        val map = element(CanvasElementKind.Image) { pressable = true; offsetX = 10f; offsetY = 10f; width = 100f; height = 50f }

        // 30 and 20 units into the image, at a canvas scale of 2.
        tap((10 + 30) * 2, (10 + 20) * 2, scale = 2f)

        assertNear(Vec2(30f, 20f), map.consumePressAt())
        assertFalse(map.consumePress(), "consumePressAt took the tap")
    }

    @Test
    fun aRotatedElementReportsThePointOnItsPictureAsIfUnturned() {
        // Turned a quarter clockwise about its centre (50, 25): what sits 10 right of the centre
        // on screen is 10 above it on the picture.
        val map = element(CanvasElementKind.Image) {
            pressable = true; offsetX = 0f; offsetY = 0f; width = 100f; height = 50f
            style = CanvasStyle(rotation = 90f)
        }

        tap(60, 25)

        assertNear(Vec2(50f, 15f), map.consumePressAt())
    }

    @Test
    fun aButtonsTapReportsWhereItLanded() {
        val button = element(CanvasElementKind.Button) { offsetX = 20f; offsetY = 20f; width = 80f; height = 40f }

        tap(25, 50)

        assertNear(Vec2(5f, 30f), button.consumePressAt())
    }

    @Test
    fun anImageIsOnlyPressedWhenMadePressableAndThenKeepsTheTapFromWhatIsUnder() {
        val under = element(CanvasElementKind.Button) { offsetX = 0f; offsetY = 0f; width = 100f; height = 50f }
        val picture = element(CanvasElementKind.Image) { order = 1; offsetX = 0f; offsetY = 0f; width = 100f; height = 50f }

        tap(40, 20)
        assertNull(picture.consumePressAt(), "an Image that is not pressable watches only")
        assertTrue(under.consumePress(), "so the tap reached the Button under it")

        picture.pressable = true
        tap(40, 20)
        assertNear(Vec2(40f, 20f), picture.consumePressAt())
        assertFalse(under.consumePress(), "a pressable Image takes the tap, as a Button would")
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 300
        const val TOLERANCE = 0.01f
    }
}
