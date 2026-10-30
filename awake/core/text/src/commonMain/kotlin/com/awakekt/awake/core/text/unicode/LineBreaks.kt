/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

/**
 * Soft line-break opportunities for plain and mixed-script text, implementing the subset of
 * Unicode Line Breaking (UAX #14) needed for Latin, Chinese, Japanese and Korean paragraphs.
 *
 * Every boundary stepped or accepted here is also a [GraphemeClusters] boundary (UAX #29):
 * combining marks, variation selectors, Hangul jamo sequences, flags and ZWJ emoji clusters stay
 * whole and inherit the line-break class of their leading code point (LB9).
 *
 * Nothing here allocates, so measurement and painting passes can query it directly.
 */
object LineBreaks {

    /**
     * Whether a line spanning `[start, endExclusive)` in [text] may soft-break at [index] under
     * [wordBreak].
     *
     * Never breaks at or outside `[start, endExclusive)`, at a hard line break (`\r` or `\n`), or
     * inside a grapheme cluster.
     */
    @Suppress("ReturnCount")
    fun canBreakAt(
        text: CharSequence,
        index: Int,
        start: Int = 0,
        endExclusive: Int = text.length,
        wordBreak: WordBreak = WordBreak.Normal,
    ): Boolean {
        val from = start.coerceAtLeast(0)
        val limit = endExclusive.coerceAtMost(text.length)
        if (index <= from || index >= limit) return false
        if (!GraphemeClusters.isBoundary(text, index)) return false

        val prevStart = GraphemeClusters.previousBoundary(text, index)
        if (prevStart < from) return false
        val prevCp = CodePoints.at(text, prevStart)
        val nextCp = CodePoints.at(text, index)
        if (isHardBreakCodePoint(prevCp) || isHardBreakCodePoint(nextCp)) return false

        val prevClass = lineBreakClassOf(prevCp)
        val nextClass = lineBreakClassOf(nextCp)
        val afterOpen = isAfterOpeningPunctuation(text, from, prevStart, prevClass)
        val prevPrevClass = if (prevClass == LB_HY && nextClass == LB_NU && prevStart > from) {
            val ppStart = GraphemeClusters.previousBoundary(text, prevStart)
            val ppCp = if (ppStart >= from) CodePoints.at(text, ppStart) else -1
            if (ppCp >= 0 && !isHardBreakCodePoint(ppCp)) lineBreakClassOf(ppCp) else LB_NONE
        } else {
            LB_NONE
        }
        return canBreakBetween(prevPrevClass, prevClass, nextClass, afterOpen, wordBreak)
    }

    /**
     * The next soft-break opportunity strictly after [from] within `[from, endExclusive)`, or
     * [endExclusive] when the remainder of the range has none.
     *
     * When a break follows a run of ASCII spaces (`' '`), the returned index sits after the full
     * run of spaces so the caller can trim trailing separator spaces if the break is taken.
     */
    @Suppress("ReturnCount")
    fun nextOpportunity(
        text: CharSequence,
        from: Int,
        endExclusive: Int = text.length,
        wordBreak: WordBreak = WordBreak.Normal,
    ): Int {
        val limit = endExclusive.coerceAtMost(text.length)
        val start = from.coerceAtLeast(0)
        if (start >= limit) return limit

        var clusterStart = GraphemeClusters.floorBoundary(text, start)
        if (clusterStart < start) {
            clusterStart = GraphemeClusters.nextBoundary(text, clusterStart)
            if (clusterStart >= limit) return limit
        }

        val prevCp = CodePoints.at(text, clusterStart)
        if (isHardBreakCodePoint(prevCp)) return limit
        var prevClass = lineBreakClassOf(prevCp)
        var prevPrevClass = initialPrevPrevClass(text, clusterStart, prevClass)
        var afterOpen = isAfterOpeningPunctuation(text, 0, clusterStart, prevClass)

        var cursor = GraphemeClusters.nextBoundary(text, clusterStart).coerceAtMost(limit)
        while (cursor < limit) {
            val nextCp = CodePoints.at(text, cursor)
            if (isHardBreakCodePoint(nextCp)) return cursor
            val nextClass = lineBreakClassOf(nextCp)
            if (canBreakBetween(prevPrevClass, prevClass, nextClass, afterOpen, wordBreak)) {
                return cursor
            }
            afterOpen = nextAfterOpenState(afterOpen, nextClass)
            prevPrevClass = prevClass
            prevClass = nextClass
            cursor = GraphemeClusters.nextBoundary(text, cursor).coerceAtMost(limit)
        }
        return limit
    }
}

