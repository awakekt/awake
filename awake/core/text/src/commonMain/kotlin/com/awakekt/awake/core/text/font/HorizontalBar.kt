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
 * A horizontal bar of ink in a glyph: the crossbar of an `e`, the bar of an `H` or an `A`, the arms of an
 * `E` or an `F`. [bottom] and [top] are the heights of its two flat edges, in font units.
 */
internal class HorizontalBar(val bottom: Double, val top: Double)

/** A flat edge of an outline: its [height] and the span [from] to [to] it covers, in font units. */
private class FlatEdge(val height: Double, val from: Double, val to: Double)

/**
 * The main horizontal bars of [outline]: each flat bottom edge (ink above it) paired with the nearest flat
 * top edge above it (ink below), when the two overlap for at least [MIN_BAR_LENGTH_EM] and sit no further
 * apart than [MAX_BAR_THICKNESS_EM]. A stem's foot is not a bar, since its top edge is a whole glyph away.
 *
 * Which side of an edge is ink comes from the outline's own winding: the contour that encloses the most area
 * is the glyph's outside, and a TrueType font winds it clockwise (ink to the right of an edge's direction)
 * where a CFF one winds it counter-clockwise. Counters wind the other way, which puts the ink on the same
 * side of their edges, so one rule covers every contour.
 *
 * @param outline The glyph's outline, in font units.
 * @param unitsPerEm The font's units per em, which the thresholds scale by.
 * @return The bars, lowest first; empty when the outline has none or no usable winding.
 */
internal fun horizontalBars(outline: List<OutlineSegment>, unitsPerEm: Double): List<HorizontalBar> {
    val inkOnRight = inkIsOnRight(outline) ?: return emptyList()
    val minLength = MIN_BAR_LENGTH_EM * unitsPerEm
    val bottoms = ArrayList<FlatEdge>()
    val tops = ArrayList<FlatEdge>()
    for (index in outline.indices) {
        val p = outline[index].points
        if (!outline[index].isLine || abs(p[1] - p[7]) > FLAT_UNITS || abs(p[0] - p[6]) < minLength) continue
        val edge = FlatEdge((p[1] + p[7]) / 2, min(p[0], p[6]), max(p[0], p[6]))
        // Travelling right with the ink on the right puts it below the edge: a top edge.
        if ((p[6] > p[0]) == inkOnRight) tops += edge else bottoms += edge
    }
    val bars = ArrayList<HorizontalBar>()
    for (bottom in bottoms) {
        val top = nearestTopAbove(bottom, tops, minLength, MAX_BAR_THICKNESS_EM * unitsPerEm) ?: continue
        if (bars.none { abs(it.bottom - bottom.height) <= FLAT_UNITS && abs(it.top - top.height) <= FLAT_UNITS }) {
            bars += HorizontalBar(bottom.height, top.height)
        }
    }
    bars.sortBy { it.bottom }
    return bars
}

/** The lowest of [tops] above [bottom] by no more than [maxThickness] that overlaps it for [minLength], or null. */
private fun nearestTopAbove(bottom: FlatEdge, tops: List<FlatEdge>, minLength: Double, maxThickness: Double): FlatEdge? {
    var nearest: FlatEdge? = null
    for (top in tops) {
        val thickness = top.height - bottom.height
        val overlap = min(top.to, bottom.to) - max(top.from, bottom.from)
        val fits = thickness > FLAT_UNITS && thickness <= maxThickness && overlap >= minLength
        if (fits && (nearest == null || top.height < nearest.height)) nearest = top
    }
    return nearest
}

/** Whether the ink lies to the right of the direction of travel along the outline's outermost contour; null when it has no area. */
private fun inkIsOnRight(outline: List<OutlineSegment>): Boolean? {
    var outer = 0.0
    var area = 0.0
    for (index in outline.indices) {
        val p = outline[index].points
        // Shoelace over the control polygon: it is not the curve's area, but it has the curve's winding.
        area += p[0] * p[3] - p[2] * p[1] + p[2] * p[5] - p[4] * p[3] + p[4] * p[7] - p[6] * p[5]
        val last = index == outline.lastIndex
        if (last || outline[index + 1].points[0] != p[6] || outline[index + 1].points[1] != p[7]) {
            if (abs(area) > abs(outer)) outer = area
            area = 0.0
        }
    }
    return if (outer == 0.0) null else outer < 0.0
}

