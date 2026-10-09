/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.unit

import com.awakekt.awake.core.math2d.Dp
import kotlin.math.roundToInt

/**
 * Dp-to-pixel conversion, split out of [MeasureScope] because an intrinsic query needs it and must
 * not be able to call `layout()` -- asking how wide something wants to be is not measuring it.
 */
interface Density {
    /** Pixels per dp. */
    val density: Float

    /** Extra multiplier on sp text sizes, applied on top of [density]. */
    val fontScale: Float

    /** Converts this [Dp] to whole pixels, rounding to the nearest. */
    fun Dp.roundToPx(): Int = (value * density).roundToInt()

    /** Converts this [Dp] to fractional pixels. */
    fun Dp.toPx(): Float = value * density

    /** Converts this pixel count to [Dp]. */
    fun Int.toDp(): Dp = (this.toFloat() / density).dp

    /** Converts this fractional pixel count to [Dp]. */
    fun Float.toDp(): Dp = (this / density).dp
}
