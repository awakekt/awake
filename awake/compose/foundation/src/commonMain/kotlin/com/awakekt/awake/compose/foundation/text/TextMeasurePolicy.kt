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
import com.awakekt.awake.core.text.font.clusterAdvance
import com.awakekt.awake.core.text.font.ellipsize
import com.awakekt.awake.core.text.font.kerningBefore
import com.awakekt.awake.core.text.scope.pixelPerfectTextScale
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.core.text.unicode.GraphemeClusters
import com.awakekt.awake.core.text.unicode.LineBreaks
import com.awakekt.awake.core.text.unicode.WordBreak
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
    internal val style: TextStyle,
    internal val font: UiFont,
    internal val singleLine: Boolean = false,
    internal val overflow: TextOverflow = TextOverflow.Clip,
    internal val softWrap: Boolean = true,
    internal val maxLines: Int = Int.MAX_VALUE,
) : MeasurePolicy {

    /** Text that never changes for the life of this policy, which is one pass for [Text]. */
    constructor(
        text: String,
        style: TextStyle,
        font: UiFont,
        singleLine: Boolean = false,
        overflow: TextOverflow = TextOverflow.Clip,
        softWrap: Boolean = true,
        maxLines: Int = Int.MAX_VALUE,
    ) : this({ text }, style, font, singleLine, overflow, softWrap, maxLines)

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

    /**
     * Last frame's measurement and run for the same node, when the caller keeps one.
     *
     * `Text` hands in the cache its retained paint node owns; a hit skips line breaking and the
     * alignment-line map. Without one, every measure and paint is computed fresh.
     */
    internal var cache: TextLayoutCache? = null

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        // Wrapping is decided here and remembered, not recomputed by the painter: two greedy passes
        // at different widths do not have to agree, and a box the right height with the words on the
        // wrong lines is invisible to a geometry oracle.
        wrapWidthPx = if (singleLine || !constraints.hasBoundedWidth) {
            Float.POSITIVE_INFINITY
        } else {
            constraints.maxWidth.toFloat()
        }
        measuredFontScale = fontScale
        val cached = cache
        if (cached != null && cached.hasMeasurement(this@TextMeasurePolicy, density, fontScale, wrapWidthPx)) {
            return layout(
                constraints.constrainWidth(cached.width),
                constraints.constrainHeight(cached.height),
                cached.alignmentLines,
            ) {}
        }
        val glyphPx = glyphPixels(density, fontScale)
        val layouts = layoutsAt(glyphPx, wrapWidthPx)
        val lines = layouts.size
        // Placeables are integer-pixel sized today. Quantize the complete intrinsic line once,
        // after weighted glyph advances and letter spacing have been summed, so sibling rows do
        // not accumulate a ceiling error for every label. This is the deterministic boundary
        // until the retained layout model carries fractional bounds end to end.
        val width = layouts.maxOf { it.boxWidthPx(text) { line -> lineWidth(line, glyphPx) } }
        // Whole pixels both, from the rule painting uses, so the box is exactly the lines it paints.
        val em = emDp(fontScale)
        val lineHeight = TextLineBox.advancePx(font, em, glyphPx, density, authoredLineHeight()).roundToInt()
        val height = lineHeight * lines
        val firstBaseline = TextLineBox.baselinePx(font, em, density, authoredLineHeight()).roundToInt()
        val lastBaseline = firstBaseline + lineHeight * (lines - 1).coerceAtLeast(0)
        val alignmentLines = mapOf<AlignmentLine, Int>(
            FirstBaseline to firstBaseline,
            LastBaseline to lastBaseline,
        )
        cached?.storeMeasurement(width, height, alignmentLines)
        cached?.keyMeasurement(this@TextMeasurePolicy, density, fontScale, wrapWidthPx)
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
    override fun Density.maxIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        val glyphPx = glyphPixels(density, fontScale)
        return layoutsAt(glyphPx, Float.POSITIVE_INFINITY).maxOfOrNull { it.boxWidthPx(text) { line -> lineWidth(line, glyphPx) } } ?: 0
    }

    /**
     * The widest single soft-wrap segment: below that, normal wrapping cannot help and emergency
     * grapheme splitting or clipping takes over.
     */
    override fun Density.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int {
        if (singleLine || !softWrap) return maxIntrinsicWidth(measurables, height)
        val sourceText = text
        val glyphPx = glyphPixels(density, fontScale)
        val resolvedWordBreak = style.wordBreak ?: WordBreak.Normal
        return if (sourceText.isEmpty()) 0 else widestWrappedSegmentPx(sourceText, resolvedWordBreak) { lineWidth(it, glyphPx) }
    }

    /**
     * The height the text takes when wrapped at [width] -- exactly what [measure] would report
     * there. Unbounded, it is the unwrapped height: one line per hard line.
     *
     * Without these a leaf inherits `max over children`, zero: every label told a planning parent
     * it needed no height, so a FlexBox column gave each one none and a wrapping row planned its
     * lines as if they held only the icons.
     */
    override fun Density.minIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int {
        val wrapAt = if (singleLine || width == Constraints.Infinity) Float.POSITIVE_INFINITY else width.toFloat()
        val glyphPx = glyphPixels(density, fontScale)
        val lineCount = layoutsAt(glyphPx, wrapAt).size
        return TextLineBox.advancePx(font, emDp(fontScale), glyphPx, density, authoredLineHeight()).roundToInt() * lineCount
    }

    /** The same answer as [minIntrinsicHeight]: text has one height at a given width. */
    override fun Density.maxIntrinsicHeight(measurables: List<IntrinsicMeasurable>, width: Int): Int =
        minIntrinsicHeight(measurables, width)

    /** The width the last measure wrapped at, so painting reproduces the same lines. */
    internal var wrapWidthPx: Float = Float.POSITIVE_INFINITY
        private set

    /** The font scale the last measure sized the glyphs at, so painting draws them at the same size. */
    internal var measuredFontScale: Float = 1f
        private set

    private fun Density.layoutsAt(glyphPx: Float, maxWidthPx: Float): List<TextLineLayout> =
        breakIntoLineLayouts(
            text = text,
            maxWidthPx = maxWidthPx,
            softWrap = softWrap && !singleLine,
            maxLines = if (singleLine) 1 else maxLines,
            overflow = overflow,
            wordBreak = style.wordBreak ?: WordBreak.Normal,
            ellipsize = { str, maxW, always ->
                font.ellipsize(
                    text = str,
                    maxWidthPx = maxW,
                    glyphPx = glyphPx,
                    weight = style.weight,
                    alwaysEllipsize = always,
                    kerning = style.kerning != false,
                )
            },
        ) { lineWidth(it, glyphPx) }

    /**
     * The authored line height in device pixels, or null for the font's own; [TextLineBox] turns either
     * into the line box.
     *
     * A CSS text scale is a *pair*: `text-sm` is `font-size: 14px; line-height: 20px`, and a design
     * system that carries only the first has no way to reproduce the second. Ignoring
     * [TextStyle.lineHeight] here made every ported recipe short by the difference -- a dropdown item
     * measured 29px against shadcn's 32 because the font's own metrics put the line at 17.
     */
    private fun Density.authoredLineHeight(): Float? = style.lineHeight?.let { it.value * density * fontScale }

    /**
     * One line's advance width. A block is as wide as its longest line, never their sum.
     *
     * Summed a grapheme cluster at a time, as `TextRun` paints: a ZWJ emoji is one character, not
     * the eight `Char`s it is stored in. Adjacent clusters kern as `TextRun` kerns them.
     */
    private fun Density.lineWidth(line: String, glyphPx: Float): Float {
        if (line.isEmpty()) return 0f
        val kerning = style.kerning != false
        var total = 0f
        var clusters = 0
        var previous = -1
        GraphemeClusters.forEach(line) { start, end ->
            total += font.kerningBefore(line, previous, start, glyphPx, style.weight)
            total += font.clusterAdvance(line, start, end, glyphPx, style.weight)
            if (kerning) previous = start
            clusters++
        }
        // Letter spacing sits between characters, not after the last one -- the same off-by-one that
        // puts a phantom gap on the trailing edge of a centred label.
        val gaps = (clusters - 1).coerceAtLeast(0)
        return total + style.letterSpacing.value * density * gaps
    }

    /** The same arithmetic painting and the caret use, so the three cannot drift apart. */
    internal fun runFor(density: Float, fontScale: Float): TextRun =
        cache?.runFor(this, density, fontScale, wrapWidthPx) ?: buildRun(density, fontScale)

    /**
     * Em size in device pixels.
     *
     * Snapped to the atlas's own scale step: a bitmap or distance-field atlas is sampled at
     * discrete sizes, and asking for one in between resamples and blurs. `ui-core` learned this the
     * same way -- see `pixelPerfectTextScale`.
     *
     * Then rounded to a whole device pixel. At 125% or 150% display scaling `13.sp` is 16.25 or
     * 19.5 pixels, and an em that is a fraction of a pixel puts every stem on a different sub-pixel
     * phase, so the same letter renders with a different weight in each place it appears. Measuring
     * and painting both come through here, so a run's width and its glyphs agree on the rounded size.
     */
    internal fun glyphPixels(density: Float, fontScale: Float): Float {
        val sizeSp = style.size ?: DefaultFontSize
        val snapped = pixelPerfectTextScale(style.scale, font.textScaleStep)
        return (sizeSp.value * density * fontScale * snapped).roundToInt().coerceAtLeast(1).toFloat()
    }
}

