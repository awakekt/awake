/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * The rows of every [DebugSource] a [DebugRegistry] has on, laid out in [slots] fixed slots for an overlay to
 * show: the heart of that overlay, with nothing in it that draws. A host calls [refresh] at the overlay's rate and
 * then reads each slot below [count]: its [label], its [value], whether it [isTitle] or [isWarn], and whether
 * [consumeChanged] says it needs drawing again.
 *
 * A source's [DebugSource.title] takes a slot of its own, with its rows in the slots after it. Rows past the last
 * slot are dropped and counted in [dropped]; the overlay shows what fits.
 *
 * The slots are made once and reused. A number is turned into text only when the number, its decimals or its
 * warn flag changed since the slot last showed it, and a slot's text keeps its `String` instance until it changes,
 * so a steady reading allocates nothing and a host can compare by identity. Frame thread only.
 *
 * A [Float] row shows to at most [MAX_DECIMALS] places, saturates far past a billion, and shows `NaN`, `Inf` and
 * `-Inf` as those words.
 *
 * @property slots How many slots there are, titles included.
 */
@Suppress("TooManyFunctions") // A reader for each thing a slot holds, the three rows of DebugRows and their helpers.
class DebugRowBuffer(val slots: Int = DEFAULT_SLOTS) : DebugRows {
    init {
        require(slots >= 0) { "slots ($slots) must not be negative" }
    }

    private val labels = Array(slots) { "" }
    private val values = Array(slots) { "" }

    // What a slot holds: NOTHING (0) until it is first written, then a title, a number or text.
    private val kinds = IntArray(slots)
    private val numbers = LongArray(slots)
    private val places = IntArray(slots)
    private val warns = BooleanArray(slots)
    private val changes = BooleanArray(slots)

    /** Slots in use after the last [refresh]; the slots from here on keep what they last held, and are not shown. */
    var count: Int = 0
        private set

    /** Titles and rows the last [refresh] had no slot for. */
    var dropped: Int = 0
        private set

    /**
     * Writes the title and rows of every source [registry] has on into the slots, in the order they registered,
     * and returns how many slots they use. With no source on that is 0.
     */
    fun refresh(registry: DebugRegistry): Int {
        count = 0
        dropped = 0
        val sources = registry.active
        for (i in sources.indices) {
            val source = sources[i]
            title(source.title)
            source.rows(this)
        }
        return count
    }

    /** The label of [slot]: a row's name, or a title's words. */
    fun label(slot: Int): String = labels[slot]

    /** The value of [slot] as text; empty for a title. The same instance until the value changes. */
    fun value(slot: Int): String = values[slot]

    /** Whether [slot] holds a source's title rather than a row. */
    fun isTitle(slot: Int): Boolean = kinds[slot] == TITLE

    /** Whether [slot]'s row asked to be shown as something to look at. */
    fun isWarn(slot: Int): Boolean = warns[slot]

    /**
     * Whether anything about [slot] (its label, value, title or warn) changed since the last time this was
     * called for it, then clears that. A host reads each slot below [count] after a [refresh], so the changes of a
     * slot it isn't showing wait until it is.
     */
    fun consumeChanged(slot: Int): Boolean {
        val changed = changes[slot]
        changes[slot] = false
        return changed
    }

    /** Makes every slot read as changed, for a host that lost what it had drawn, such as one whose panel was remade. */
    fun invalidate() {
        changes.fill(true)
    }

    override fun row(label: String, value: Int, warn: Boolean) = number(label, value.toLong(), 0, warn)

    override fun row(label: String, value: Float, decimals: Int, warn: Boolean) {
        when {
            value.isNaN() -> text(label, NOT_A_NUMBER, warn)
            value == Float.POSITIVE_INFINITY -> text(label, INFINITY, warn)
            value == Float.NEGATIVE_INFINITY -> text(label, NEGATIVE_INFINITY, warn)
            else -> {
                val shown = decimals.coerceIn(0, MAX_DECIMALS)
                val scaled = (value.toDouble() * POWERS_OF_TEN[shown]).roundToLong().coerceIn(-MAX_SCALED, MAX_SCALED)
                number(label, scaled, shown, warn)
            }
        }
    }

    override fun row(label: String, value: String, warn: Boolean) = text(label, value, warn)

    private fun title(title: String) {
        val slot = next()
        if (slot < 0) return
        var changed = setLabel(slot, title)
        if (kinds[slot] != TITLE) {
            kinds[slot] = TITLE
            values[slot] = ""
            warns[slot] = false
            changed = true
        }
        if (changed) changes[slot] = true
    }

    private fun number(label: String, scaled: Long, decimals: Int, warn: Boolean) {
        val slot = next()
        if (slot < 0) return
        var changed = setLabel(slot, label)
        if (kinds[slot] != NUMBER || numbers[slot] != scaled || places[slot] != decimals) {
            kinds[slot] = NUMBER
            numbers[slot] = scaled
            places[slot] = decimals
            values[slot] = fixed(scaled, decimals)
            changed = true
        }
        if (setWarn(slot, warn)) changed = true
        if (changed) changes[slot] = true
    }

    private fun text(label: String, value: String, warn: Boolean) {
        val slot = next()
        if (slot < 0) return
        var changed = setLabel(slot, label)
        if (kinds[slot] != TEXT) {
            kinds[slot] = TEXT
            changed = true
        }
        // Equal text keeps the instance already shown, so a source that builds the same words each time costs nothing.
        if (values[slot] != value) {
            values[slot] = value
            changed = true
        }
        if (setWarn(slot, warn)) changed = true
        if (changed) changes[slot] = true
    }

    /** The next slot, or -1 when they are all used. */
    private fun next(): Int {
        if (count >= slots) {
            dropped++
            return -1
        }
        return count++
    }

    private fun setLabel(slot: Int, label: String): Boolean {
        if (labels[slot] == label) return false
        labels[slot] = label
        return true
    }

    private fun setWarn(slot: Int, warn: Boolean): Boolean {
        if (warns[slot] == warn) return false
        warns[slot] = warn
        return true
    }

    /** What an overlay sizes itself by. */
    companion object {
        /** Slots an overlay has unless it asks for more or fewer: room for a few sources and their rows. */
        const val DEFAULT_SLOTS: Int = 16

        /** Most decimal places a [Float] row shows; more are shown as this many. */
        const val MAX_DECIMALS: Int = 6

        private const val MAX_SCALED = 1_000_000_000_000_000L
        private const val NOT_A_NUMBER = "NaN"
        private const val INFINITY = "Inf"
        private const val NEGATIVE_INFINITY = "-Inf"

        private const val TITLE = 1
        private const val NUMBER = 2
        private const val TEXT = 3

        private val POWERS_OF_TEN = doubleArrayOf(1.0, 10.0, 100.0, 1_000.0, 10_000.0, 100_000.0, 1_000_000.0)
        private val UNITS = longArrayOf(1L, 10L, 100L, 1_000L, 10_000L, 100_000L, 1_000_000L)

        /** [scaled] with [decimals] places: 1234 and 1 read `123.4`, and -5 and 2 read `-0.05`. */
        internal fun fixed(scaled: Long, decimals: Int): String {
            if (decimals <= 0) return scaled.toString()
            val unit = UNITS[decimals]
            val magnitude = abs(scaled)
            val out = StringBuilder()
            if (scaled < 0) out.append('-')
            out.append(magnitude / unit).append('.')
            val part = (magnitude % unit).toString()
            repeat(decimals - part.length) { out.append('0') }
            return out.append(part).toString()
        }
    }
}
