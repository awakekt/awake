/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

/**
 * Splits a run into the lines it actually occupies.
 *
 * One function rather than one in the measure policy and another in the painter, because those two
 * disagreeing is invisible: the box is the right height and the words inside it are on the wrong
 * lines. `TextRun`'s own header makes the same argument about the caret.
 *
 * Greedy and space-only. A word longer than the available width overflows rather than being split,
 * which is what a browser does with `overflow-wrap: normal` -- the default every shadcn component
 * inherits. Real line breaking wants a Unicode break iterator; this covers the Latin text the
 * component set is written in and says so rather than pretending to more.
 */
internal fun breakIntoLines(
    text: String,
    maxWidthPx: Float,
    widthOf: (String) -> Float,
): List<String> = breakIntoLineLayouts(text, maxWidthPx, widthOf).map(TextLineLayout::text)

/** One painted visual line and the matching source range, excluding a trailing line break. */
internal data class TextLineLayout(
    val text: String,
    val start: Int,
    val endExclusive: Int,
)

/**
 * Splits text exactly as [breakIntoLines] does while retaining where each visual line came from.
 *
 * Caret placement, hit testing and painting must all use this one result. Reconstructing ranges by
 * searching the rendered line text fails as soon as two words repeat or a hard line is empty.
 */
internal fun breakIntoLineLayouts(
    text: String,
    maxWidthPx: Float,
    widthOf: (String) -> Float,
): List<TextLineLayout> {
    val hardLines = buildList {
        var start = 0
        while (true) {
            val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
            add(TextLineLayout(text.substring(start, end), start, end))
            if (end == text.length) break
            start = end + 1
        }
    }
    if (maxWidthPx <= 0f || maxWidthPx == Float.POSITIVE_INFINITY) return hardLines

    val out = mutableListOf<TextLineLayout>()
    for (hardLine in hardLines) {
        if (hardLine.text.isEmpty() || fits(widthOf(hardLine.text), maxWidthPx)) {
            out += hardLine
            continue
        }
        var current = StringBuilder()
        var currentStart = hardLine.start
        var wordStart = hardLine.start
        while (wordStart <= hardLine.endExclusive) {
            val wordEnd = text.indexOf(' ', wordStart).let { found ->
                if (found < 0 || found > hardLine.endExclusive) hardLine.endExclusive else found
            }
            val word = text.substring(wordStart, wordEnd)
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (current.isNotEmpty() && !fits(widthOf(candidate), maxWidthPx)) {
                out += TextLineLayout(current.toString(), currentStart, wordStart - 1)
                current = StringBuilder(word)
                currentStart = wordStart
            } else {
                current = StringBuilder(candidate)
            }
            if (wordEnd == hardLine.endExclusive) break
            wordStart = wordEnd + 1
        }
        out += TextLineLayout(current.toString(), currentStart, hardLine.endExclusive)
    }
    return out
}

/**
 * Whether a line of [widthPx] fits in [maxWidthPx], judged on the whole pixel the line is sized to.
 *
 * A measured line is rounded to the nearest pixel, and so is the intrinsic width a parent plans
 * with. Comparing the unrounded width instead wraps the last word of a label measured at exactly
 * its own intrinsic width whenever the line rounded down: a two-word label in a FlexBox, sized to
 * its maximum intrinsic width, came back two lines tall.
 */
private fun fits(widthPx: Float, maxWidthPx: Float): Boolean = widthPx < maxWidthPx + 0.5f
