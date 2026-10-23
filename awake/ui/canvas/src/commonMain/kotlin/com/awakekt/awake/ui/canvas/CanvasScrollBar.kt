/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

/**
 * Virtual-row scroll bar controller over a set of [CanvasElement]s: [up] and [down] arrow buttons,
 * a [track] container, a [thumb] indicator, optional [pageUp] and [pageDown] track regions, and an
 * optional [wheel] area.
 *
 * Each arrow press moves one row, each page-track press moves [shown][scroll] rows, and fractional
 * mouse-wheel or trackpad deltas on [wheel] accumulate across frames until they cross a full notch.
 * When all rows fit (`rows <= shown`), the thumb and page regions hide automatically.
 *
 * @property up Optional button that scrolls one row up when pressed.
 * @property down Optional button that scrolls one row down when pressed.
 * @property track Optional scroll track element whose height bounds the thumb travel.
 * @property thumb Optional thumb element sized proportionally to visible rows.
 * @property pageUp Optional track region above the thumb that scrolls one page up when pressed.
 * @property pageDown Optional track region below the thumb that scrolls one page down when pressed.
 * @property wheel Optional element whose [CanvasElement.consumeWheel] delta drives wheel scrolling.
 */
class CanvasScrollBar(
    val up: CanvasElement? = null,
    val down: CanvasElement? = null,
    val track: CanvasElement? = null,
    val thumb: CanvasElement? = null,
    val pageUp: CanvasElement? = null,
    val pageDown: CanvasElement? = null,
    val wheel: CanvasElement? = null,
) {
    private var turned = 0f

    /**
     * Advances the scroll offset from [first] across [rows] total rows with [shown] rows visible on
     * screen, consuming any pending wheel, arrow, page, or thumb-drag input and updating element bounds.
     *
     * @param first Current zero-based index of the first visible row.
     * @param rows Total number of rows in the list or grid.
     * @param shown Number of rows visible at once in the viewport.
     * @return Updated zero-based index of the first visible row, clamped to `0..(rows - shown)`.
     */
    fun scroll(first: Int, rows: Int, shown: Int): Int {
        turned += wheel?.consumeWheel() ?: 0f
        val notches = turned.toInt()
        turned -= notches
        var row = first - notches
        if (up?.consumePress() == true) row--
        if (down?.consumePress() == true) row++
        if (pageUp?.consumePress() == true) row -= shown
        if (pageDown?.consumePress() == true) row += shown
        val maxFirst = (rows - shown).coerceAtLeast(0)
        if (thumb?.dragging == true && track != null && rows > shown) {
            var usableHeight = track.height
            val length = usableHeight * shown / rows
            if (length < LEAST_THUMB) {
                usableHeight -= LEAST_THUMB - length
            }
            if (usableHeight > 0f) {
                val trackTop = track.screenBounds?.y ?: track.offsetY
                val thumbLength = length.coerceAtLeast(LEAST_THUMB)
                val localTop = thumb.dragPointCanvasY - trackTop - thumbLength / 2f
                row = (localTop / usableHeight * rows).toInt()
            }
        }
        row = row.coerceIn(0, maxFirst)
        place(row, rows, shown)
        return row
    }

    private fun place(first: Int, rows: Int, shown: Int) {
        val scrolls = rows > shown
        thumb?.visible = scrolls
        pageUp?.visible = scrolls
        pageDown?.visible = scrolls
        val track = track
        val thumb = thumb
        if (!scrolls || track == null || thumb == null) return
        var height = track.height
        var length = height * shown / rows
        if (length < LEAST_THUMB) {
            height -= LEAST_THUMB - length
            length = LEAST_THUMB
        }
        thumb.height = length
        thumb.offsetY = height * first / rows
        pageUp?.apply {
            offsetY = 0f
            this.height = thumb.offsetY
        }
        pageDown?.apply {
            offsetY = thumb.offsetY + thumb.height
            this.height = (track.height - offsetY).coerceAtLeast(0f)
        }
    }

    /** Factory methods and standard node-naming conventions for [CanvasScrollBar]. */
    companion object {
        /** Minimum thumb length in canvas units (dp/pixels). */
        const val LEAST_THUMB = 6f

        /**
         * Resolves a [CanvasScrollBar] for a window rooted at [root] using the standard child names
         * (`<root> scroll up`, `<root> scroll down`, `<root> scroll`, `<root> scroll thumb`,
         * `<root> scroll page up`, `<root> scroll page down`).
         *
         * @param root Prefix name of the owning window or control.
         * @param wheel Optional name of the element that captures mouse-wheel scroll events.
         * @param named Lookup function resolving an element name to its [CanvasElement].
         * @return A configured [CanvasScrollBar].
         */
        fun of(root: String, wheel: String? = null, named: (String) -> CanvasElement?): CanvasScrollBar =
            CanvasScrollBar(
                up = named(up(root)),
                down = named(down(root)),
                track = named(track(root)),
                thumb = named(thumb(root)),
                pageUp = named(pageUp(root)),
                pageDown = named(pageDown(root)),
                wheel = wheel?.let(named),
            )

        /**
         * Standard node name for the up-arrow button under [root].
         *
         * @param root Prefix name of the owning window or control.
         * @return The up-arrow node name.
         */
        fun up(root: String): String = "$root scroll up"

        /**
         * Standard node name for the down-arrow button under [root].
         *
         * @param root Prefix name of the owning window or control.
         * @return The down-arrow node name.
         */
        fun down(root: String): String = "$root scroll down"

        /**
         * Standard node name for the scroll track under [root].
         *
         * @param root Prefix name of the owning window or control.
         * @return The scroll track node name.
         */
        fun track(root: String): String = "$root scroll"

        /**
         * Standard node name for the scroll thumb under [root].
         *
         * @param root Prefix name of the owning window or control.
         * @return The scroll thumb node name.
         */
        fun thumb(root: String): String = "$root scroll thumb"

        /**
         * Standard node name for the page-up track area under [root].
         *
         * @param root Prefix name of the owning window or control.
         * @return The page-up node name.
         */
        fun pageUp(root: String): String = "$root scroll page up"

        /**
         * Standard node name for the page-down track area under [root].
         *
         * @param root Prefix name of the owning window or control.
         * @return The page-down node name.
         */
        fun pageDown(root: String): String = "$root scroll page down"
    }
}
