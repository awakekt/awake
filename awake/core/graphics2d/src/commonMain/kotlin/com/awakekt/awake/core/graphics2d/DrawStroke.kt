/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.dp

enum class StrokeCap {
    Butt,
    Round,
    Square,
}

typealias UiStrokeCap = StrokeCap

enum class StrokeJoin {
    Miter,
    Round,
    Bevel,
}

typealias UiStrokeJoin = StrokeJoin

data class DrawStroke(
    val width: Dp = 1f.dp,
    val cap: StrokeCap = StrokeCap.Butt,
    val join: StrokeJoin = StrokeJoin.Miter,
)

typealias UiStroke = DrawStroke
