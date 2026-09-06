/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Lower/upper bound for [adaptiveCurveSteps]/[adaptiveArcSteps]'s chosen step count. */
internal const val MIN_ADAPTIVE_STEPS = 4
internal const val MAX_ADAPTIVE_STEPS = 64

/** Target max deviation (px) between a flattened polyline and the true curve/arc. */
internal const val CURVE_FLATNESS_TOLERANCE_PX = 0.25f

/**
 * Step count for flattening one quad/cubic Bezier segment so its max deviation from the
 * resulting polyline stays under [CURVE_FLATNESS_TOLERANCE_PX].
 */
internal fun adaptiveCurveSteps(controlNetLength: Float, minSteps: Int): Int {
    val estimate = ceil(sqrt(controlNetLength / (8f * CURVE_FLATNESS_TOLERANCE_PX)))
    val bounded = if (estimate.isFinite()) estimate.toInt() else MAX_ADAPTIVE_STEPS
    return bounded.coerceIn(minSteps.coerceIn(MIN_ADAPTIVE_STEPS, MAX_ADAPTIVE_STEPS), MAX_ADAPTIVE_STEPS)
}

/**
 * Step count for flattening one [PathCommand.ArcTo] sweep so its max sagitta (the chord's
 * bulge past the true arc) stays under [CURVE_FLATNESS_TOLERANCE_PX].
 */
internal fun adaptiveArcSteps(sweepDegrees: Float, radiusPx: Float, maxStepDegrees: Float): Int {
    val radius = radiusPx.coerceAtLeast(0f)
    val sagittaStepDegrees = if (radius <= CURVE_FLATNESS_TOLERANCE_PX) {
        Float.MAX_VALUE // sub-pixel radius: even a single step is already an invisible facet.
    } else {
        2f * acos(1f - CURVE_FLATNESS_TOLERANCE_PX / radius) * 180f / PI.toFloat()
    }
    val stepDegrees = min(sagittaStepDegrees, maxStepDegrees).coerceAtLeast(1f)
    return ceil(abs(sweepDegrees) / stepDegrees).toInt().coerceIn(1, MAX_ADAPTIVE_STEPS)
}

/**
 * Flattens every curve/arc command into line segments, adaptively.
 */
fun DrawPath.flattenContours(
    curveSteps: Int = MIN_ADAPTIVE_STEPS,
    arcStepDegrees: Float = 90f,
): List<PathContour> {
    if (commands.isEmpty()) return emptyList()

    val contours = ArrayList<PathContour>()
    var points = ArrayList<DrawPoint>()
    var cursor: DrawPoint? = null
    var subpathStart: DrawPoint? = null
    var closed = false

    fun appendPoint(point: DrawPoint) {
        if (points.lastOrNull() != point) points += point
        cursor = point
    }

    fun finishContour() {
        if (points.isEmpty()) return
        contours += PathContour(points.toList(), closed)
        points = ArrayList()
        subpathStart = null
        closed = false
    }

    commands.forEach { command ->
        when (command) {
            is PathCommand.MoveTo -> {
                finishContour()
                val point = DrawPoint(command.x, command.y)
                points += point
                cursor = point
                subpathStart = point
            }

            is PathCommand.LineTo -> appendPoint(DrawPoint(command.x, command.y))

            is PathCommand.QuadTo -> {
                val start = cursor ?: DrawPoint(command.x, command.y)
                val controlNetLength = hypot(command.cx - start.x, command.cy - start.y) +
                    hypot(command.x - command.cx, command.y - command.cy)
                val steps = adaptiveCurveSteps(controlNetLength, curveSteps)
                repeat(steps) { step ->
                    val t = (step + 1) / steps.toFloat()
                    val oneMinusT = 1f - t
                    appendPoint(
                        DrawPoint(
                            x = oneMinusT * oneMinusT * start.x + 2f * oneMinusT * t * command.cx + t * t * command.x,
                            y = oneMinusT * oneMinusT * start.y + 2f * oneMinusT * t * command.cy + t * t * command.y,
                        ),
                    )
                }
            }

            is PathCommand.CubicTo -> {
                val start = cursor ?: DrawPoint(command.x, command.y)
                val controlNetLength = hypot(command.c1x - start.x, command.c1y - start.y) +
                    hypot(command.c2x - command.c1x, command.c2y - command.c1y) +
                    hypot(command.x - command.c2x, command.y - command.c2y)
                val steps = adaptiveCurveSteps(controlNetLength, curveSteps)
                repeat(steps) { step ->
                    val t = (step + 1) / steps.toFloat()
                    val oneMinusT = 1f - t
                    appendPoint(
                        DrawPoint(
                            x = oneMinusT * oneMinusT * oneMinusT * start.x +
                                3f * oneMinusT * oneMinusT * t * command.c1x +
                                3f * oneMinusT * t * t * command.c2x +
                                t * t * t * command.x,
                            y = oneMinusT * oneMinusT * oneMinusT * start.y +
                                3f * oneMinusT * oneMinusT * t * command.c1y +
                                3f * oneMinusT * t * t * command.c2y +
                                t * t * t * command.y,
                        ),
                    )
                }
            }

            is PathCommand.ArcTo -> {
                val centerX = (command.left + command.right) / 2f
                val centerY = (command.top + command.bottom) / 2f
                val radiusX = (command.right - command.left) / 2f
                val radiusY = (command.bottom - command.top) / 2f
                val steps = adaptiveArcSteps(command.sweepDegrees, max(radiusX, radiusY), arcStepDegrees)
                repeat(steps) { step ->
                    val t = (step + 1) / steps.toFloat()
                    val angleDegrees = command.startDegrees + command.sweepDegrees * t
                    val angleRadians = angleDegrees * PI.toFloat() / 180f
                    appendPoint(
                        DrawPoint(
                            x = centerX + cos(angleRadians) * radiusX,
                            y = centerY + sin(angleRadians) * radiusY,
                        ),
                    )
                }
            }

            PathCommand.Close -> {
                closed = true
                cursor = subpathStart ?: cursor
                finishContour()
            }
        }
    }

    finishContour()
    return contours
}
