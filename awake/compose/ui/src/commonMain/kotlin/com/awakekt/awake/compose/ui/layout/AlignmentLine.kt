/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.layout

import kotlin.math.max
import kotlin.math.min

/**
 * Defines an alignment line along which layouts can align their children.
 */
sealed class AlignmentLine(
    val merger: (Int, Int) -> Int,
) {
    companion object {
        const val Unspecified: Int = Int.MIN_VALUE
    }
}

/**
 * An [AlignmentLine] defined along the horizontal axis (representing a vertical coordinate, e.g. baseline).
 */
class HorizontalAlignmentLine(
    merger: (Int, Int) -> Int,
) : AlignmentLine(merger)

/**
 * An [AlignmentLine] defined along the vertical axis (representing a horizontal coordinate).
 */
class VerticalAlignmentLine(
    merger: (Int, Int) -> Int,
) : AlignmentLine(merger)

/** First text baseline alignment line. */
val FirstBaseline = HorizontalAlignmentLine(::min)

/** Last text baseline alignment line. */
val LastBaseline = HorizontalAlignmentLine(::max)
