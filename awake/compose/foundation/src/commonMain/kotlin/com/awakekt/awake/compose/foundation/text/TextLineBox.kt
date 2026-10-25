/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.core.text.font.UiFont
import kotlin.math.floor

/**
 * The vertical rule every line of a text run follows: how far apart lines are, and where the baseline
 * sits in each. Measuring, painting, the caret and hit testing all read it here.
 *
 * The rule is CSS's, as the pinned Chromium oracle measures it (`tools/fonts-tooling/layout`):
 *
 * 1. The font's line ascent, descent and gap ([UiFont.lineAscentEm] and its siblings) are each
 *    rounded to a whole density-independent pixel, as a browser rounds them to whole CSS pixels. Their
 *    sum is `line-height: normal`: Roboto at 14 px is 13 + 4 = 17, not its exact 16.8. The metrics are
 *    the ones DirectWrite reads, as Chromium on Windows (the oracle's platform) does; a browser on
 *    macOS or Linux reads the `hhea` line instead, 16 px for the same text.
 * 2. An authored [com.awakekt.awake.core.text.theme.TextStyle.lineHeight] replaces that sum, tighter or
 *    looser, on one line or many. The difference from the rounded ascent plus descent is the leading,
 *    split as half-leading above and below the glyphs; the half above is floored to a whole pixel, as
 *    Blink floors it. A tight line height makes the half-leading negative, and glyphs then overlap the
 *    neighbouring lines' boxes, as they do in a browser.
 * 3. The line advance is rounded once per run to a whole device pixel and the baseline offset with
 *    it. Every line is then the same whole number of pixels below the one before, and when the first
 *    baseline lands on the pixel grid, every baseline does. A browser instead advances by a fractional
 *    line height at a fractional density and snaps each baseline, so its gaps alternate; this keeps
 *    them equal, and differs from the browser by under half a pixel per line.
 */
internal object TextLineBox {

    /**
     * The distance from one line's top to the next, in whole device pixels, never less than one, for
     * text drawn from [font] at [glyphPx] device pixels on a [density] display. [lineHeightPx] is an
     * authored line height in device pixels, or null for the font's own.
     */
    fun advancePx(font: UiFont, glyphPx: Float, density: Float, lineHeightPx: Float?): Float {
        val requestedPx = lineHeightPx ?: (normalLineDp(font, glyphPx / density) * density)
        return wholePixel(requestedPx).coerceAtLeast(1f)
    }

    /** The first line's baseline below its top, in whole device pixels; see [advancePx] for [lineHeightPx]. */
    fun baselinePx(font: UiFont, glyphPx: Float, density: Float, lineHeightPx: Float?): Float {
        val emDp = glyphPx / density
        val ascentDp = wholePixel(font.lineAscentEm * emDp)
        val contentDp = ascentDp + wholePixel(font.lineDescentEm * emDp)
        val lineDp = lineHeightPx?.let { it / density } ?: normalLineDp(font, emDp)
        return wholePixel((ascentDp + floor((lineDp - contentDp) / 2f)) * density)
    }

    /** `line-height: normal`, in whole density-independent pixels. */
    private fun normalLineDp(font: UiFont, emDp: Float): Float =
        wholePixel(font.lineAscentEm * emDp) + wholePixel(font.lineDescentEm * emDp) + wholePixel(font.lineGapEm * emDp)

    /** [value] rounded half up, as Skia rounds a font's metrics. */
    private fun wholePixel(value: Float): Float = floor(value + 0.5f)
}
