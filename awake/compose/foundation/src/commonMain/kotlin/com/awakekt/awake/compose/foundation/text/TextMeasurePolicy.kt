/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.layout.AlignmentLine
import com.awakekt.awake.compose.ui.layout.FirstBaseline
import com.awakekt.awake.compose.ui.layout.IntrinsicMeasurable
import com.awakekt.awake.compose.ui.layout.LastBaseline
import com.awakekt.awake.compose.ui.layout.Measurable
import com.awakekt.awake.compose.ui.layout.MeasurePolicy
import com.awakekt.awake.compose.ui.layout.MeasureResult
import com.awakekt.awake.compose.ui.layout.MeasureScope
import com.awakekt.awake.compose.ui.platform.DefaultFontSize
import com.awakekt.awake.compose.ui.unit.Constraints
import com.awakekt.awake.compose.ui.unit.Density
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.scope.pixelPerfectTextScale
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Sizes a run of text from the font's own metrics.
 *
 * [style] and [font] arrive already resolved. A policy never reads a `CompositionLocal` -- the
 * composable resolves them and hands concrete values in, which is what keeps a subtree measurable
 * in isolation.
 *
 * Single-line fields use an unbounded text run and let the viewport handle horizontal overflow;
 * multiline fields use the measured width for greedy soft wrapping.
 */
