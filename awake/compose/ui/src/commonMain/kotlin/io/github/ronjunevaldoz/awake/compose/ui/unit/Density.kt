// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.unit

import io.github.ronjunevaldoz.awake.core.math2d.Dp
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
}