/**
 * This map with the edges of [bars] moved onto whole pixel rows, the baseline, x-height and cap-height snaps
 * kept: the map is piecewise linear through the heights it keeps ([VerticalMap.pins]) and the bars' edges,
 * so everything between stretches to fit as it does between the pins.
 *
 * A bar within [ROW_SNAP_TOLERANCE] of a whole number of rows, or thinner than one, becomes that many
 * rows, at least one, so one solid row replaces a pair of grey ones. A bar that is between, such as 1.5
 * rows at 20 px, keeps its thickness, since rounding it would make it a third heavier or lighter than its
 * stems. It sits where an edge already is on a whole pixel (the baseline under an `E`'s foot, the cap
 * height over an `F`'s arm) and otherwise with its lower edge on the row nearest its centre. A bar that
 * would cross a pin or another bar, or leave under [MIN_ROW_GAP] pixels between them, stays unsnapped.
 *
 * @param bars The glyph's bars, in font units.
 * @param scale Pixels per font unit, which maps [HorizontalBar]'s units.
 */
internal fun VerticalMap.snappingBars(bars: List<HorizontalBar>, scale: Double): VerticalMap {
    var knots = Knots(pins.copyOf(), pins.copyOf())
    var changed = false
    for (bar in bars) {
        val bottom = this(bar.bottom * scale)
        val top = this(bar.top * scale)
        val row = if (bottom < -SNAP_EPSILON) null else snappedBar(bottom, top)
        val merged = row?.let { knots.with(doubleArrayOf(bottom, top), doubleArrayOf(it.first, it.first + it.second)) }
        if (merged != null) {
            knots = merged
            changed = true
        }
    }
    val snapped = knots
    return if (changed) VerticalMap(pins) { y -> snapped.map(this(y)) } else this
}

/** The row, and how many rows, a bar from [bottom] to [top] pixels is moved to: see [snappingBars]. */
private fun snappedBar(bottom: Double, top: Double): Pair<Double, Double> {
    val thickness = top - bottom
    val whole = max(1.0, floor(thickness + HALF))
    val rows = if (abs(thickness - whole) <= ROW_SNAP_TOLERANCE) whole else max(1.0, thickness)
    val row = when {
        abs(bottom - floor(bottom + HALF)) < SNAP_EPSILON -> floor(bottom + HALF)
        abs(top - floor(top + HALF)) < SNAP_EPSILON -> floor(top + HALF) - rows
        else -> floor((bottom + top) / 2 - rows / 2 + HALF)
    }
    return row to rows
}

/** A piecewise linear map from [source] heights to [target] heights, both ascending; heights beyond the ends keep their offset. */
private class Knots(val source: DoubleArray, val target: DoubleArray) {
    /** [source] mapped. */
    fun map(y: Double): Double {
        val last = source.size - 1
        var at = 1
        while (at < last && y > source[at]) at++
        return when {
            y <= source[0] -> y + target[0] - source[0]
            y >= source[last] -> y + target[last] - source[last]
            else -> target[at - 1] + (target[at] - target[at - 1]) * (y - source[at - 1]) / (source[at] - source[at - 1])
        }
    }

    /**
     * These knots with [newSource] and [newTarget] added, or null when that would put two knots at one
     * source with different targets, or order the targets differently from the sources, or bring two targets
     * within [MIN_ROW_GAP] of each other.
     */
    fun with(newSource: DoubleArray, newTarget: DoubleArray): Knots? {
        val sources = source.toMutableList()
        val targets = target.toMutableList()
        for (index in newSource.indices) {
            val existing = sources.indexOfFirst { abs(it - newSource[index]) < SNAP_EPSILON }
            when {
                existing < 0 -> insert(sources, targets, newSource[index], newTarget[index])
                abs(targets[existing] - newTarget[index]) > SNAP_EPSILON -> return null
            }
        }
        val crowded = (1 until sources.size).any { targets[it] - targets[it - 1] < MIN_ROW_GAP }
        return if (crowded) null else Knots(sources.toDoubleArray(), targets.toDoubleArray())
    }

    private fun insert(sources: MutableList<Double>, targets: MutableList<Double>, newSource: Double, newTarget: Double) {
        val at = sources.indexOfFirst { it > newSource }.let { if (it < 0) sources.size else it }
        sources.add(at, newSource)
        targets.add(at, newTarget)
    }
}

/** A bar's edge within this many pixels of a pin or a whole row counts as already on it. */
private const val SNAP_EPSILON = 0.02

/** How far, in rows, a bar may be from a whole number of them and still be rounded to it. */
private const val ROW_SNAP_TOLERANCE = 0.25

/** The least distance, in pixels, between two snapped edges. */
private const val MIN_ROW_GAP = 0.5

/** A flat edge's slope, and the least thickness of a bar, in font units. */
private const val FLAT_UNITS = 0.5

/** A bar overlaps its two edges for at least this much of an em: about a pixel at 12 px. */
internal const val MIN_BAR_LENGTH_EM = 0.08

/** The thickest bar, as a share of an em: a Black weight's arms are about 0.2. */
internal const val MAX_BAR_THICKNESS_EM = 0.25

private const val HALF = 0.5
