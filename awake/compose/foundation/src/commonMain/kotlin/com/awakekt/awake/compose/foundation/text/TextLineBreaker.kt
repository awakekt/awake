/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.core.text.unicode.GraphemeClusters
import com.awakekt.awake.core.text.unicode.LineBreaks
import com.awakekt.awake.core.text.unicode.WordBreak
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Splits a run into the lines it actually occupies.
 *
 * One function rather than one in the measure policy and another in the painter, because those two
 * disagreeing is invisible: the box is the right height and the words inside it are on the wrong
 * lines. `TextRun`'s own header makes the same argument about the caret.
 *
 * Greedy over [LineBreaks] (a UAX #14 subset covering spaces, hyphens, CJK ideographs, kana,
 * Hangul and kinsoku punctuation), with [WordBreak.KeepAll] keeping Korean and unspaced letter runs
 * whole. When a single unbreakable segment at the start of a line still exceeds [maxWidthPx], it
 * breaks between [GraphemeClusters] as a last resort (`overflow-wrap: anywhere`), always advancing
 * by at least one grapheme cluster.
 */
@Suppress("LongParameterList")
internal fun breakIntoLines(
    text: String,
    maxWidthPx: Float,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    wordBreak: WordBreak = WordBreak.Normal,
    ellipsize: ((text: String, maxWidthPx: Float, alwaysEllipsize: Boolean) -> String)? = null,
    widthOf: (String) -> Float,
): List<String> = breakIntoLineLayouts(
    text = text,
    maxWidthPx = maxWidthPx,
    softWrap = softWrap,
    maxLines = maxLines,
    overflow = overflow,
    wordBreak = wordBreak,
    ellipsize = ellipsize,
    widthOf = widthOf,
).map(TextLineLayout::text)

/** Overload for callers specifying only maxWidthPx and widthOf. */
internal fun breakIntoLines(
    text: String,
    maxWidthPx: Float,
    widthOf: (String) -> Float,
): List<String> = breakIntoLines(
    text = text,
    maxWidthPx = maxWidthPx,
    softWrap = true,
    maxLines = Int.MAX_VALUE,
    overflow = TextOverflow.Clip,
    wordBreak = WordBreak.Normal,
    ellipsize = null,
    widthOf = widthOf,
)

/** One painted visual line and the matching source range, excluding a trailing line break. */
internal data class TextLineLayout(
    val text: String,
    val start: Int,
    val endExclusive: Int,
)

/** Overload for callers specifying only maxWidthPx and widthOf. */
internal fun breakIntoLineLayouts(
    text: String,
    maxWidthPx: Float,
    widthOf: (String) -> Float,
): List<TextLineLayout> = breakIntoLineLayouts(
    text = text,
    maxWidthPx = maxWidthPx,
    softWrap = true,
    maxLines = Int.MAX_VALUE,
    overflow = TextOverflow.Clip,
    wordBreak = WordBreak.Normal,
    ellipsize = null,
    widthOf = widthOf,
)

/**
 * Splits text exactly as [breakIntoLines] does while retaining where each visual line came from.
 *
 * Caret placement, hit testing and painting must all use this one result. Reconstructing ranges by
 * searching the rendered line text fails as soon as two words repeat or a hard line is empty.
 */
@Suppress("LongParameterList")
internal fun breakIntoLineLayouts(
    text: String,
    maxWidthPx: Float,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    wordBreak: WordBreak = WordBreak.Normal,
    ellipsize: ((text: String, maxWidthPx: Float, alwaysEllipsize: Boolean) -> String)? = null,
    widthOf: (String) -> Float,
): List<TextLineLayout> {
    val breaker = TextBreaker(text, maxWidthPx, maxLines, overflow, wordBreak, ellipsize, widthOf)
    return breaker.breakLines(softWrap)
}

