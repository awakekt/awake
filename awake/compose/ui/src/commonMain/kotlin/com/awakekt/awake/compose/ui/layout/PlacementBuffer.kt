/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.layout

/**
 * Node-owned scratch table for a [MeasurePolicy] to record measured [Placeable]s and their resolved
 * positions during `measure()`, replaying them during placement with zero per-pass allocations.
 *
 * Obtained from [MeasureScope.placementBuffer] and passed back to [MeasureScope.layout]. Reused
 * across layout passes on the same node, so do not retain a buffer or its placeables past the pass.
 */
class PlacementBuffer {
    private var placeables: Array<Placeable?> = emptyArray()
    private var xs: IntArray = EMPTY_COORDS
    private var ys: IntArray = EMPTY_COORDS
    private var relative: BooleanArray = EMPTY_FLAGS
    private var activeCount: Int = 0

    internal val placeBlock: PlacementScope.() -> Unit = {
        for (i in 0 until activeCount) {
            val placeable = placeables[i] ?: continue
            if (relative[i]) {
                placeable.placeRelativeAt(xs[i], ys[i])
            } else {
                placeable.placeAbsoluteAt(xs[i], ys[i])
            }
        }
    }

    /** Current number of prepared child slots. */
    val size: Int get() = activeCount

    /**
     * Resets this buffer for [count] child slots, growing backing storage only when [count] exceeds
     * capacity and clearing every previous slot so disposed children are never retained.
     */
    fun prepare(count: Int) {
        if (count > placeables.size) {
            val next = maxOf(count, placeables.size * 2)
            placeables = arrayOfNulls(next)
            xs = IntArray(next)
            ys = IntArray(next)
            relative = BooleanArray(next)
        } else {
            for (i in 0 until activeCount) placeables[i] = null
        }
        activeCount = count
    }

    /** Stores the measured [placeable] at [index]. */
    fun setPlaceable(index: Int, placeable: Placeable?) {
        placeables[index] = placeable
    }

    /** Returns the measured [Placeable] stored at [index], or `null` when none was set. */
    fun placeableAt(index: Int): Placeable? = placeables[index]

    /** Records an absolute placement ([PlacementScope.placeAbsoluteAt]) for slot [index]. */
    fun setAbsolute(index: Int, x: Int, y: Int) {
        xs[index] = x
        ys[index] = y
        relative[index] = false
    }

    /** Records a layout-direction-aware placement ([PlacementScope.placeRelativeAt]) for slot [index]. */
    fun setRelative(index: Int, x: Int, y: Int) {
        xs[index] = x
        ys[index] = y
        relative[index] = true
    }

    /**
     * Returns a zeroed scratch [IntArray] of at least [size] entries for intermediate measure math
     * (such as weighted-child shares) before positions are recorded via [setAbsolute]/[setRelative].
     */
    fun scratchInts(): IntArray {
        for (i in 0 until activeCount) xs[i] = 0
        return xs
    }

    /** Releases all held [Placeable] references when the owning node is disposed. */
    fun clear() {
        for (i in 0 until activeCount) placeables[i] = null
        activeCount = 0
    }
}

private val EMPTY_COORDS = IntArray(0)
private val EMPTY_FLAGS = BooleanArray(0)
