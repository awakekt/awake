/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

/**
 * Where a string's user-perceived characters begin and end: Unicode's extended grapheme clusters
 * (UAX #29), the unit a caret, a backspace and a truncation step by.
 *
 * One cluster can be many `Char`s. A ZWJ family emoji (👨‍👩‍👧) is eight, a skin-toned thumb (👍🏽) four,
 * a flag (🇵🇭) four, a keycap (1️⃣) three, and `e` with a combining acute two. Hangul jamo sequences
 * and CR LF are one cluster each too. Stepping by `Char` or by code point splits them; stepping by
 * cluster never does.
 *
 * Indices are `Char` indices into the text, as everywhere else in Kotlin. A boundary is an index
 * a cluster starts or ends at; `0` and `text.length` always are.
 *
 * The break properties come from the Unicode Character Database, version
 * [GRAPHEME_BREAK_UNICODE_VERSION], built into a table by the `generateGraphemeBreakTable` task.
 * Nothing here allocates, so a layout or paint pass can call it for every cluster of every frame.
 */
object GraphemeClusters {
    /** The Unicode version whose grapheme break rules and properties this follows. */
    val unicodeVersion: String get() = GRAPHEME_BREAK_UNICODE_VERSION

    /**
     * The end of the cluster that starts at [index], which is the next boundary after it.
     *
     * [index] must itself be a boundary, as `0` and every index this returns are. Call
     * [floorBoundary] first for an index that may sit inside a cluster. Returns `text.length` at or
     * past the end.
     */
    @Suppress("ReturnCount") // The end, the first break, or the end again: a loop with an early exit.
    fun nextBoundary(text: CharSequence, index: Int): Int {
        val length = text.length
        if (index >= length) return length
        var i = index.coerceAtLeast(0)
        var codePoint = CodePoints.at(text, i)
        var previous = propertiesOf(codePoint)
        i += CodePoints.charCount(codePoint)
        var regionalIndicators = if (previous and GCB_MASK == GCB_REGIONAL_INDICATOR) 1 else 0
        var emoji = if (previous and EXTENDED_PICTOGRAPHIC != 0) EMOJI_PICTOGRAPH else EMOJI_NONE
        var linker = previous and INCB_MASK == INCB_LINKER
        while (i < length) {
            codePoint = CodePoints.at(text, i)
            val next = propertiesOf(codePoint)
            if (isBreak(previous, next, regionalIndicators, emoji, linker)) return i
            regionalIndicators = if (next and GCB_MASK == GCB_REGIONAL_INDICATOR) regionalIndicators + 1 else 0
            emoji = nextEmojiState(emoji, next)
            linker = when (next and INCB_MASK) {
                INCB_LINKER -> true
                INCB_EXTEND -> linker
                else -> false
            }
            previous = next
            i += CodePoints.charCount(codePoint)
        }
        return length
    }

    /** The last boundary before [index]; `0` at or before the start. [index] need not be a boundary. */
    fun previousBoundary(text: CharSequence, index: Int): Int {
        val end = index.coerceAtMost(text.length)
        if (end <= 0) return 0
        var boundary = safeStart(text, end - 1)
        while (true) {
            val next = nextBoundary(text, boundary)
            if (next >= end) return boundary
            boundary = next
        }
    }

    /** Whether a cluster starts or ends at [index]. Out of range is never a boundary. */
    fun isBoundary(text: CharSequence, index: Int): Boolean {
        if (index < 0 || index > text.length) return false
        return index == 0 || index == text.length || floorBoundary(text, index) == index
    }

    /** [index] if it is a boundary, otherwise the start of the cluster it sits inside. */
    fun floorBoundary(text: CharSequence, index: Int): Int {
        val bounded = index.coerceIn(0, text.length)
        if (bounded == 0 || bounded == text.length) return bounded
        var boundary = safeStart(text, bounded)
        while (true) {
            val next = nextBoundary(text, boundary)
            if (next > bounded) return boundary
            boundary = next
        }
    }

    /** [index] if it is a boundary, otherwise the end of the cluster it sits inside. */
    fun ceilBoundary(text: CharSequence, index: Int): Int {
        val floor = floorBoundary(text, index)
        return if (floor == index.coerceIn(0, text.length)) floor else nextBoundary(text, floor)
    }

