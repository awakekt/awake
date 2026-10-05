/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import com.awakekt.awake.core.math2d.Dp
import com.awakekt.awake.core.math2d.dp

/**
 * The stroke cap style applied to the endpoints of open subpaths.
 */
enum class StrokeCap {
    /**
     * Ends the stroke flat at the endpoint with no extension beyond it.
     */
    Butt,

    /**
     * Ends the stroke with a semi-circle whose diameter is the stroke width.
     */
    Round,

    /**
     * Ends the stroke with a half square that projects past the endpoint by half the stroke width.
     */
    Square,
}

typealias UiStrokeCap = StrokeCap

/**
 * The stroke join style applied to the corners where two path segments meet.
 */
enum class StrokeJoin {
    /**
     * Joins path segments by connecting outer edges at a sharp corner until the miter limit is exceeded.
     */
    Miter,

    /**
     * Joins path segments by rounding off the corner with a circular arc of radius half the stroke width.
     */
    Round,

    /**
     * Joins path segments with a flat diagonal bevel edge connecting the outer corners.
     */
    Bevel,
}

typealias UiStrokeJoin = StrokeJoin

/**
 * Style parameters controlling how path outlines and geometric strokes are stroked and tessellated.
 *
 * @property width The thickness of the stroke in density-independent pixels ([Dp]).
 * @property cap The cap style applied to open path endpoints.
 * @property join The join style applied to the corners where path segments meet.
 */
data class DrawStroke(
    val width: Dp = 1f.dp,
    val cap: StrokeCap = StrokeCap.Butt,
    val join: StrokeJoin = StrokeJoin.Miter,
)

typealias UiStroke = DrawStroke
