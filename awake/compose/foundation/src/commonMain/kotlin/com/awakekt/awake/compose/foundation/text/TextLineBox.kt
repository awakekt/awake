/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import kotlin.math.ceil
import kotlin.math.floor

/**
 * The vertical rule every line of a text run follows: how far apart lines are, and where the baseline
 * sits in each. Measuring, painting, the caret and hit testing all read it here.
 *
 * The rule is CSS's, as the pinned Chromium oracle measures it (`tools/fonts-tooling/layout`):
 *
 * 1. The font's line ascent, descent and gap ([UiFont.lineAscentEm] and its siblings) are each
 *    rounded to a whole density-independent pixel, as a browser rounds them to whole CSS pixels, from
 *    the authored em (size times font scale) and not from the glyph size drawn, which is rounded to
 *    whole device pixels and at 125% is 14.4 dp for a 14 dp em. Their
 *    sum is `line-height: normal`: Roboto at 14 px is 13 + 4 = 17, not its exact 16.8. The metrics are
 *    the ones DirectWrite reads, as Chromium on Windows (the oracle's platform) does; a browser on
 *    macOS or Linux reads the `hhea` line instead, 16 px for the same text.
 * 2. An authored [com.awakekt.awake.core.text.theme.TextStyle.lineHeight] replaces that sum, tighter or
 *    looser, on one line or many. The difference from the rounded ascent plus descent is the leading,
 *    split as half-leading above and below the glyphs; the half above is floored to a whole pixel, as
 *    Blink floors it. A tight line height makes the half-leading negative, and glyphs then overlap the
 *    neighbouring lines' boxes, as they do in a browser.
 * 3. The line advance is rounded once per run to a whole device pixel and the baseline offset with
 *    it, half down, so a half pixel goes below the baseline where descenders need it. Every line is
 *    then the same whole number of pixels below the one before, and when the first baseline lands on
 *    the pixel grid, every baseline does. A browser instead advances by a fractional line height at a
 *    fractional density and snaps each baseline, so its gaps alternate; this keeps them equal, and
 *    differs from the browser by under half a pixel per line.
 * 4. The font's own line box never ends inside its ink. Rounded in density-independent pixels, the
 *    descent can fall short of the font's deepest descender once scaled to device pixels (9 sp at a
 *    0.75 density), and a glyph drawn from the distance field, as everything above the exact-size
 *    pixel-glyph limit is, antialiases up to [FIELD_FRINGE_PX] past its outline (24 sp at 125%). Where
 *    either reaches below the box, the advance grows by the whole pixels it needs; this is the one rule
 *    that reads the drawn glyph size. At density 1 it
 *    does not apply to the oracle's 11 to 18 px, whose box stays the browser's; field-drawn text such
 *    as 28 or 48 px gets a pixel more than Chromium gives it. An authored line height is the author's
 *    box and is never grown.
 */
internal object TextLineBox {

    /**
     * The distance from one line's top to the next, in whole device pixels, never less than one, for
     * text drawn from [font] on a [density] display. [emDp] is the authored em in density-independent
     * pixels, which rules 1 to 3 round from; [glyphPx] is the em as drawn, rounded to whole device
     * pixels, which only rule 4's ink check reads. [lineHeightPx] is an authored line height in device
     * pixels, or null for the font's own.
     */
    fun advancePx(
        font: UiFont,
        emDp: Float,
        glyphPx: Float,
        density: Float,
        lineHeightPx: Float?,
        forceDistanceField: Boolean = false,
    ): Float {
        if (lineHeightPx != null) return wholePixel(lineHeightPx).coerceAtLeast(1f)
        val normalPx = wholePixel(normalLineDp(font, emDp) * density)
        val inkBottomPx = baselinePx(font, emDp, density, null) + inkDescentPx(font, glyphPx, forceDistanceField)
        return maxOf(normalPx, inkBottomPx, 1f)
    }

    /** The first line's baseline below its top, in whole device pixels; see [advancePx] for the parameters. */
    fun baselinePx(font: UiFont, emDp: Float, density: Float, lineHeightPx: Float?): Float {
        val ascentDp = wholePixel(font.lineAscentEm * emDp)
        val contentDp = ascentDp + wholePixel(font.lineDescentEm * emDp)
        val lineDp = lineHeightPx?.let { it / density } ?: normalLineDp(font, emDp)
        return wholePixelHalfDown((ascentDp + floor((lineDp - contentDp) / 2f)) * density)
    }

    /**
     * Whole device pixels of ink below the baseline: the font's deepest descender ([UiFont.descentEm]),
     * and the distance field's antialiased fringe when the run draws from the field.
     */
    private fun inkDescentPx(font: UiFont, glyphPx: Float, forceDistanceField: Boolean): Float {
        val fringe = if (drawsFromField(font, glyphPx, forceDistanceField)) FIELD_FRINGE_PX else 0f
        return ceil(font.descentEm * glyphPx + fringe - INK_EPSILON_PX)
    }

    /** Whether a run at [glyphPx] samples the distance field rather than drawing exact-size pixel glyphs, as `TextRun` decides. */
    private fun drawsFromField(font: UiFont, glyphPx: Float, forceDistanceField: Boolean): Boolean =
        font.samplingMode == UiFontSamplingMode.DistanceField &&
            (forceDistanceField || font.pixelGlyphs == null || glyphPx != floor(glyphPx) || glyphPx < 1f || glyphPx > font.pixelGlyphMaxSize)

    /** `line-height: normal`, in whole density-independent pixels. */
    private fun normalLineDp(font: UiFont, emDp: Float): Float =
        wholePixel(font.lineAscentEm * emDp) + wholePixel(font.lineDescentEm * emDp) + wholePixel(font.lineGapEm * emDp)

    /** [value] rounded half up, as Skia rounds a font's metrics. */
    private fun wholePixel(value: Float): Float = floor(value + 0.5f)

    /** [value] rounded half down: a baseline at x.5 moves up, leaving the half pixel to the descenders. */
    private fun wholePixelHalfDown(value: Float): Float = ceil(value - 0.5f)

    /**
     * How far past its outline a distance-field glyph's antialiased edge leaves visible ink, in device
     * pixels. Measured on Roboto's descenders through the CPU rasterizer: up to one pixel at 28 to 48 px.
     */
    const val FIELD_FRINGE_PX = 1f

    /** Float noise allowed before a fraction of ink counts as another row. */
    private const val INK_EPSILON_PX = 1e-3f
}
