/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.foundation.layout.Box
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.layout.width
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.input.pointer.pointerCursor
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PlatformEffects
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.CursorImage
import com.awakekt.awake.core.input.PointerCursor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * The cursor request travels node -> hover -> frame output.
 *
 * Every link is silent when broken: a cursor nobody collects, or a hover list nobody reads, leaves
 * the pointer as an arrow and nothing throws. `ui-core` shipped that exact gap -- its own
 * `UiCursor` doc says the platform host owns the call and that without the wiring "every
 * hover-driven cursor stays the default arrow".
 */
class PointerCursorTest {

    private fun hoverAt(x: Int, y: Int, content: context(Composer) () -> Unit): PointerCursor = effectsAt(x, y, content).cursor

    private fun effectsAt(x: Int, y: Int, content: context(Composer) () -> Unit): PlatformEffects {
        val host = ComposeHost()
        // Twice: input is dispatched against the previous frame's placed tree, so the first frame
        // has no geometry to hit-test and reports Default whatever is under the pointer.
        val input = FrameInput(viewportWidth = 100, viewportHeight = 100, pointerX = x, pointerY = y)
        host.frame(input, content)
        return host.frame(input, content).effects
    }

    private val image = CursorImage(ByteArray(4 * 4 * CursorImage.PIXEL_STRIDE), 4, 4, hotspotX = 1, hotspotY = 2)
    private val otherImage = CursorImage(ByteArray(2 * 2 * CursorImage.PIXEL_STRIDE), 2, 2)

    @Test
    fun aHoveredNodeRequestsItsCursor() {
        val cursor = hoverAt(10, 10) {
            Box(Modifier.size(40.dp).pointerCursor(PointerCursor.Text))
        }

        assertEquals(PointerCursor.Text, cursor)
    }

    @Test
    fun nothingHoveredLeavesTheDefault() {
        val cursor = hoverAt(90, 90) {
            Box(Modifier.size(40.dp).pointerCursor(PointerCursor.Text))
        }

        assertEquals(PointerCursor.Default, cursor)
    }

    @Test
    fun theInnermostRequestWins() {
        // A resize handle inside a panel shows the resize arrow, not the panel's pointer -- which
        // is CSS's rule and the one a nested control depends on.
        val cursor = hoverAt(10, 10) {
            Box(Modifier.size(80.dp).pointerCursor(PointerCursor.Pointer)) {
                Box(Modifier.size(20.dp).pointerCursor(PointerCursor.ResizeHorizontal))
            }
        }

        assertEquals(PointerCursor.ResizeHorizontal, cursor)
    }

    @Test
    fun aCornerHandleRequestsItsDiagonal() {
        // The top-left and top-right corner handles along an 80dp edge.
        val corners = listOf(10 to PointerCursor.ResizeNorthWestSouthEast, 70 to PointerCursor.ResizeNorthEastSouthWest)
        for ((x, expected) in corners) {
            val cursor = hoverAt(x, 10) {
                Row {
                    Box(Modifier.size(20.dp).pointerCursor(PointerCursor.ResizeNorthWestSouthEast))
                    Spacer(Modifier.width(40.dp))
                    Box(Modifier.size(20.dp).pointerCursor(PointerCursor.ResizeNorthEastSouthWest))
                }
            }

            assertEquals(expected, cursor, "at x=$x")
        }
    }

    @Test
    fun aNodeWithoutARequestDefersToItsAncestor() {
        val cursor = hoverAt(10, 10) {
            Box(Modifier.size(80.dp).pointerCursor(PointerCursor.Pointer)) {
                Box(Modifier.size(20.dp))
            }
        }

        assertEquals(PointerCursor.Pointer, cursor)
    }

    @Test
    fun aBoxInFrontThatRequestsACursorKeepsItOverOneBehind() {
        val cursor = hoverAt(10, 10) {
            Box(Modifier.size(40.dp).pointerCursor(PointerCursor.Text))
            Box(Modifier.size(40.dp).pointerCursor(PointerCursor.Pointer))
        }

        assertEquals(PointerCursor.Pointer, cursor)
    }

