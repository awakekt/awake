/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.text.unicode.GraphemeClusters

/**
 * [text] as one line no wider than [maxWidthPx] at [glyphPx]: itself when it fits, otherwise its
 * longest start that fits with [ellipsis] after it.
 *
 * The cut falls between grapheme clusters ([GraphemeClusters]), so a flag, a skin-toned or ZWJ
 * emoji, or a letter with its combining marks is kept whole or left out, never split into code
 * points the font draws as stand-ins. Spaces before the ellipsis are dropped. When not even the
 * ellipsis fits, the result is empty. Widths are [measureTextWidth]'s, so a label sized with that
 * holds the result.
 *
 * @param text One line of text; a line break in it is measured as nothing, as [measureTextWidth] does.
 * @param maxWidthPx The widest the result may measure, in pixels.
 * @param glyphPx The font size in pixels.
 * @param weight The face to measure with.
 * @param ellipsis What marks the cut: U+2026 HORIZONTAL ELLIPSIS unless told otherwise.
 * @param alwaysEllipsize When true, append [ellipsis] (and trim [text] if necessary) even if [text] fits within [maxWidthPx].
 */
fun UiFont.ellipsize(
    text: String,
    maxWidthPx: Float,
    glyphPx: Float,
    weight: FontWeight = FontWeight.Normal,
    ellipsis: String = "\u2026",
    alwaysEllipsize: Boolean = false,
): String {
    if (!alwaysEllipsize && measureTextWidth(text, glyphPx, weight) <= maxWidthPx) return text
    val ellipsisWidth = measureTextWidth(ellipsis, glyphPx, weight)
    return if (ellipsisWidth > maxWidthPx) {
        ""
    } else {
        text.substring(0, cutBefore(text, maxWidthPx, glyphPx, weight, ellipsis)).trimEnd() + ellipsis
    }
}

/** Where [UiFont.ellipsize] cuts [text]: the last cluster boundary whose start, with [ellipsis] after it, fits. */
private fun UiFont.cutBefore(
    text: String,
    maxWidthPx: Float,
    glyphPx: Float,
    weight: FontWeight,
    ellipsis: String,
): Int {
    val ellipsisWidth = measureTextWidth(ellipsis, glyphPx, weight)
    var pen = 0f
    var fits = 0
    var start = 0
    while (start < text.length) {
        val end = GraphemeClusters.nextBoundary(text, start)
        pen += clusterAdvance(text, start, end, glyphPx, weight)
        if (pen + ellipsisWidth > maxWidthPx) break
        fits = end
        start = end
    }
    // Pen sums advances, which can be narrower than the trailing glyph's ink: shorten until the
    // measured result fits too.
    var cut = fits
    while (cut > 0 && measureTextWidth(text.substring(0, cut).trimEnd() + ellipsis, glyphPx, weight) > maxWidthPx) {
        cut = GraphemeClusters.previousBoundary(text, cut)
    }
    return cut
}