class TextMeasurePolicy(
    private val source: () -> String,
    private val style: TextStyle,
    private val font: UiFont,
    private val singleLine: Boolean = false,
) : MeasurePolicy {

    /** Text that never changes for the life of this policy, which is one pass for [Text]. */
    constructor(text: String, style: TextStyle, font: UiFont, singleLine: Boolean = false) :
        this({ text }, style, font, singleLine)

    /**
     * Read here rather than captured at composition, so an editable field's content reaches the
     * glyphs through the *layout* phase.
     *
     * This is the phase discipline Compose's own `BasicTextField` follows -- it reads its state
     * during layout and draw, which is why typing there triggers neither recomposition nor a state
     * subscription. Capturing the string at composition instead means a keystroke changes nothing
     * anything downstream can see until something re-runs the composable, which is the bug the
     * removed `TextFieldState.collectAsState` existed to paper over.
     */
    internal val text: String get() = source()

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val glyphPx = glyphPixels()
        // Wrapping is decided here and remembered, not recomputed by the painter: two greedy passes
        // at different widths do not have to agree, and a box the right height with the words on the
        // wrong lines is invisible to a geometry oracle.
        wrapWidthPx = if (singleLine || !constraints.hasBoundedWidth) {
            Float.POSITIVE_INFINITY
        } else {
            constraints.maxWidth.toFloat()
        }
        val lines = linesAt(glyphPx, wrapWidthPx)
        // Placeables are integer-pixel sized today. Quantize the complete intrinsic line once,
        // after weighted glyph advances and letter spacing have been summed, so sibling rows do
        // not accumulate a ceiling error for every label. This is the deterministic boundary
        // until the retained layout model carries fractional bounds end to end.
        val width = lines.maxOf { lineWidth(it, glyphPx) }.roundToInt()
        val rawLineHeight = oneLineHeight(glyphPx)
        // A compact line-height is fine for one line, but cannot be allowed to make adjacent
        // multiline glyph quads overlap the font's own vertical metrics.
        val safeLineHeight = if (lines.size > 1) {
            maxOf(rawLineHeight, glyphPx * font.lineHeightEm)
        } else {
            rawLineHeight
        }
        val lineHeight = ceil(safeLineHeight).roundToInt()
        val height = lineHeight * lines.size
        val fontH = glyphPx * font.lineHeightEm
        val topPad = ((safeLineHeight - fontH) / 2f).coerceAtLeast(0f)
        val firstBaseline = (topPad + glyphPx * font.ascentEm).roundToInt()
        val lastBaseline = firstBaseline + lineHeight * (lines.size - 1).coerceAtLeast(0)
        val alignmentLines = mapOf<AlignmentLine, Int>(
            FirstBaseline to firstBaseline,
            LastBaseline to lastBaseline,
        )
        return layout(
            constraints.constrainWidth(width),
            constraints.constrainHeight(height),
            alignmentLines,
        ) {}
    }

    /**
     * The widest this text wants to be, and the narrowest it can survive at.
     *
     * A [MeasurePolicy] with no children inherits `max over children`, which for a leaf is zero --
     * so every label reported that it wanted no width at all, and anything sizing itself to its
     * content got nothing back. A dropdown asking for its widest item was told 0.
     */
    override fun Density.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int =
        widestLine(Float.POSITIVE_INFINITY)

    /**
     * The longest single word: below that, wrapping cannot help and a glyph would be cut.
     *
     * Measured directly rather than by wrapping at zero. A greedy wrapper keeps at least one word
     * per line and returns the text unbroken when nothing fits at all, so asking it for a
     * zero-width layout answers with the whole line -- the maximum, wearing the minimum's name.
     */
    override fun Density.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        val glyphPx = glyphPixels()
        return text.split(' ', '\n', '\t')
            .maxOfOrNull { lineWidth(it, glyphPx) }
            ?.roundToInt()
            ?: 0
    }

    private fun Density.widestLine(wrapAt: Float): Int {
        val glyphPx = glyphPixels()
        return linesAt(glyphPx, wrapAt).maxOfOrNull { lineWidth(it, glyphPx) }?.roundToInt() ?: 0
    }

    /** The width the last measure wrapped at, so painting reproduces the same lines. */
    private var wrapWidthPx: Float = Float.POSITIVE_INFINITY

    private fun Density.linesAt(glyphPx: Float, maxWidthPx: Float): List<String> =
        breakIntoLines(text, maxWidthPx) { lineWidth(it, glyphPx) }

    /**
     * The line box, authored where the style says so and intrinsic otherwise.
     *
     * A CSS text scale is a *pair*: `text-sm` is `font-size: 14px; line-height: 20px`, and a design
     * system that carries only the first has no way to reproduce the second. Ignoring
     * [TextStyle.lineHeight] here made every ported recipe short by the difference -- a dropdown item
     * measured 29px against shadcn's 32 because the font's own metrics put the line at 17.
     */
    private fun Density.oneLineHeight(glyphPx: Float): Float =
        style.lineHeight?.let { it.value * density * fontScale } ?: (glyphPx * font.lineHeightEm)

    /**
     * Em size in device pixels.
     *
     * Snapped to the atlas's own scale step: a bitmap or distance-field atlas is sampled at
     * discrete sizes, and asking for one in between resamples and blurs. `ui-core` learned this the
     * same way -- see `pixelPerfectTextScale`.
     */
    private fun Density.glyphPixels(): Float {
        val sizeSp = style.size ?: DefaultFontSize
        val snapped = pixelPerfectTextScale(style.scale, font.textScaleStep)
        return (sizeSp.value * density * fontScale * snapped).coerceAtLeast(1f)
    }

    /** One line's advance width. A block is as wide as its longest line, never their sum. */
    private fun Density.lineWidth(line: String, glyphPx: Float): Float {
        if (line.isEmpty()) return 0f
        var total = 0f
        for (i in line.indices) {
            total += font.advanceFor(line[i], glyphPx, style.weight)
        }
        // Letter spacing sits between glyphs, not after the last one -- the same off-by-one that
        // puts a phantom gap on the trailing edge of a centred label.
        val gaps = (line.length - 1).coerceAtLeast(0)
        return total + style.letterSpacing.value * density * gaps
    }

    /** The same arithmetic painting and the caret use, so the three cannot drift apart. */
    internal fun runFor(density: Float, fontScale: Float): TextRun =
        TextRun(text, style, font, glyphPixels(density, fontScale), density, wrapWidthPx)

    internal fun glyphPixels(density: Float, fontScale: Float): Float {
        val sizeSp = style.size ?: DefaultFontSize
        val snapped = pixelPerfectTextScale(style.scale, font.textScaleStep)
        return (sizeSp.value * density * fontScale * snapped).coerceAtLeast(1f)
    }
}
