/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The area of each pixel a glyph's outline covers, at one exact size: what a small label draws 1:1.
 *
 * Each pixel row is cut into [SUB_ROWS] scanlines. On each, the outline's crossings are sorted and walked
 * with the non-zero winding rule, and every span the glyph fills adds its exact length to the pixels under
 * it, so coverage is exact across a row and resolved to 1/[SUB_ROWS] down it. Winding, not a sum of areas,
 * decides what is inside: Roboto's instances draw a bowl and its stem, or an arch and its stem, as
 * contours that overlap, and an area sum counts a pixel both edge into twice. (FreeType's accumulation
 * rasterizer darkens those pixels for that reason.) Curves are cut into lines no further than
 * [FLATNESS_PX] from the curve, the count coming from the curve's own control points, so the same outline
 * always cuts the same way.
 *
 * Only `Double` arithmetic and square roots touch a value, which round the same on the JVM, Kotlin/JS,
 * Wasm and Native, so a glyph rasterizes to the same bytes on every target. (`Float` would not: Kotlin/JS
 * keeps a `Float` in a double.)
 */
internal class CoverageRasterizer {
    private var coverage = DoubleArray(0)
    private var crossingX = DoubleArray(INITIAL_CROSSINGS)
    private var crossingWinding = IntArray(INITIAL_CROSSINGS)

    /**
     * Rasterizes [lines], a flattened outline in pixels (y down, a contour closed by its own last line),
     * into a [width] by [height] coverage mask, one byte per pixel, row-major from the top-left. Ink outside
     * the mask is clipped.
     */
    fun rasterize(lines: LineBuffer, width: Int, height: Int): ByteArray {
        val size = width * height
        if (coverage.size < size) coverage = DoubleArray(size) else coverage.fill(0.0, 0, size)
        if (crossingX.size < lines.count) {
            crossingX = DoubleArray(lines.count)
            crossingWinding = IntArray(lines.count)
        }
        for (row in 0 until height) {
            for (sub in 0 until SUB_ROWS) {
                scanline(lines, row, row + (sub + HALF) / SUB_ROWS, width)
            }
        }
        val mask = ByteArray(size)
        for (index in 0 until size) {
            mask[index] = floor(min(coverage[index] / SUB_ROWS, 1.0) * BYTE_MAX + HALF).toInt().toByte()
        }
        return mask
    }

    /** Fills the spans of the scanline at [y] that the outline winds around, into row [row]. */
    private fun scanline(lines: LineBuffer, row: Int, y: Double, width: Int) {
        val count = sortCrossings(lines, y)
        var winding = 0
        var start = 0.0
        for (index in 0 until count) {
            val before = winding
            winding += crossingWinding[index]
            if (before == 0 && winding != 0) {
                start = crossingX[index]
            } else if (before != 0 && winding == 0) {
                span(row, start, crossingX[index], width)
            }
        }
    }

    /** Puts where the scanline at [y] crosses the outline, and each crossing's direction, in x order; returns how many. */
    private fun sortCrossings(lines: LineBuffer, y: Double): Int {
        var count = 0
        for (index in 0 until lines.count) {
            val y0 = lines.y0(index)
            val y1 = lines.y1(index)
            // Half-open in y, so a scanline through a vertex counts the two edges meeting there once.
            val crosses = if (y0 < y1) y >= y0 && y < y1 else y >= y1 && y < y0
            if (!crosses) continue
            val x0 = lines.x0(index)
            val x = x0 + (y - y0) * (lines.x1(index) - x0) / (y1 - y0)
            val winding = if (y1 > y0) 1 else -1
            // Insertion sort: a glyph's scanline crosses a handful of edges.
            var at = count
            while (at > 0 && crossingX[at - 1] > x) {
                crossingX[at] = crossingX[at - 1]
                crossingWinding[at] = crossingWinding[at - 1]
                at--
            }
            crossingX[at] = x
            crossingWinding[at] = winding
            count++
        }
        return count
    }

    /** Adds the span from [from] to [to], in pixels along row [row], to the pixels it covers. */
    private fun span(row: Int, from: Double, to: Double, width: Int) {
        val left = max(from, 0.0)
        val right = min(to, width.toDouble())
        if (right <= left) return
        val base = row * width
        val first = floor(left).toInt()
        val last = min(floor(right).toInt(), width - 1)
        if (first == last) {
            coverage[base + first] += right - left
            return
        }
        coverage[base + first] += first + 1 - left
        for (column in first + 1 until last) coverage[base + column] += 1.0
        coverage[base + last] += right - last
    }

    private companion object {
        /** Scanlines per pixel row. */
        const val SUB_ROWS = 16
        const val INITIAL_CROSSINGS = 64
        const val BYTE_MAX = 255.0
        const val HALF = 0.5
    }
}

/** A growable list of line segments in pixels, kept as flat doubles so flattening an outline allocates nothing per point. */
internal class LineBuffer {
    private var values = DoubleArray(INITIAL_CAPACITY * 4)

