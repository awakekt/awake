/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.ui.unit.IntOffset
import com.awakekt.awake.compose.ui.unit.IntSize
import com.awakekt.awake.compose.ui.unit.LayoutDirection

/**
 * Positions a child with [size] inside a container with [space], taking [LayoutDirection] into account.
 */
fun interface Alignment {
    /** Returns where the child's top-left corner sits relative to the container's top-left corner. */
    fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset

    /** Aligns for [LayoutDirection.Ltr]. */
    fun align(size: IntSize, space: IntSize): IntOffset = align(size, space, LayoutDirection.Ltr)

    /** Positions a child along the horizontal axis only, swapping start and end under [LayoutDirection.Rtl]. */
    fun interface Horizontal {
        /** Offset of a [size]-wide child inside a [space]-wide container. */
        fun align(size: Int, space: Int, layoutDirection: LayoutDirection): Int

        /** Aligns for [LayoutDirection.Ltr]. */
        fun align(size: Int, space: Int): Int = align(size, space, LayoutDirection.Ltr)
    }

    /** Positions a child along the vertical axis only. */
    fun interface Vertical {
        /** Offset of a [size]-tall child inside a [space]-tall container. */
        fun align(size: Int, space: Int): Int
    }

    /**
     * The standard alignments.
     *
     * Start and end variants swap sides under [LayoutDirection.Rtl]. Centring truncates, so an odd
     * leftover pixel falls on the end or bottom side.
     */
    companion object {
        /** Top edge, start side. */
        val TopStart: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) 0 else space.width - size.width
            IntOffset(x, 0)
        }
        /** Top edge, centred horizontally. */
        val TopCenter: Alignment = Alignment { size, space, _ ->
            IntOffset((space.width - size.width) / 2, 0)
        }
        /** Top edge, end side. */
        val TopEnd: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) space.width - size.width else 0
            IntOffset(x, 0)
        }

        /** Centred vertically, start side. */
        val CenterStart: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) 0 else space.width - size.width
            IntOffset(x, (space.height - size.height) / 2)
        }
        /** Centred on both axes. */
        val Center: Alignment = Alignment { size, space, _ ->
            IntOffset((space.width - size.width) / 2, (space.height - size.height) / 2)
        }
        /** Centred vertically, end side. */
        val CenterEnd: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) space.width - size.width else 0
            IntOffset(x, (space.height - size.height) / 2)
        }

        /** Bottom edge, start side. */
        val BottomStart: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) 0 else space.width - size.width
            IntOffset(x, space.height - size.height)
        }
        /** Bottom edge, centred horizontally. */
        val BottomCenter: Alignment = Alignment { size, space, _ ->
            IntOffset((space.width - size.width) / 2, space.height - size.height)
        }
        /** Bottom edge, end side. */
        val BottomEnd: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) space.width - size.width else 0
            IntOffset(x, space.height - size.height)
        }

        /** The start edge: left under [LayoutDirection.Ltr], right under [LayoutDirection.Rtl]. */
        val Start: Horizontal = Horizontal { size, space, layoutDirection ->
            if (layoutDirection == LayoutDirection.Ltr) 0 else space - size
        }
        /** Centred along the horizontal axis. */
        val CenterHorizontally: Horizontal = Horizontal { size, space, _ ->
            (space - size) / 2
        }
        /** The end edge: right under [LayoutDirection.Ltr], left under [LayoutDirection.Rtl]. */
        val End: Horizontal = Horizontal { size, space, layoutDirection ->
            if (layoutDirection == LayoutDirection.Ltr) space - size else 0
        }

        /** The top edge. */
        val Top: Vertical = Vertical { _, _ -> 0 }
        /** Centred along the vertical axis. */
        val CenterVertically: Vertical = Vertical { size, space -> (space - size) / 2 }
        /** The bottom edge. */
        val Bottom: Vertical = Vertical { size, space -> space - size }
    }
}
