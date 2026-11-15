/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.graphics.drawscope

import com.awakekt.awake.core.graphics2d.UiDrawPrimitive

/**
 * The base output of a [PaintScope] for retained draw (#1512 stage e): this paint's list, the last
 * paint's list a node's recorded range points into, and two counters a node compares before and
 * after it paints.
 *
 * A paint that does not retain clears [current] and never swaps, so it keeps the one list it had.
 */
internal class RetainedOutput {
    /** The base list this paint writes. */
    var current = mutableListOf<UiDrawPrimitive>()
        private set
    private var last = mutableListOf<UiDrawPrimitive>()

    /** Bumped by a draw a retained range cannot replay at all: an offscreen layer, a path shadow, an `unclipped {}` escape. */
    var unretainable = 0

    /**
     * Bumped by a draw whose output is not its node's origin plus a local offset, a scale, a
     * translation or a raw `emit`, so its range replays in place, never moved. Paths need no count:
     * a moved range is checked primitive by primitive, and a path never moves.
     */
    var placementBound = 0

    /** How many base primitives this paint has written so far. */
    val size: Int get() = current.size

    /** Makes this paint's list the last one, and the last one's this paint's, to be cleared. */
    fun swap() {
        val previous = current
        current = last
        last = previous
    }

    /** The primitive at [index] of the last paint's list. */
    fun previous(index: Int): UiDrawPrimitive = last[index]

    /** Appends a replayed primitive to this paint's list. */
    fun append(primitive: UiDrawPrimitive) {
        current += primitive
    }
}