    /** How many lines the buffer holds. */
    var count: Int = 0
        private set

    fun clear() {
        count = 0
    }

    fun add(x0: Double, y0: Double, x1: Double, y1: Double) {
        if (count * 4 + 4 > values.size) values = values.copyOf(values.size * 2)
        val at = count * 4
        values[at] = x0
        values[at + 1] = y0
        values[at + 2] = x1
        values[at + 3] = y1
        count++
    }

    fun x0(index: Int): Double = values[index * 4]

    fun y0(index: Int): Double = values[index * 4 + 1]

    fun x1(index: Int): Double = values[index * 4 + 2]

    fun y1(index: Int): Double = values[index * 4 + 3]

    private companion object {
        const val INITIAL_CAPACITY = 256
    }
}

/**
 * Cuts [outline] (font units, y up) into lines in pixels (y down) for [CoverageRasterizer]: x is
 * `x * scale + shiftX`, moved by [snap] when there is one, y is `-mapY(y * scale)` with [mapY] the
 * vertical hinting (identity for none).
 *
 * A cubic is cut into `n` equal steps of its parameter. Its distance from the chord of a step is at most
 * 3/4 of its control polygon's largest second difference over n squared, so n is chosen to keep that
 * under [FLATNESS_PX].
 */
internal fun flattenOutline(
    outline: List<OutlineSegment>,
    scale: Double,
    shiftX: Double,
    mapY: (Double) -> Double,
    into: LineBuffer,
    snap: StemSnap? = null,
) {
    into.clear()
    val mover = LineMover(into, shiftX, snap)
    for (index in outline.indices) {
        val segment = outline[index]
        val p = segment.points
        val startX = p[0] * scale
        val startY = -mapY(p[1] * scale)
        val endX = p[6] * scale
        val endY = -mapY(p[7] * scale)
        if (segment.isLine) {
            mover.add(startX, startY, endX, endY)
            continue
        }
        val ddx = max(abs(p[0] - 2 * p[2] + p[4]), abs(p[2] - 2 * p[4] + p[6])) * scale
        val ddy = max(abs(p[1] - 2 * p[3] + p[5]), abs(p[3] - 2 * p[5] + p[7])) * scale
        val steps = ceil(sqrt(FLATNESS_FACTOR * sqrt(ddx * ddx + ddy * ddy) / FLATNESS_PX)).toInt().coerceIn(1, MAX_CURVE_STEPS)
        var previousX = startX
        var previousY = startY
        for (step in 1 until steps) {
            val t = step.toDouble() / steps
            val x = cubicAt(t, p[0], p[2], p[4], p[6]) * scale
            val y = -mapY(cubicAt(t, p[1], p[3], p[5], p[7]) * scale)
            mover.add(previousX, previousY, x, y)
            previousX = x
            previousY = y
        }
        mover.add(previousX, previousY, endX, endY)
    }
}

/**
 * Adds lines to [into] with each x moved by [snap] and then by [shiftX]. A line that crosses one of
 * [snap]'s knots is added as the pieces either side of it, each straight in the moved outline as the
 * original was.
 */
private class LineMover(private val into: LineBuffer, private val shiftX: Double, private val snap: StemSnap?) {
    /** Adds the line from ([x0], [y0]) to ([x1], [y1]) moved. */
    fun add(x0: Double, y0: Double, x1: Double, y1: Double) {
        if (snap == null) {
            into.add(x0 + shiftX, y0, x1 + shiftX, y1)
            return
        }
        val knots = snap.knots
        var fromX = x0
        var fromY = y0
        // Knots ascend; walk them in the line's own direction.
        for (step in knots.indices) {
            val knot = if (x1 >= x0) knots[step] else knots[knots.size - 1 - step]
            if (knot <= min(x0, x1) || knot >= max(x0, x1)) continue
            val y = y0 + (knot - x0) / (x1 - x0) * (y1 - y0)
            into.add(snap.apply(fromX) + shiftX, fromY, snap.apply(knot) + shiftX, y)
            fromX = knot
            fromY = y
        }
        into.add(snap.apply(fromX) + shiftX, fromY, snap.apply(x1) + shiftX, y1)
    }
}

private fun cubicAt(t: Double, a0: Double, a1: Double, a2: Double, a3: Double): Double {
    val u = 1 - t
    return u * u * u * a0 + 3 * u * u * t * a1 + 3 * u * t * t * a2 + t * t * t * a3
}

/** How far, in pixels, a flattened curve may stray from the curve. */
private const val FLATNESS_PX = 1.0 / 32

/** The bound on a cubic step's distance from its chord, as a share of its largest second difference. */
private const val FLATNESS_FACTOR = 0.75

/** A ceiling on steps per curve, so a malformed outline cannot cost unbounded work. */
private const val MAX_CURVE_STEPS = 64
