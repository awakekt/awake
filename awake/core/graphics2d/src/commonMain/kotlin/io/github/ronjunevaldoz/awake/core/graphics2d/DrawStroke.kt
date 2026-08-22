// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.graphics2d

import io.github.ronjunevaldoz.awake.core.math2d.Dp
import io.github.ronjunevaldoz.awake.core.math2d.dp

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
