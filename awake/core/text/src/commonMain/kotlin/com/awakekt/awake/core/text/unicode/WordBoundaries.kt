/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.unicode

/**
 * Word boundaries for caret movement (Ctrl/Option+Arrow) and double-click word selection, built on
 * the same [GraphemeClusters] and script/punctuation classes as [LineBreaks].
 *
 * - Contiguous Latin/alphabetic/numeric/`'_'` clusters stay one word (`"Harbor"`, `"quay_01"`).
 * - Contiguous Hangul clusters stay one word (`"마을의"`), separating at spaces and punctuation.
 * - Contiguous Katakana clusters (including prolonged sound marks `ー` and `ｰ`) stay one word
 *   (`"ランタン"`, `"コーヒー"`), and contiguous Hiragana clusters stay one run (`"です"`).
 * - Han ideographs and emoji clusters step one grapheme cluster at a time, separating cleanly from
 *   adjacent Latin, kana, Hangul and punctuation without spaces (`"Harbor港口Town"` -> `"Harbor"`,
 *   `"港"`, `"口"`, `"Town"`).
 * - Caret word jumps skip intervening whitespace and punctuation to stop at the edge of the word.
 */
object WordBoundaries {

    /**
     * Where `Ctrl+Left` (`Option+Left` on macOS) moves a caret from [index] in [text]: skips any
     * preceding whitespace or punctuation and stops at the start of the preceding word unit, or `0`
     * when none precedes it. Never stops inside a grapheme cluster or crosses a newline unless
     * [index] starts right after one.
     */
    @Suppress("ReturnCount")
    fun previousWordBoundary(text: CharSequence, index: Int): Int {
        val clamped = GraphemeClusters.floorBoundary(text, index.coerceIn(0, text.length))
        if (clamped <= 0) return 0

        var cursor = GraphemeClusters.previousBoundary(text, clamped)
        val firstCp = CodePoints.at(text, cursor)
        if (isHardBreakCodePoint(firstCp)) return cursor

        // Skip whitespace and punctuation immediately before the caret, stopping before a newline.
        var kind = wordKindOf(firstCp)
        while (kind == WK_SPACE || kind == WK_PUNCT) {
            if (cursor == 0) return 0
            val prev = GraphemeClusters.previousBoundary(text, cursor)
            val prevCp = CodePoints.at(text, prev)
            if (isHardBreakCodePoint(prevCp)) return cursor
            cursor = prev
            kind = wordKindOf(prevCp)
        }

        // Single-cluster ideograph/emoji units stop after one cluster; script runs step to run start.
        if (kind == WK_IDEOGRAPH) return cursor
        while (cursor > 0) {
            val prev = GraphemeClusters.previousBoundary(text, cursor)
            val prevCp = CodePoints.at(text, prev)
            if (isHardBreakCodePoint(prevCp) || wordKindOf(prevCp) != kind) break
            cursor = prev
        }
        return cursor
    }

    /**
     * Where `Ctrl+Right` (`Option+Right` on macOS) moves a caret from [index] in [text]: skips any
     * leading whitespace or punctuation and stops at the end of the next word unit, or
     * `text.length` when none follows it. Never stops inside a grapheme cluster.
     */
    @Suppress("ReturnCount")
    fun nextWordBoundary(text: CharSequence, index: Int): Int {
        val length = text.length
        var cursor = GraphemeClusters.floorBoundary(text, index.coerceIn(0, length))
        if (cursor >= length) return length

        val firstCp = CodePoints.at(text, cursor)
        if (isHardBreakCodePoint(firstCp)) return GraphemeClusters.nextBoundary(text, cursor)

        // Skip whitespace and punctuation at or after the caret, stopping at a newline.
        var kind = wordKindOf(firstCp)
        while (kind == WK_SPACE || kind == WK_PUNCT) {
            cursor = GraphemeClusters.nextBoundary(text, cursor)
            if (cursor >= length) return length
            val cp = CodePoints.at(text, cursor)
            if (isHardBreakCodePoint(cp)) return cursor
            kind = wordKindOf(cp)
        }

        val afterFirst = GraphemeClusters.nextBoundary(text, cursor)
        if (kind == WK_IDEOGRAPH) return afterFirst
        cursor = afterFirst
        while (cursor < length) {
            val cp = CodePoints.at(text, cursor)
            if (isHardBreakCodePoint(cp) || wordKindOf(cp) != kind) break
            cursor = GraphemeClusters.nextBoundary(text, cursor)
        }
        return cursor
    }

