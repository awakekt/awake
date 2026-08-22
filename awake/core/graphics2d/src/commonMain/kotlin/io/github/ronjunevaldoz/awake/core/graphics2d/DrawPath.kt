// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.core.graphics2d

enum class FillRule {
    NonZero,
    EvenOdd,
}

typealias UiFillRule = FillRule

data class DrawPoint(
    val x: Float,
    val y: Float,
)

typealias UiPoint = DrawPoint

data class PathContour(
    val points: List<DrawPoint>,
    val closed: Boolean,
)

typealias UiPathContour = PathContour

sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand
    data class LineTo(val x: Float, val y: Float) : PathCommand
    data class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathCommand
    data class CubicTo(
        val c1x: Float,
        val c1y: Float,
        val c2x: Float,
        val c2y: Float,
        val x: Float,
        val y: Float,
    ) : PathCommand

    /**
     * Elliptical arc inside the given bounds, using screen-space coordinates (Y-down) and
     * degree angles so backends can choose whether they flatten, tessellate, or shader-draw
     * the segment later.
     */
    data class ArcTo(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val startDegrees: Float,
        val sweepDegrees: Float,
    ) : PathCommand

    data object Close : PathCommand

    companion object {
        val Close: PathCommand.Close get() = PathCommand.Close
        fun MoveTo(x: Float, y: Float): PathCommand.MoveTo = PathCommand.MoveTo(x, y)
        fun LineTo(x: Float, y: Float): PathCommand.LineTo = PathCommand.LineTo(x, y)
        fun QuadTo(cx: Float, cy: Float, x: Float, y: Float): PathCommand.QuadTo = PathCommand.QuadTo(cx, cy, x, y)
        fun CubicTo(
            c1x: Float,
            c1y: Float,
            c2x: Float,
            c2y: Float,
            x: Float,
            y: Float,
        ): PathCommand.CubicTo = PathCommand.CubicTo(c1x, c1y, c2x, c2y, x, y)
        fun ArcTo(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            startDegrees: Float,
            sweepDegrees: Float,
        ): PathCommand.ArcTo = PathCommand.ArcTo(left, top, right, bottom, startDegrees, sweepDegrees)
    }
}

typealias UiPathCommand = PathCommand

data class DrawPath(
    val fillRule: FillRule = FillRule.NonZero,
    val commands: List<PathCommand>,
) {
    companion object {
        fun build(fillRule: FillRule = FillRule.NonZero, block: PathBuilder.() -> Unit): DrawPath =
            drawPath(fillRule, block)
    }
}

typealias UiPath = DrawPath

class PathBuilder internal constructor(
    private val fillRule: FillRule,
) {
    private val commands = ArrayList<PathCommand>()

    fun moveTo(x: Float, y: Float) {
        commands += PathCommand.MoveTo(x, y)
    }

    fun lineTo(x: Float, y: Float) {
        commands += PathCommand.LineTo(x, y)
    }

    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) {
        commands += PathCommand.QuadTo(cx, cy, x, y)
    }

    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        commands += PathCommand.CubicTo(c1x, c1y, c2x, c2y, x, y)
    }

    fun arcTo(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float) {
        commands += PathCommand.ArcTo(left, top, right, bottom, startDegrees, sweepDegrees)
    }

    fun close() {
        commands += PathCommand.Close
    }

    internal fun build(): DrawPath = DrawPath(fillRule = fillRule, commands = commands.toList())
}

typealias UiPathBuilder = PathBuilder

fun drawPath(fillRule: FillRule = FillRule.NonZero, block: PathBuilder.() -> Unit): DrawPath {
    val builder = PathBuilder(fillRule)
    builder.block()
    return builder.build()
}

fun uiPath(fillRule: FillRule = FillRule.NonZero, block: PathBuilder.() -> Unit): DrawPath =
    drawPath(fillRule, block)
