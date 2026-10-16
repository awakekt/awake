/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.PointerCursor
import kotlin.test.Test
import kotlin.test.assertEquals

/** A browser canvas shows the cursor a frame asks for, by its CSS name, once per change. */
class CanvasCursorTest {
    @Test
    fun everyCursorMapsToItsCssName() {
        val expected = mapOf(
            PointerCursor.Default to "default",
            PointerCursor.ResizeHorizontal to "ew-resize",
            PointerCursor.ResizeVertical to "ns-resize",
            PointerCursor.ResizeNorthWestSouthEast to "nwse-resize",
            PointerCursor.ResizeNorthEastSouthWest to "nesw-resize",
            PointerCursor.Pointer to "pointer",
            PointerCursor.Text to "text",
        )

        assertEquals(PointerCursor.entries.toSet(), expected.keys, "a cursor has no CSS name in this test")
        assertEquals(expected, PointerCursor.entries.associateWith { it.toCssCursor() })
    }

    @Test
    fun theStyleIsWrittenOnlyWhenTheCursorChanges() {
        val written = mutableListOf<String>()
        val canvasCursor = CanvasCursor { written += it }

        canvasCursor.show(PointerCursor.Default)
        canvasCursor.show(PointerCursor.ResizeNorthWestSouthEast)
        canvasCursor.show(PointerCursor.ResizeNorthWestSouthEast)
        canvasCursor.show(PointerCursor.ResizeNorthEastSouthWest)
        canvasCursor.show(PointerCursor.Default)
        canvasCursor.show(PointerCursor.Default)

        assertEquals(listOf("nwse-resize", "nesw-resize", "default"), written)
    }
}
