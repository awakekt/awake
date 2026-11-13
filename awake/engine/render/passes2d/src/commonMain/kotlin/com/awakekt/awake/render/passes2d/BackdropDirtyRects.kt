/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes2d

import kotlin.math.max
import kotlin.math.min

/**
 * The pixels painted since a blur ran, as a few disjoint rectangles rather than one bounding box.
 *
 * One box made two panels far apart look like a single big painted area that covered the space
 * between them, so a third panel there could not share the blur (#1512). Rectangles that overlap
 * are merged into one; past [CAPACITY] rectangles everything collapses into one box, which is
 * only ever too cautious (a panel opens a blur it could have shared), never wrong.
 */
internal class BackdropDirtyRects {
    private val left = FloatArray(CAPACITY)
    private val top = FloatArray(CAPACITY)
    private val right = FloatArray(CAPACITY)
    private val bottom = FloatArray(CAPACITY)
    private var count = 0

    /** Forgets everything painted. */
    fun clear() {
        count = 0
    }

    /** Whether anything painted overlaps the rectangle. */
    fun touches(l: Float, t: Float, r: Float, b: Float): Boolean {
        for (i in 0 until count) {
            if (overlaps(i, l, t, r, b)) return true
        }
        return false
    }

    /** Records that the rectangle was painted. */
    fun add(l: Float, t: Float, r: Float, b: Float) {
        var nl = l
        var nt = t
        var nr = r
        var nb = b
        var i = 0
        while (i < count) {
            if (overlaps(i, nl, nt, nr, nb, inclusive = true)) {
                nl = min(nl, left[i])
                nt = min(nt, top[i])
                nr = max(nr, right[i])
                nb = max(nb, bottom[i])
                remove(i)
                i = 0 // the grown rectangle may now reach one it did not
            } else {
                i++
            }
        }
        if (count == CAPACITY) {
            for (j in 0 until count) {
                nl = min(nl, left[j])
                nt = min(nt, top[j])
                nr = max(nr, right[j])
                nb = max(nb, bottom[j])
            }
            count = 0
        }
        left[count] = nl
        top[count] = nt
        right[count] = nr
        bottom[count] = nb
        count++
    }

    /** Whether rectangle [i] overlaps the box; [inclusive] also counts rectangles that only share an edge. */
    @Suppress("LongParameterList")
    private fun overlaps(i: Int, l: Float, t: Float, r: Float, b: Float, inclusive: Boolean = false): Boolean {
        val across = if (inclusive) right[i] >= l && left[i] <= r else right[i] > l && left[i] < r
        val down = if (inclusive) bottom[i] >= t && top[i] <= b else bottom[i] > t && top[i] < b
        return across && down
    }

    private fun remove(index: Int) {
        count--
        left[index] = left[count]
        top[index] = top[count]
        right[index] = right[count]
        bottom[index] = bottom[count]
    }

    private companion object {
        const val CAPACITY = 8
    }
}
