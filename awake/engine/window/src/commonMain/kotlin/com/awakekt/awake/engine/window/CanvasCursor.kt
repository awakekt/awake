/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.PointerCursor

/**
 * [this] cursor as the value of a CSS `cursor` property -- what a browser canvas shows.
 *
 * Kept out of the wasmJs source set so the mapping is tested on the JVM: a wrong name is silent,
 * since a browser ignores a `cursor` value it doesn't know and keeps the last valid one.
 */
internal fun PointerCursor.toCssCursor(): String = when (this) {
    PointerCursor.Default -> "default"
    PointerCursor.ResizeHorizontal -> "ew-resize"
    PointerCursor.ResizeVertical -> "ns-resize"
    PointerCursor.ResizeNorthWestSouthEast -> "nwse-resize"
    PointerCursor.ResizeNorthEastSouthWest -> "nesw-resize"
    PointerCursor.Pointer -> "pointer"
    PointerCursor.Text -> "text"
}

/**
 * Shows each frame's requested [PointerCursor] through [setCssCursor], and only when it differs
 * from the last one shown, so a style write is not made on every animation frame.
 *
 * The cursor starts out as [PointerCursor.Default], which nothing is written for: a canvas whose
 * page styles its own cursor keeps it until the UI asks for something else.
 */
internal class CanvasCursor(private val setCssCursor: (String) -> Unit) {
    private var shown = PointerCursor.Default

    /** Shows [cursor] if it is not the one already shown. */
    fun show(cursor: PointerCursor) {
        if (cursor == shown) return
        shown = cursor
        setCssCursor(cursor.toCssCursor())
    }
}