private fun initialPrevPrevClass(text: CharSequence, clusterStart: Int, prevClass: Int): Int {
    if (prevClass != LB_HY || clusterStart <= 0) return LB_NONE
    val ppStart = GraphemeClusters.previousBoundary(text, clusterStart)
    val ppCp = CodePoints.at(text, ppStart)
    return if (isHardBreakCodePoint(ppCp)) LB_NONE else lineBreakClassOf(ppCp)
}

private fun nextAfterOpenState(afterOpen: Boolean, nextClass: Int): Boolean = when {
    isOpeningPunctuation(nextClass) -> true
    nextClass == LB_SP -> afterOpen
    else -> false
}

private fun isAfterOpeningPunctuation(
    text: CharSequence,
    start: Int,
    prevStart: Int,
    prevClass: Int,
): Boolean {
    if (isOpeningPunctuation(prevClass)) return true
    var result = false
    var pos = prevStart
    var scanning = prevClass == LB_SP
    while (scanning && pos > start) {
        pos = GraphemeClusters.previousBoundary(text, pos)
        if (pos < start) {
            scanning = false
        } else {
            val cls = lineBreakClassOf(CodePoints.at(text, pos))
            if (isOpeningPunctuation(cls)) {
                result = true
                scanning = false
            } else if (cls != LB_SP) {
                scanning = false
            }
        }
    }
    return result
}

@Suppress("CyclomaticComplexMethod", "ReturnCount")
private fun canBreakBetween(
    prevPrev: Int,
    prev: Int,
    next: Int,
    afterOpen: Boolean,
    wordBreak: WordBreak,
): Boolean {
    if (afterOpen) return false // LB14: OP SP* ×
    if (next == LB_SP) return false // LB7: × SP
    if (prev == LB_SP) return true // LB18: SP ÷
    if (prev == LB_GL || next == LB_GL) return false // LB12, LB12a: × GL, GL ×
    if (prev == LB_QU || next == LB_QU) return false // LB19: × QU, QU ×
    if (isProhibitedLineStart(next)) return false // LB13 & kinsoku non-starters
    if (next == LB_IN) return prev == LB_BA || prev == LB_HY // LB22: × IN
    if (prev == LB_B2 && next == LB_B2) return false // LB17: B2 × B2 (——)
    if (next == LB_BA || next == LB_HY) return false // LB21: × BA, × HY
    if (prev == LB_HY) return !(next == LB_NU && !isWordUnit(prevPrev)) // LB25: -5 vs a-1
    if (prev == LB_BA || prev == LB_B2) return true
    if (next == LB_OP_NARROW) return !isAlphanumericLike(prev) // LB30: fn(x) vs 港(x)
    if (next == LB_OP_EA) return true
    if (prev == LB_CP) return !isAlphanumericLike(next) // LB30: (x)y vs (x)港
    if (isBreakAfterClosingOrNonStarter(prev)) return true
    if (prev == LB_IS || prev == LB_SY) return !isAlphanumericLike(next) // LB25, LB29, URLs
    if (wordBreak == WordBreak.KeepAll && isWordUnit(prev) && isWordUnit(next)) return false
    if (isAlphanumericLike(prev) && isAlphanumericLike(next)) return false // LB23, LB28
    return true
}

private fun isOpeningPunctuation(cls: Int): Boolean = cls == LB_OP_NARROW || cls == LB_OP_EA

private fun isAlphanumericLike(cls: Int): Boolean = cls == LB_AL || cls == LB_NU

private fun isBreakAfterClosingOrNonStarter(cls: Int): Boolean =
    cls == LB_CL || cls == LB_EX || cls == LB_NS || cls == LB_IN

private fun isProhibitedLineStart(cls: Int): Boolean =
    cls == LB_CL || cls == LB_CP || cls == LB_EX || cls == LB_IS ||
        cls == LB_SY || cls == LB_NS || cls == LB_CJ_PROLONGED
