// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui

/**
 * Where a smaller child sits inside a larger space, per axis.
 *
 * `Start`/`End` rather than `Left`/`Right` -- naming only, with `LayoutDirection.Ltr` as the sole
 * value that exists today. See docs/reference/compose-engine/14-density-resize.md: the naming is
 * free now and expensive to retrofit; the mirroring can land whenever RTL is actually wanted.
 */
object Alignment {

    fun interface Horizontal {
        /** Offset of a [size]-wide child inside a [space]-wide container. */
        fun align(size: Int, space: Int): Int
    }

    fun interface Vertical {
        fun align(size: Int, space: Int): Int
    }

    val Start: Horizontal = Horizontal { _, _ -> 0 }
    val CenterHorizontally: Horizontal = Horizontal { size, space -> (space - size) / 2 }
    val End: Horizontal = Horizontal { size, space -> space - size }

    val Top: Vertical = Vertical { _, _ -> 0 }
    val CenterVertically: Vertical = Vertical { size, space -> (space - size) / 2 }
    val Bottom: Vertical = Vertical { size, space -> space - size }
}