@Suppress("LongParameterList")
private class TextBreaker(
    private val text: String,
    private val maxWidthPx: Float,
    maxLines: Int,
    private val overflow: TextOverflow,
    private val wordBreak: WordBreak,
    private val ellipsize: ((String, Float, Boolean) -> String)?,
    private val widthOf: (String) -> Float,
) {
    private val limit: Int = maxLines.coerceAtLeast(1)
    private val isBounded: Boolean = maxWidthPx > 0f && maxWidthPx != Float.POSITIVE_INFINITY

    fun breakLines(softWrap: Boolean): List<TextLineLayout> {
        val hardLines = splitHardLines()
        if (!softWrap || !isBounded) {
            return breakUnwrapped(hardLines)
        }
        val out = mutableListOf<TextLineLayout>()
        for (i in hardLines.indices) {
            if (out.size >= limit) break
            processWrappedHardLine(hardLines[i], i == hardLines.lastIndex, out)
        }
        return out
    }

    private fun splitHardLines(): List<TextLineLayout> = buildList {
        var start = 0
        while (true) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            add(TextLineLayout(text.substring(start, end), start, end))
            if (end == text.length) break
            start = end + 1
        }
    }

    private fun breakUnwrapped(hardLines: List<TextLineLayout>): List<TextLineLayout> {
        val count = minOf(hardLines.size, limit)
        val out = ArrayList<TextLineLayout>(count)
        for (i in 0 until count) {
            val line = hardLines[i]
            val isLast = i == limit - 1
            val hasMore = isLast && hardLines.size > limit
            val exceeds = isBounded && !fitsAsSized(widthOf(line.text), maxWidthPx)
            val lineText = if (shouldEllipsize(hasMore, exceeds)) {
                applyEllipsis(line.text, hasMore)
            } else {
                line.text
            }
            out.add(TextLineLayout(lineText, line.start, line.endExclusive))
        }
        return out
    }

    private fun processWrappedHardLine(
        hardLine: TextLineLayout,
        isLastHardLine: Boolean,
        out: MutableList<TextLineLayout>,
    ) {
        if (hardLine.text.isEmpty() || fitsAsSized(widthOf(hardLine.text), maxWidthPx)) {
            val isLast = out.size == limit - 1
            val hasMore = isLast && !isLastHardLine
            val lineText = if (shouldEllipsize(hasMore, false)) {
                applyEllipsis(hardLine.text, true)
            } else {
                hardLine.text
            }
            out += TextLineLayout(lineText, hardLine.start, hardLine.endExclusive)
            return
        }
        wrapSegmentsIntoLines(hardLine, isLastHardLine, out)
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun wrapSegmentsIntoLines(
        hardLine: TextLineLayout,
        isLastHardLine: Boolean,
        out: MutableList<TextLineLayout>,
    ) {
        var lineStart = hardLine.start
        var lineEnd = hardLine.start
        var lineVisibleEnd = hardLine.start
        var segStart = hardLine.start
        while (segStart < hardLine.endExclusive) {
            val segEnd = LineBreaks.nextOpportunity(text, segStart, hardLine.endExclusive, wordBreak)
            val candidateVisibleEnd = trimTrailingSpaces(text, lineStart, segEnd)
            val candidateFits = candidateVisibleEnd == lineStart ||
                fits(widthOf(text.substring(lineStart, candidateVisibleEnd)), maxWidthPx)
            if (candidateFits) {
                lineEnd = segEnd
                lineVisibleEnd = candidateVisibleEnd
                segStart = segEnd
            } else if (lineEnd > lineStart) {
                if (out.size == limit - 1) {
                    out += makeFinalEllipsizedLine(hardLine, lineStart, segEnd, isLastHardLine)
                    return
                }
                out += TextLineLayout(text.substring(lineStart, lineVisibleEnd), lineStart, lineVisibleEnd)
                lineStart = lineEnd
                lineVisibleEnd = lineStart
            } else {
                if (out.size == limit - 1) {
                    out += makeFinalEllipsizedLine(hardLine, lineStart, segEnd, isLastHardLine)
                    return
                }
                val clusterCut = lastFittingClusterEnd(lineStart, candidateVisibleEnd)
                out += TextLineLayout(text.substring(lineStart, clusterCut), lineStart, clusterCut)
                val nextStart = if (clusterCut >= candidateVisibleEnd) segEnd else clusterCut
                lineStart = nextStart
                lineEnd = nextStart
                lineVisibleEnd = nextStart
                segStart = nextStart
            }
        }
        if (lineStart < hardLine.endExclusive && out.size < limit) {
            emitRemainingWrapped(
                currentText = text.substring(lineStart, hardLine.endExclusive),
                currentStart = lineStart,
                endExclusive = hardLine.endExclusive,
                isLastHardLine = isLastHardLine,
                out = out,
            )
        }
    }

    private fun lastFittingClusterEnd(start: Int, endExclusive: Int): Int {
        val firstEnd = GraphemeClusters.nextBoundary(text, start).coerceAtMost(endExclusive)
        var best = firstEnd
        var cursor = firstEnd
        while (cursor < endExclusive) {
            val next = GraphemeClusters.nextBoundary(text, cursor).coerceAtMost(endExclusive)
            if (!fits(widthOf(text.substring(start, next)), maxWidthPx)) break
            best = next
            cursor = next
        }
        return best
    }

    private fun makeFinalEllipsizedLine(
        hardLine: TextLineLayout,
        currentStart: Int,
        segEnd: Int,
        isLastHardLine: Boolean,
    ): TextLineLayout {
        val remaining = text.substring(currentStart, hardLine.endExclusive)
        val hasMore = !isLastHardLine || segEnd < hardLine.endExclusive || !fits(widthOf(remaining), maxWidthPx)
        val lineText = if (shouldEllipsize(hasMore, false)) {
            applyEllipsis(remaining, hasMore)
        } else {
            remaining
        }
        return TextLineLayout(lineText, currentStart, hardLine.endExclusive)
    }

    private fun emitRemainingWrapped(
        currentText: String,
        currentStart: Int,
        endExclusive: Int,
        isLastHardLine: Boolean,
        out: MutableList<TextLineLayout>,
    ) {
        if (currentText.isEmpty() || out.size >= limit) return
        val isLast = out.size == limit - 1
        val hasMore = isLast && !isLastHardLine
        val exceeds = !fits(widthOf(currentText), maxWidthPx)
        val lineText = if (shouldEllipsize(hasMore, exceeds)) {
            applyEllipsis(currentText, hasMore)
        } else {
            currentText
        }
        out += TextLineLayout(lineText, currentStart, endExclusive)
    }

    private fun shouldEllipsize(hasMore: Boolean, exceeds: Boolean): Boolean =
        overflow == TextOverflow.Ellipsis && ellipsize != null && (hasMore || exceeds)

    private fun applyEllipsis(text: String, hasMore: Boolean): String =
        ellipsize?.invoke(text, maxWidthPx, hasMore) ?: text
}

