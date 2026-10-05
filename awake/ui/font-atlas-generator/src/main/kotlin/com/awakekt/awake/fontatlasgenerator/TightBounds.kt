/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import java.awt.Shape
import java.awt.geom.PathIterator
import java.awt.geom.Rectangle2D
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The box [shape] really fills: where its curves reach, not where their control points are.
 *
 * `Shape.getBounds2D` on a curved outline is not one answer. Before JDK 19 it returns the box of the
 * control points, which is larger wherever a curve bulges less than its control point suggests; from
 * JDK 19 it returns the true extent. A glyph like `@` has curves with no point at their extreme, so the
 * generator measured it a few font units wider on JDK 17 than on JDK 21 and wrote different metrics
 * and a different atlas cell. This is the true extent on every JDK. An empty path gives an empty box.
 */
internal fun tightBounds(shape: Shape): Rectangle2D {
    val extent = Extent()
    val coordinates = DoubleArray(SEGMENT_COORDINATES)
    var x = 0.0
    var y = 0.0
    val path = shape.getPathIterator(null)
    while (!path.isDone) {
        when (path.currentSegment(coordinates)) {
            PathIterator.SEG_MOVETO, PathIterator.SEG_LINETO -> {
                extent.add(coordinates[0], coordinates[1])
                x = coordinates[0]
                y = coordinates[1]
            }
            PathIterator.SEG_QUADTO -> {
                extent.addQuad(x, y, coordinates)
                x = coordinates[2]
                y = coordinates[3]
            }
            PathIterator.SEG_CUBICTO -> {
                extent.addCubic(x, y, coordinates)
                x = coordinates[4]
                y = coordinates[5]
            }
        }
        path.next()
    }
    return extent.toRectangle()
}

/** A segment's coordinates: up to three points. */
private const val SEGMENT_COORDINATES = 6

private const val EPSILON = 1e-12

/** The running box of every point and curve extremum seen so far. */
private class Extent {
    private var minX = Double.POSITIVE_INFINITY
    private var minY = Double.POSITIVE_INFINITY
    private var maxX = Double.NEGATIVE_INFINITY
    private var maxY = Double.NEGATIVE_INFINITY

    fun add(x: Double, y: Double) {
        addX(x)
        addY(y)
    }

    private fun addX(x: Double) {
        minX = minOf(minX, x)
        maxX = maxOf(maxX, x)
    }

    private fun addY(y: Double) {
        minY = minOf(minY, y)
        maxY = maxOf(maxY, y)
    }

    /** A quadratic from ([x0], [y0]) with its control point and end point in [c]`[0..3]`. */
    fun addQuad(x0: Double, y0: Double, c: DoubleArray) {
        quadExtremum(x0, c[0], c[2])?.let(::addX)
        quadExtremum(y0, c[1], c[3])?.let(::addY)
        add(c[2], c[3])
    }

    /** A cubic from ([x0], [y0]) with its two control points and end point in [c]`[0..5]`. */
    fun addCubic(x0: Double, y0: Double, c: DoubleArray) {
        cubicExtrema(x0, c[0], c[2], c[4]).forEach(::addX)
        cubicExtrema(y0, c[1], c[3], c[5]).forEach(::addY)
        add(c[4], c[5])
    }

    /** An empty box, as `getBounds2D` gives an empty path, when nothing was added. */
    fun toRectangle(): Rectangle2D =
        if (minX > maxX) Rectangle2D.Double() else Rectangle2D.Double(minX, minY, maxX - minX, maxY - minY)
}

/** The value where a quadratic through [a0], [a1], [a2] turns round, or null when it does so outside the curve. */
private fun quadExtremum(a0: Double, a1: Double, a2: Double): Double? {
    val denominator = a0 - 2 * a1 + a2
    if (denominator == 0.0) return null
    val t = (a0 - a1) / denominator
    return if (t > 0 && t < 1) {
        val mt = 1 - t
        mt * mt * a0 + 2 * mt * t * a1 + t * t * a2
    } else {
        null
    }
}

/** The values where a cubic through [a0]..[a3] turns round inside the curve: up to two. */
private fun cubicExtrema(a0: Double, a1: Double, a2: Double, a3: Double): List<Double> {
    // The derivative's coefficients: qa t^2 + qb t + qc.
    val qa = -a0 + 3 * a1 - 3 * a2 + a3
    val qb = 2 * (a0 - 2 * a1 + a2)
    val qc = a1 - a0
    val roots = when {
        abs(qa) < EPSILON -> if (qb == 0.0) emptyList() else listOf(-qc / qb)
        else -> {
            val discriminant = qb * qb - 4 * qa * qc
            if (discriminant < 0) {
                emptyList()
            } else {
                val root = sqrt(discriminant)
                listOf((-qb + root) / (2 * qa), (-qb - root) / (2 * qa))
            }
        }
    }
    return roots.filter { it > 0 && it < 1 }.map { t ->
        val mt = 1 - t
        mt * mt * mt * a0 + 3 * mt * mt * t * a1 + 3 * mt * t * t * a2 + t * t * t * a3
    }
}
