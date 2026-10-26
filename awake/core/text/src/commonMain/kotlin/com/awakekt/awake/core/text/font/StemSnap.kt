/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * A glyph's main stem and the horizontal move that puts it on the pixel grid.
 *
 * The move is `shift` pixels across the stem, from its left edge `from` to its right edge `to`, and fades
 * to nothing over `ramp` pixels beyond each. The stem keeps its width, since both its edges move alike, and
 * the glyph's other parts keep the places the pen puts them, apart from the shear the ramp spreads a
 * move of under half a pixel over. A glyph is a run of such moves joined by straight lines, so a line that
 * crosses a knot is cut there ([knots]) and stays straight on each side.
 *
 * @param from The stem's left edge, in pixels from the glyph origin.
 * @param to The stem's right edge, in the same pixels; infinite with an infinite `ramp` for a stem whose
 *   other edge was not found, which moves the whole glyph as one.
 * @param shift How far the stem moves, in pixels, to land its left edge on a whole pixel.
 * @param ramp How far beyond each edge the move fades out, in pixels.
 */
internal class StemSnap private constructor(
    private val from: Double,
    private val to: Double,
    private val shift: Double,
    private val ramp: Double,
) {
    /** Where the move changes slope, ascending; between and beyond them the move is linear. */
    val knots: DoubleArray = doubleArrayOf(from - ramp, from, to, to + ramp).filter { it.isFinite() }.toDoubleArray()

    /** [x] moved. */
    fun apply(x: Double): Double = x + when {
        x <= from - ramp || x >= to + ramp -> 0.0
        x < from -> shift * (x - (from - ramp)) / ramp
        x <= to -> shift
        else -> shift * (to + ramp - x) / ramp
    }

    companion object {
        /**
         * The move for [outline] at [scale] pixels per unit with its pen [subpixel] of a pixel right of a
         * whole one, or null when it has no main stem: a round or diagonal glyph keeps its own position.
         */
        fun of(outline: List<OutlineSegment>, scale: Double, subpixel: Double): StemSnap? {
            val stem = mainStem(outline) ?: return null
            val left = stem.left * scale
            val shift = floor(left + subpixel + HALF) - (left + subpixel)
            val right = stem.right
            return if (right == null) {
                // Without the stem's other edge there is no telling where it ends: the glyph moves as one.
                StemSnap(Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, shift, Double.POSITIVE_INFINITY)
            } else {
                StemSnap(left, right * scale, shift, max(MIN_RAMP_PX, stem.height * scale * RAMP_SHARE))
            }
        }
    }
}

/**
 * A glyph's main stem, in font units: [left] is the x of its left edge; [right] is the x of its right edge,
 * null if none is near; [height] is the glyph's.
 */
internal class MainStem(val left: Double, val right: Double?, val height: Double)

/** A straight vertical edge: its [x] and the heights [low] to [high] it spans, in font units. */
private class VerticalEdge(val x: Double, val low: Double, val high: Double) {
    val length: Double get() = high - low

    /** Whether this edge is the better stem than [other]: longer, or as long and further left. */
    fun beats(other: VerticalEdge): Boolean = length > other.length || (length == other.length && x < other.x)
}

/**
 * [outline]'s main stem: its longest straight vertical edge, unless that is shorter than [MIN_STEM_SHARE]
 * of the glyph's height, as a round or diagonal glyph's is. The stem's other edge is the nearest straight
 * vertical edge, to either side, that is at least a fifth as long, lies within the main edge's span, and
 * is no further than [MAX_STEM_SHARE] of the glyph's height away.
 */
internal fun mainStem(outline: List<OutlineSegment>): MainStem? {
    val edges = ArrayList<VerticalEdge>()
    var minY = Double.POSITIVE_INFINITY
    var maxY = Double.NEGATIVE_INFINITY
    for (segment in outline) {
        val p = segment.points
        minY = min(minY, min(p[1], p[7]))
        maxY = max(maxY, max(p[1], p[7]))
        if (segment.isLine && abs(p[0] - p[6]) <= STEM_SLANT_UNITS) edges += VerticalEdge(min(p[0], p[6]), min(p[1], p[7]), max(p[1], p[7]))
    }
    val height = maxY - minY
    // Prefer the leftmost of two equal stems, so 'H' and 'n' anchor on their first stem.
    val main = edges.fold(null as VerticalEdge?) { best, edge -> if (best == null || edge.beats(best)) edge else best }
    if (main == null || main.length < height * MIN_STEM_SHARE) return null
    val other = edges.filter { partnerOf(main, it, height) }.minByOrNull { abs(it.x - main.x) }?.x
    return MainStem(if (other == null) main.x else min(main.x, other), other?.let { max(main.x, it) }, height)
}

/** Whether [edge] can be the other side of the stem whose longer edge is [main], in a glyph [height] units tall. */
private fun partnerOf(main: VerticalEdge, edge: VerticalEdge, height: Double): Boolean {
    val distance = abs(edge.x - main.x)
    val within = edge.low >= main.low - STEM_SLANT_UNITS && edge.high <= main.high + STEM_SLANT_UNITS
    return distance > 2 * STEM_SLANT_UNITS && distance <= height * MAX_STEM_SHARE && within && edge.length * PARTNER_SHARE >= main.length
}

private const val HALF = 0.5

/** How far a straight edge may lean, in font units, and still count as a vertical stem. */
private const val STEM_SLANT_UNITS = 0.5

/** A stem must be at least this share of the glyph's height to anchor it: Roboto's round glyphs have straight runs of up to 0.19 of it, its stems 0.5 and more. */
private const val MIN_STEM_SHARE = 0.45

/** A stem's other edge must be at least this share of the main edge's length: 1 / 0.2. */
private const val PARTNER_SHARE = 5.0

/** The widest a stem may be, as a share of the glyph's height. */
private const val MAX_STEM_SHARE = 0.3

/** The distance over which a stem's move fades, as a share of the glyph's height. */
private const val RAMP_SHARE = 0.2

/** The shortest fade, in pixels, so a move of under half a pixel never folds the glyph back on itself. */
private const val MIN_RAMP_PX = 1.0
