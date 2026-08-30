/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui

import io.github.awakelab.awake.compose.ui.unit.IntOffset
import io.github.awakelab.awake.compose.ui.unit.IntSize
import io.github.awakelab.awake.compose.ui.unit.LayoutDirection

/**
 * Positions a child with [size] inside a container with [space], taking [LayoutDirection] into account.
 */
fun interface Alignment {
    fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset

    fun align(size: IntSize, space: IntSize): IntOffset = align(size, space, LayoutDirection.Ltr)

    fun interface Horizontal {
        /** Offset of a [size]-wide child inside a [space]-wide container. */
        fun align(size: Int, space: Int, layoutDirection: LayoutDirection): Int

        fun align(size: Int, space: Int): Int = align(size, space, LayoutDirection.Ltr)
    }

    fun interface Vertical {
        /** Offset of a [size]-tall child inside a [space]-tall container. */
        fun align(size: Int, space: Int): Int
    }

    companion object {
        val TopStart: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) 0 else space.width - size.width
            IntOffset(x, 0)
        }
        val TopCenter: Alignment = Alignment { size, space, _ ->
            IntOffset((space.width - size.width) / 2, 0)
        }
        val TopEnd: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) space.width - size.width else 0
            IntOffset(x, 0)
        }

        val CenterStart: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) 0 else space.width - size.width
            IntOffset(x, (space.height - size.height) / 2)
        }
        val Center: Alignment = Alignment { size, space, _ ->
            IntOffset((space.width - size.width) / 2, (space.height - size.height) / 2)
        }
        val CenterEnd: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) space.width - size.width else 0
            IntOffset(x, (space.height - size.height) / 2)
        }

        val BottomStart: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) 0 else space.width - size.width
            IntOffset(x, space.height - size.height)
        }
        val BottomCenter: Alignment = Alignment { size, space, _ ->
            IntOffset((space.width - size.width) / 2, space.height - size.height)
        }
        val BottomEnd: Alignment = Alignment { size, space, layoutDirection ->
            val x = if (layoutDirection == LayoutDirection.Ltr) space.width - size.width else 0
            IntOffset(x, space.height - size.height)
        }

        val Start: Horizontal = Horizontal { size, space, layoutDirection ->
            if (layoutDirection == LayoutDirection.Ltr) 0 else space - size
        }
        val CenterHorizontally: Horizontal = Horizontal { size, space, _ ->
            (space - size) / 2
        }
        val End: Horizontal = Horizontal { size, space, layoutDirection ->
            if (layoutDirection == LayoutDirection.Ltr) space - size else 0
        }

        val Top: Vertical = Vertical { _, _ -> 0 }
        val CenterVertically: Vertical = Vertical { size, space -> (space - size) / 2 }
        val Bottom: Vertical = Vertical { size, space -> space - size }
    }
}