/**
 * The authored em in density-independent pixels: size times font scale times the atlas-snapped text
 * scale, before it is rounded to device pixels as [TextMeasurePolicy.glyphPixels] does. [TextLineBox]
 * builds the line from this, so a fractional density does not stretch the line by the rounding of the
 * drawn size.
 */
internal fun TextMeasurePolicy.emDp(fontScale: Float): Float {
    val sizeSp = style.size ?: DefaultFontSize
    val snapped = pixelPerfectTextScale(style.scale, font.textScaleStep)
    return sizeSp.value * fontScale * snapped
}

/** A fresh run from [TextMeasurePolicy]'s inputs and its last wrap width. */
internal fun TextMeasurePolicy.buildRun(density: Float, fontScale: Float): TextRun =
    TextRun(
        text = text,
        style = style,
        font = font,
        glyphPx = glyphPixels(density, fontScale),
        emDp = emDp(fontScale),
        density = density,
        wrapWidthPx = wrapWidthPx,
        singleLine = singleLine,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        fontScale = fontScale,
    )

private inline fun widestWrappedSegmentPx(
    sourceText: String,
    wordBreak: WordBreak,
    measureSegment: (String) -> Float,
): Int {
    var widest = 0f
    var hardStart = 0
    while (hardStart <= sourceText.length) {
        val hardEnd = sourceText.indexOf('\n', hardStart).let { if (it < 0) sourceText.length else it }
        var segStart = hardStart
        while (segStart < hardEnd) {
            val segEnd = LineBreaks.nextOpportunity(sourceText, segStart, hardEnd, wordBreak)
            val visibleEnd = trimTrailingSpaces(sourceText, segStart, segEnd)
            if (visibleEnd > segStart) {
                widest = maxOf(widest, measureSegment(sourceText.substring(segStart, visibleEnd)))
            }
            segStart = segEnd
        }
        if (hardEnd == sourceText.length) break
        hardStart = hardEnd + 1
    }
    return widest.roundToInt()
}
