// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.remember

/**
 * How far a scrollable is scrolled, and how far it can go.
 *
 * Caller-owned and held with [rememberScrollState], for the reason `02-modifier.md` spells out: a
 * modifier link is rebuilt every pass, so a scroll offset kept inside one would reset every frame.
 *
 * [maxValue] and [viewportSize] are written by layout rather than by the caller -- a scrollable does
 * not know its own extent until its content has been measured.
 */
class ScrollState(initial: Int = 0) {

    var value: Int = initial
        private set

    var maxValue: Int = 0
        internal set(newMax) {
            field = newMax
            // Content shrinking under a scrolled viewport would otherwise leave the offset past the
            // end, showing blank space with no way back.
            if (value > newMax) value = newMax
        }

    /** The visible extent along the scroll axis, measured. */
    var viewportSize: Int = 0
        internal set

    val canScrollForward: Boolean get() = value < maxValue

    val canScrollBackward: Boolean get() = value > 0

    /**
     * Moves by [delta] and returns what was actually consumed.
     *
     * The unconsumed remainder is what lets a nested scrollable hand the rest to its parent, and
     * what tells the frame whether the UI took this wheel event or gameplay should have it.
     */
    fun scrollBy(delta: Int): Int {
        val target = (value + delta).coerceIn(0, maxValue)
        val consumed = target - value
        value = target
        return consumed
    }

    override fun toString(): String = "ScrollState(value=$value, maxValue=$maxValue)"
}

/** A [ScrollState] that survives the next pass. */
context(_: Composer)
fun rememberScrollState(initial: Int = 0): ScrollState = remember { ScrollState(initial) }