/**
 * Whether a line that wrapping made, one of the several a hard line breaks into, fits in [maxWidthPx], as
 * Chromium's line breaker decides it.
 *
 * A line fits when its width, snapped up to a `LayoutUnit` (1/64 px), is no wider than the available
 * width snapped down to one. There is no allowance past the limit: a line 110.3 px wide does not fit in
 * 110 px, and a browser wraps it there (#1021).
 */
private fun fits(widthPx: Float, maxWidthPx: Float): Boolean =
    ceil(widthPx * LAYOUT_UNITS_PER_PX) <= floor(maxWidthPx * LAYOUT_UNITS_PER_PX)

/**
 * Whether a whole hard line of [widthPx] fits in [maxWidthPx], judged on the whole pixel it is sized to.
 *
 * Placeables are whole pixels, so a box sized to a line (see [lineBoxWidthPx]) is the line's width rounded
 * to the nearest pixel, up to half a pixel narrower than the line. Where a browser's box is as fractional
 * as its text, this one is not, and text laid out at its own measured width must stay on the one line it
 * measured. Only a line nothing has wrapped is judged this way: wrapping a paragraph is the browser's rule.
 */
private fun fitsAsSized(widthPx: Float, maxWidthPx: Float): Boolean = widthPx < maxWidthPx + 0.5f

/**
 * The width of the box that holds a line of [widthPx]: the nearest whole pixel for a whole hard line, which
 * [fitsAsSized] admits at that width, and the next whole pixel up for a line wrapping made, which [fits]
 * admits only if it is no wider than the box. Either way, text laid out at the width it measured to keeps
 * its lines.
 */
internal fun lineBoxWidthPx(widthPx: Float, wholeHardLine: Boolean): Int =
    if (wholeHardLine) floor(widthPx + 0.5f).toInt() else ceil(widthPx).toInt()

/** The width of the box that holds this line of [source], measured by [widthOf] (see [lineBoxWidthPx]). */
internal fun TextLineLayout.boxWidthPx(source: String, widthOf: (String) -> Float): Int {
    val whole = (start == 0 || source[start - 1] == '\n') && (endExclusive == source.length || source[endExclusive] == '\n')
    return lineBoxWidthPx(widthOf(text), whole)
}

/** Trims trailing ASCII `' '` separator spaces from `[start, endExclusive)` when soft-wrapping. */
internal fun trimTrailingSpaces(text: String, start: Int, endExclusive: Int): Int {
    var end = endExclusive
    while (end > start && text[end - 1] == ' ') end--
    return end
}

/** Chromium's `LayoutUnit` is a fixed-point number with six fractional bits. */
private const val LAYOUT_UNITS_PER_PX = 64f
