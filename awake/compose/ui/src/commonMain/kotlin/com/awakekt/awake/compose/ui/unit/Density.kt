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
    val density: Float
    val fontScale: Float

    fun Dp.roundToPx(): Int = (value * density).roundToInt()

    fun Dp.toPx(): Float = value * density

    fun Int.toDp(): Dp = (this.toFloat() / density).dp

    fun Float.toDp(): Dp = (this / density).dp
}
