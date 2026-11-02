/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.CursorImage
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
 * Shows each frame's requested [PointerCursor], or [CursorImage] when it asks for one, through
 * [setCssCursor], and only when what is shown changes, so a style write is not made on every
 * animation frame.
 *
 * The cursor starts out as [PointerCursor.Default], which nothing is written for: a canvas whose
 * page styles its own cursor keeps it until the UI asks for something else.
 *
 * An image wins over the shape while there is one, as on the desktop host, and the shape is
 * written again once the image is gone. Each image is PNG-encoded and turned into its CSS string
 * once, then reused: [CursorImage] has identity equality, so a steady frame costs a reference
 * comparison and no allocation.
 */
internal class CanvasCursor(private val setCssCursor: (String) -> Unit) {
    private var shown = PointerCursor.Default
    private var shownImage: CursorImage? = null
    private val cssByImage = HashMap<CursorImage, String>()

    /**
     * Shows this frame's request: [cursorImage]'s image if it returns one, else [cursor]'s shape.
     * A `null` provider asks for nothing, so a canvas with neither keeps whatever cursor it has.
     */
    fun showFrame(cursor: (() -> PointerCursor)?, cursorImage: (() -> CursorImage?)?) {
        if (cursor == null && cursorImage == null) return
        show(cursor?.invoke() ?: PointerCursor.Default, cursorImage?.invoke())
    }

    /** Shows [image] if there is one and it is not already shown, else [cursor] if it is not already shown. */
    fun show(cursor: PointerCursor, image: CursorImage? = null) {
        val leavingImage = image == null && shownImage != null
        val changed = if (image != null) image !== shownImage else cursor != shown || leavingImage
        shownImage = image
        if (!changed) return
        if (image != null) {
            setCssCursor(cssFor(image))
        } else {
            // Leaving an image writes the shape even when it equals the last shape shown before it.
            shown = cursor
            setCssCursor(cursor.toCssCursor())
        }
    }

    private fun cssFor(image: CursorImage): String {
        // A cursor is loaded once and held; a game that builds a new instance per frame would grow
        // this without bound, so drop the cache rather than leak it.
        if (cssByImage.size >= MAX_CACHED_IMAGES) cssByImage.clear()
        return cssByImage.getOrPut(image) { image.toCssCursor() }
    }

    private companion object {
        const val MAX_CACHED_IMAGES = 32
    }
}