    @Test
    fun aPlainBoxInFrontLetsTheCursorOfOneBehindShow() {
        // A box that asks for nothing is transparent to the pointer, so what is behind it is hovered.
        val cursor = hoverAt(10, 10) {
            Box(Modifier.size(40.dp).pointerCursor(PointerCursor.Text))
            Box(Modifier.size(40.dp))
        }

        assertEquals(PointerCursor.Text, cursor)
    }

    @Test
    fun aHoveredNodeRequestsItsCursorImage() {
        val effects = effectsAt(10, 10) {
            Box(Modifier.size(40.dp).pointerCursor(image))
        }

        assertSame(image, effects.cursorImage)
    }

    @Test
    fun leavingTheNodeDropsTheImageRequest() {
        // The host falls back to the scene's own image, then to the system shape, on null.
        val effects = effectsAt(90, 90) {
            Box(Modifier.size(40.dp).pointerCursor(image))
        }

        assertNull(effects.cursorImage)
        assertEquals(PointerCursor.Default, effects.cursor)
    }

    @Test
    fun aFrameThatHoversNoImageNodeReportsNone() {
        val effects = effectsAt(10, 10) {
            Box(Modifier.size(40.dp).pointerCursor(PointerCursor.Text))
        }

        assertNull(effects.cursorImage)
        assertEquals(PointerCursor.Text, effects.cursor)
    }

    @Test
    fun theInnermostImageRequestWins() {
        val effects = effectsAt(10, 10) {
            Box(Modifier.size(80.dp).pointerCursor(image)) {
                Box(Modifier.size(20.dp).pointerCursor(otherImage))
            }
        }

        assertSame(otherImage, effects.cursorImage)
    }

    @Test
    fun aNodeWithoutARequestDefersToItsAncestorsImage() {
        val effects = effectsAt(10, 10) {
            Box(Modifier.size(80.dp).pointerCursor(image)) {
                Box(Modifier.size(20.dp))
            }
        }

        assertSame(image, effects.cursorImage)
    }

    @Test
    fun anInnerShapeRequestBeatsAnOuterImage() {
        // Innermost wins across both kinds, so a text field's I-beam inside an image-cursor panel
        // is not hidden by the panel's image; the host then shows the shape.
        val effects = effectsAt(10, 10) {
            Box(Modifier.size(80.dp).pointerCursor(image)) {
                Box(Modifier.size(20.dp).pointerCursor(PointerCursor.Text))
            }
        }

        assertNull(effects.cursorImage)
        assertEquals(PointerCursor.Text, effects.cursor)
    }

    @Test
    fun anInnerImageRequestBeatsAnOuterShapeAndKeepsItAsFallback() {
        val effects = effectsAt(10, 10) {
            Box(Modifier.size(80.dp).pointerCursor(PointerCursor.Pointer)) {
                Box(Modifier.size(20.dp).pointerCursor(image))
            }
        }

        assertSame(image, effects.cursorImage)
        assertEquals(PointerCursor.Pointer, effects.cursor)
    }

    @Test
    fun onOneNodeTheLaterRequestWins() {
        val imageLast = effectsAt(10, 10) {
            Box(Modifier.size(40.dp).pointerCursor(PointerCursor.Text).pointerCursor(image))
        }
        val shapeLast = effectsAt(10, 10) {
            Box(Modifier.size(40.dp).pointerCursor(image).pointerCursor(PointerCursor.Text))
        }

        assertSame(image, imageLast.cursorImage)
        assertNull(shapeLast.cursorImage)
        assertEquals(PointerCursor.Text, shapeLast.cursor)
    }

    @Test
    fun anImageNodeInFrontLetsAnImageBehindShowOnlyWhereItDoesNotCover() {
        val covered = effectsAt(10, 10) {
            Box(Modifier.size(40.dp).pointerCursor(otherImage))
            Box(Modifier.size(40.dp).pointerCursor(image))
        }

        assertSame(image, covered.cursorImage)
    }

    @Test
    fun copyingTheEffectsKeepsTheImage() {
        val effects = PlatformEffects(requestKeyboard = true, cursor = PointerCursor.Text, cursorImage = image)

        assertSame(image, effects.copy(cursor = PointerCursor.Pointer).cursorImage)
    }
}
