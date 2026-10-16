/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.PointerCursor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The cursor a frame asks for reaches GLFW as its own shape.
 *
 * A wrong mapping is silent: GLFW draws whatever shape it is handed, so a corner handle mapped to
 * the arrow, or to the horizontal resize, looks merely odd and nothing throws.
 */
class GlfwCursorTest {
    @Test
    fun theDiagonalsAskForGlfwsDiagonalShapes() {
        // glfw3.h's GLFW_RESIZE_NWSE_CURSOR and GLFW_RESIZE_NESW_CURSOR (3.4).
        assertEquals(0x00036007, PointerCursor.ResizeNorthWestSouthEast.toGlfwCursorShape())
        assertEquals(0x00036008, PointerCursor.ResizeNorthEastSouthWest.toGlfwCursorShape())
    }

    @Test
    fun everyCursorHasAShapeOfItsOwn() {
        val shapes = PointerCursor.entries.associateWith { it.toGlfwCursorShape() }

        assertEquals(PointerCursor.entries.size, shapes.values.toSet().size, "shared shapes: $shapes")
    }
}
