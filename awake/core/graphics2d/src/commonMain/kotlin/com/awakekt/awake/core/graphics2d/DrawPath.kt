/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.graphics2d

/**
 * The fill rule used to determine the interior of a closed path during filling and clipping.
 */
enum class FillRule {
    /**
     * Non-zero winding rule: a point is inside if the ray cast to infinity has non-zero total winding number.
     */
    NonZero,

    /**
     * Even-odd rule: a point is inside if the ray cast to infinity crosses an odd number of path segments.
     */
    EvenOdd,
}

typealias UiFillRule = FillRule

/**
 * A 2D point with float coordinates.
 *
 * @property x The horizontal X coordinate.
 * @property y The vertical Y coordinate.
 */
data class DrawPoint(
    val x: Float,
    val y: Float,
)

typealias UiPoint = DrawPoint

/**
 * A continuous polygonal contour representing a subpath, either open or closed.
 *
 * @property points The sequential list of points defining the contour outline.
 * @property closed True if the contour forms a closed loop returning to the start point.
 */
data class PathContour(
    val points: List<DrawPoint>,
    val closed: Boolean,
)

typealias UiPathContour = PathContour

/**
 * Commands defining the drawing segments of a 2D vector path.
 */
sealed interface PathCommand {
    /**
     * Moves the current position to ([x], [y]) without drawing.
     *
     * @property x The destination X coordinate.
     * @property y The destination Y coordinate.
     */
    data class MoveTo(val x: Float, val y: Float) : PathCommand

    /**
     * Draws a line segment from the current position to ([x], [y]).
     *
     * @property x The destination X coordinate.
     * @property y The destination Y coordinate.
     */
    data class LineTo(val x: Float, val y: Float) : PathCommand

    /**
     * Draws a quadratic Bezier curve to ([x], [y]) with control point ([cx], [cy]).
     *
     * @property cx The control point X coordinate.
     * @property cy The control point Y coordinate.
     * @property x The end point X coordinate.
     * @property y The end point Y coordinate.
     */
    data class QuadTo(val cx: Float, val cy: Float, val x: Float, val y: Float) : PathCommand

    /**
     * Draws a cubic Bezier curve to ([x], [y]) using control points ([c1x], [c1y]) and ([c2x], [c2y]).
     *
     * @property c1x The first control point X coordinate.
     * @property c1y The first control point Y coordinate.
     * @property c2x The second control point X coordinate.
     * @property c2y The second control point Y coordinate.
     * @property x The end point X coordinate.
     * @property y The end point Y coordinate.
     */
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
     *
     * @property left The left coordinate of the bounding rectangle.
     * @property top The top coordinate of the bounding rectangle.
     * @property right The right coordinate of the bounding rectangle.
     * @property bottom The bottom coordinate of the bounding rectangle.
     * @property startDegrees The starting angle of the arc in degrees.
     * @property sweepDegrees The angular sweep of the arc in degrees.
     */
    data class ArcTo(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val startDegrees: Float,
        val sweepDegrees: Float,
    ) : PathCommand

    /**
     * Closes the current subpath by connecting a line back to the most recent [MoveTo] point.
     */
    data object Close : PathCommand

