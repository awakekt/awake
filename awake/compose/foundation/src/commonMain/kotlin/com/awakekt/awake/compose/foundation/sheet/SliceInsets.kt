/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.sheet

/**
 * Nine-slice insets in source pixels: the corner sizes [ImageFill] keeps when it stretches a cell.
 * [None], all 0, stretches the whole cell.
 *
 * @property left Width of the left corners.
 * @property top Height of the top corners.
 * @property right Width of the right corners.
 * @property bottom Height of the bottom corners.
 */
data class SliceInsets(val left: Int = 0, val top: Int = 0, val right: Int = 0, val bottom: Int = 0) {
    init {
        require(left >= 0 && top >= 0 && right >= 0 && bottom >= 0) { "Slice insets must not be negative." }
    }

    /** Builds insets of [all] pixels on every side. */
    constructor(all: Int) : this(all, all, all, all)

    /** The shared [None] insets. */
    companion object {
        /** No slicing: the cell is one stretched centre. */
        val None = SliceInsets()
    }
}