    /** How many clusters [text] holds: what a reader counts as its characters. */
    fun count(text: CharSequence): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            index = nextBoundary(text, index)
            count++
        }
        return count
    }

    /** Calls [action] with the start and end of each cluster of [text], in order. */
    inline fun forEach(text: CharSequence, action: (start: Int, end: Int) -> Unit) {
        var start = 0
        while (start < text.length) {
            val end = nextBoundary(text, start)
            action(start, end)
            start = end
        }
    }

    /**
     * An index no cluster spans that is at or before [index]: just after the nearest earlier line
     * feed, since a cluster always ends at one (rule GB4), or the start of the text.
     */
    private fun safeStart(text: CharSequence, index: Int): Int {
        var i = index - 1
        while (i >= 0) {
            if (text[i] == '\n') return i + 1
            i--
        }
        return 0
    }

    // The rules of UAX #29 section 3.1.1, in order; the first that applies decides. One branch per
    // rule reads against the specification line by line, which a split would not.
    @Suppress("CyclomaticComplexMethod")
    private fun isBreak(previous: Int, next: Int, regionalIndicators: Int, emoji: Int, linker: Boolean): Boolean {
        val before = previous and GCB_MASK
        val after = next and GCB_MASK
        return when {
            before == GCB_CR && after == GCB_LF -> false // GB3
            before == GCB_CR || before == GCB_LF || before == GCB_CONTROL -> true // GB4
            after == GCB_CR || after == GCB_LF || after == GCB_CONTROL -> true // GB5
            before == GCB_L && (after == GCB_L || after == GCB_V || after == GCB_LV || after == GCB_LVT) -> false // GB6
            (before == GCB_LV || before == GCB_V) && (after == GCB_V || after == GCB_T) -> false // GB7
            (before == GCB_LVT || before == GCB_T) && after == GCB_T -> false // GB8
            after == GCB_EXTEND || after == GCB_ZWJ -> false // GB9
            after == GCB_SPACING_MARK -> false // GB9a
            before == GCB_PREPEND -> false // GB9b
            linker && next and INCB_MASK == INCB_CONSONANT -> false // GB9c
            emoji == EMOJI_JOINED && next and EXTENDED_PICTOGRAPHIC != 0 -> false // GB11
            before == GCB_REGIONAL_INDICATOR && after == GCB_REGIONAL_INDICATOR -> regionalIndicators % 2 == 0 // GB12, GB13
            else -> true // GB999
        }
    }

    /** Tracks GB11's `ExtPict Extend* ZWJ` prefix through one more code point. */
    private fun nextEmojiState(state: Int, next: Int): Int = when {
        next and EXTENDED_PICTOGRAPHIC != 0 -> EMOJI_PICTOGRAPH
        state == EMOJI_PICTOGRAPH && next and GCB_MASK == GCB_EXTEND -> EMOJI_PICTOGRAPH
        state == EMOJI_PICTOGRAPH && next and GCB_MASK == GCB_ZWJ -> EMOJI_JOINED
        else -> EMOJI_NONE
    }

    private const val EMOJI_NONE = 0
    private const val EMOJI_PICTOGRAPH = 1
    private const val EMOJI_JOINED = 2
}

/** [codePoint]'s break properties: Grapheme_Cluster_Break, Extended_Pictographic and Indic_Conjunct_Break bits. */
internal fun propertiesOf(codePoint: Int): Int {
    if (codePoint in 0x20..0x7E) return GCB_OTHER // Printable ASCII, the common case.
    val ranges = GraphemeBreakTable.ranges
    var low = 0
    var high = ranges.size - 1
    val key = codePoint shl GRAPHEME_VALUE_BITS or GRAPHEME_VALUE_MASK
    while (low < high) {
        val middle = (low + high + 1) ushr 1
        if (ranges[middle] <= key) low = middle else high = middle - 1
    }
    val value = ranges[low] and GRAPHEME_VALUE_MASK
    // The table stores every Hangul syllable as LV; the ones with a trailing consonant are LVT.
    return if (value and GCB_MASK == GCB_LV && (codePoint - HANGUL_FIRST) % HANGUL_T_COUNT != 0) {
        (value and GCB_MASK.inv()) or GCB_LVT
    } else {
        value
    }
}

/** Bits per table entry that hold properties; the code point sits above them. */
internal const val GRAPHEME_VALUE_BITS = 7
private const val GRAPHEME_VALUE_MASK = (1 shl GRAPHEME_VALUE_BITS) - 1

// Grapheme_Cluster_Break values, as the generator numbers them.
internal const val GCB_MASK = 0xF
internal const val GCB_OTHER = 0
internal const val GCB_CR = 1
internal const val GCB_LF = 2
internal const val GCB_CONTROL = 3
internal const val GCB_EXTEND = 4
internal const val GCB_ZWJ = 5
internal const val GCB_REGIONAL_INDICATOR = 6
internal const val GCB_PREPEND = 7
internal const val GCB_SPACING_MARK = 8
internal const val GCB_L = 9
internal const val GCB_V = 10
internal const val GCB_T = 11
internal const val GCB_LV = 12
internal const val GCB_LVT = 13

internal const val EXTENDED_PICTOGRAPHIC = 1 shl 4

// Indic_Conjunct_Break values.
internal const val INCB_MASK = 3 shl 5
internal const val INCB_LINKER = 1 shl 5
internal const val INCB_CONSONANT = 2 shl 5
internal const val INCB_EXTEND = 3 shl 5

private const val HANGUL_FIRST = 0xAC00
private const val HANGUL_T_COUNT = 28