    /**
     * The `[start, endExclusive)` `Char` range selected by double-clicking at [index] in [text], as
     * an `IntRange` of `start until endExclusive` (`IntRange.EMPTY` with `first = 0` when [text] is
     * empty; callers read [wordStart] and [wordEnd] for zero-allocation pair bounds).
     */
    fun wordRangeAt(text: CharSequence, index: Int): IntRange =
        wordStart(text, index) until wordEnd(text, index)

    /** Start index (inclusive) of the word or token double-clicked at [index] in [text]. */
    fun wordStart(text: CharSequence, index: Int): Int {
        if (text.isEmpty()) return 0
        val anchor = anchorClusterStart(text, index)
        val cp = CodePoints.at(text, anchor)
        val kind = wordKindOf(cp)
        var start = anchor
        if (!isSingleClusterSelection(cp, kind)) {
            while (start > 0) {
                val prev = GraphemeClusters.previousBoundary(text, start)
                val prevCp = CodePoints.at(text, prev)
                if (isHardBreakCodePoint(prevCp) || wordKindOf(prevCp) != kind) break
                start = prev
            }
        }
        return start
    }

    /** End index (exclusive) of the word or token double-clicked at [index] in [text]. */
    fun wordEnd(text: CharSequence, index: Int): Int {
        if (text.isEmpty()) return 0
        val anchor = anchorClusterStart(text, index)
        val cp = CodePoints.at(text, anchor)
        val kind = wordKindOf(cp)
        var end = if (isHardBreakCodePoint(cp)) anchor else GraphemeClusters.nextBoundary(text, anchor)
        if (!isSingleClusterSelection(cp, kind)) {
            while (end < text.length) {
                val nextCp = CodePoints.at(text, end)
                if (isHardBreakCodePoint(nextCp) || wordKindOf(nextCp) != kind) break
                end = GraphemeClusters.nextBoundary(text, end)
            }
        }
        return end
    }

    private fun isSingleClusterSelection(cp: Int, kind: Int): Boolean =
        isHardBreakCodePoint(cp) || kind == WK_IDEOGRAPH || kind == WK_PUNCT

    /**
     * Picks the cluster clicked at [index]. When hit-testing snaps a click on the trailing half of
     * a line's last character to the end of the text or a newline, select that preceding cluster.
     */
    @Suppress("ReturnCount")
    private fun anchorClusterStart(text: CharSequence, index: Int): Int {
        val length = text.length
        val bounded = index.coerceIn(0, length)
        if (bounded == length) return GraphemeClusters.previousBoundary(text, length)
        val floor = GraphemeClusters.floorBoundary(text, bounded)
        if (floor == bounded && isHardBreakCodePoint(CodePoints.at(text, floor)) && floor > 0) {
            val prev = GraphemeClusters.previousBoundary(text, floor)
            if (!isHardBreakCodePoint(CodePoints.at(text, prev))) return prev
        }
        return floor
    }
}

private const val WK_SPACE = 0
private const val WK_PUNCT = 1
private const val WK_ALNUM = 2
private const val WK_HANGUL = 3
private const val WK_HIRAGANA = 4
private const val WK_KATAKANA = 5
private const val WK_IDEOGRAPH = 6

@Suppress("CyclomaticComplexMethod", "MagicNumber", "ReturnCount")
private fun wordKindOf(cp: Int): Int {
    if (isWordSpace(cp)) return WK_SPACE
    if (cp == '_'.code) return WK_ALNUM
    if (isHiraganaWordChar(cp)) return WK_HIRAGANA
    if (isKatakanaWordChar(cp)) return WK_KATAKANA
    return when (lineBreakClassOf(cp)) {
        LB_HANGUL -> WK_HANGUL
        LB_ID -> WK_IDEOGRAPH
        LB_AL, LB_NU -> if (isAsciiPunctuation(cp)) WK_PUNCT else WK_ALNUM
        else -> WK_PUNCT
    }
}

@Suppress("MagicNumber")
private fun isWordSpace(cp: Int): Boolean = when (cp) {
    0x20, 0x09, 0x00A0, 0x202F, 0x3000 -> true
    else -> false
}

@Suppress("MagicNumber")
private fun isHiraganaWordChar(cp: Int): Boolean = cp in 0x3040..0x309F && cp !in 0x309B..0x309E

@Suppress("MagicNumber")
private fun isKatakanaWordChar(cp: Int): Boolean = when (cp) {
    in 0x30A1..0x30FA, 0x30FC, in 0x31F0..0x31FF, in 0xFF66..0xFF9D, 0xFF70 -> true
    else -> false
}

@Suppress("MagicNumber")
private fun isAsciiPunctuation(cp: Int): Boolean =
    cp in 0x21..0x2F || cp in 0x3A..0x40 || cp in 0x5B..0x60 || cp in 0x7B..0x7E