    /**
     * Factory methods and singleton accessors for [PathCommand] instances.
     */
    companion object {
        /**
         * Singleton accessor for [PathCommand.Close].
         */
        val Close: PathCommand.Close get() = PathCommand.Close

        /**
         * Factory function for a [PathCommand.MoveTo] command.
         *
         * @param x Destination X coordinate.
         * @param y Destination Y coordinate.
         * @return A [PathCommand.MoveTo] command.
         */
        fun MoveTo(x: Float, y: Float): PathCommand.MoveTo = PathCommand.MoveTo(x, y)

        /**
         * Factory function for a [PathCommand.LineTo] command.
         *
         * @param x Destination X coordinate.
         * @param y Destination Y coordinate.
         * @return A [PathCommand.LineTo] command.
         */
        fun LineTo(x: Float, y: Float): PathCommand.LineTo = PathCommand.LineTo(x, y)

        /**
         * Factory function for a [PathCommand.QuadTo] command.
         *
         * @param cx Control point X coordinate.
         * @param cy Control point Y coordinate.
         * @param x End point X coordinate.
         * @param y End point Y coordinate.
         * @return A [PathCommand.QuadTo] command.
         */
        fun QuadTo(cx: Float, cy: Float, x: Float, y: Float): PathCommand.QuadTo = PathCommand.QuadTo(cx, cy, x, y)

        /**
         * Factory function for a [PathCommand.CubicTo] command.
         *
         * @param c1x First control point X coordinate.
         * @param c1y First control point Y coordinate.
         * @param c2x Second control point X coordinate.
         * @param c2y Second control point Y coordinate.
         * @param x End point X coordinate.
         * @param y End point Y coordinate.
         * @return A [PathCommand.CubicTo] command.
         */
        fun CubicTo(
            c1x: Float,
            c1y: Float,
            c2x: Float,
            c2y: Float,
            x: Float,
            y: Float,
        ): PathCommand.CubicTo = PathCommand.CubicTo(c1x, c1y, c2x, c2y, x, y)

        /**
         * Factory function for an [PathCommand.ArcTo] command.
         *
         * @param left Left coordinate of arc bounds.
         * @param top Top coordinate of arc bounds.
         * @param right Right coordinate of arc bounds.
         * @param bottom Bottom coordinate of arc bounds.
         * @param startDegrees Starting angle in degrees.
         * @param sweepDegrees Sweep angle in degrees.
         * @return An [PathCommand.ArcTo] command.
         */
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

/**
 * An immutable sequence of vector drawing commands evaluated with an explicit [FillRule].
 *
 * @property fillRule The fill rule determining the interior of closed contours in this path.
 * @property commands The sequential drawing commands defining the path geometry.
 */
data class DrawPath(
    val fillRule: FillRule = FillRule.NonZero,
    val commands: List<PathCommand>,
) {
    /**
     * Factory methods for building [DrawPath] instances.
     */
    companion object {
        /**
         * Builds a [DrawPath] using a declarative builder lambda.
         *
         * @param fillRule The fill rule determining the interior of closed contours.
         * @param block The builder configuration lambda.
         * @return A constructed [DrawPath].
         */
        fun build(fillRule: FillRule = FillRule.NonZero, block: PathBuilder.() -> Unit): DrawPath =
            drawPath(fillRule, block)
    }
}

typealias UiPath = DrawPath

/**
 * Mutable builder DSL used to assemble a sequence of [PathCommand]s into a [DrawPath].
 */
class PathBuilder internal constructor(
    private val fillRule: FillRule,
) {
    private val commands = ArrayList<PathCommand>()

    /**
     * Sets the current pen position to ([x], [y]) without drawing a connecting segment.
     *
     * @param x Destination X coordinate.
     * @param y Destination Y coordinate.
     */
    fun moveTo(x: Float, y: Float) {
        commands += PathCommand.MoveTo(x, y)
    }

    /**
     * Adds a straight line segment from the current position to ([x], [y]).
     *
     * @param x Destination X coordinate.
     * @param y Destination Y coordinate.
     */
    fun lineTo(x: Float, y: Float) {
        commands += PathCommand.LineTo(x, y)
    }

    /**
     * Adds a quadratic Bezier curve to ([x], [y]) using control point ([cx], [cy]).
     *
     * @param cx Control point X coordinate.
     * @param cy Control point Y coordinate.
     * @param x End point X coordinate.
     * @param y End point Y coordinate.
     */
    fun quadTo(cx: Float, cy: Float, x: Float, y: Float) {
        commands += PathCommand.QuadTo(cx, cy, x, y)
    }

    /**
     * Adds a cubic Bezier curve to ([x], [y]) using control points ([c1x], [c1y]) and ([c2x], [c2y]).
     *
     * @param c1x First control point X coordinate.
     * @param c1y First control point Y coordinate.
     * @param c2x Second control point X coordinate.
     * @param c2y Second control point Y coordinate.
     * @param x End point X coordinate.
     * @param y End point Y coordinate.
     */
    fun cubicTo(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) {
        commands += PathCommand.CubicTo(c1x, c1y, c2x, c2y, x, y)
    }

    /**
     * Adds an elliptical arc segment inscribed within the specified bounding box.
     *
     * @param left Left coordinate of the bounding rectangle.
     * @param top Top coordinate of the bounding rectangle.
     * @param right Right coordinate of the bounding rectangle.
     * @param bottom Bottom coordinate of the bounding rectangle.
     * @param startDegrees Starting angle in degrees (clockwise from positive X-axis).
     * @param sweepDegrees Angular sweep in degrees.
     */
    fun arcTo(left: Float, top: Float, right: Float, bottom: Float, startDegrees: Float, sweepDegrees: Float) {
        commands += PathCommand.ArcTo(left, top, right, bottom, startDegrees, sweepDegrees)
    }

    /**
     * Closes the current contour by drawing a straight line back to the most recent [moveTo] position.
     */
    fun close() {
        commands += PathCommand.Close
    }

    internal fun build(): DrawPath = DrawPath(fillRule = fillRule, commands = commands.toList())
}

typealias UiPathBuilder = PathBuilder

/**
 * Constructs a [DrawPath] using a declarative builder block.
 *
 * @param fillRule The fill rule determining interior regions during rasterization.
 * @param block The configuration block applied to the [PathBuilder].
 * @return The assembled [DrawPath].
 */
fun drawPath(fillRule: FillRule = FillRule.NonZero, block: PathBuilder.() -> Unit): DrawPath {
    val builder = PathBuilder(fillRule)
    builder.block()
    return builder.build()
}

/**
 * Constructs an alias [DrawPath] using a declarative builder block.
 *
 * @param fillRule The fill rule determining interior regions during rasterization.
 * @param block The configuration block applied to the [PathBuilder].
 * @return The assembled [DrawPath].
 */
fun uiPath(fillRule: FillRule = FillRule.NonZero, block: PathBuilder.() -> Unit): DrawPath =
    drawPath(fillRule, block)
