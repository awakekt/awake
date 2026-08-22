// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.text

import io.github.ronjunevaldoz.awake.compose.ui.layout.Measurable
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasurePolicy
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureResult
import io.github.ronjunevaldoz.awake.compose.ui.layout.MeasureScope
import io.github.ronjunevaldoz.awake.compose.ui.platform.DefaultFontSize
import io.github.ronjunevaldoz.awake.compose.ui.unit.Constraints
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.ui.scope.pixelPerfectTextScale
import io.github.ronjunevaldoz.awake.ui.theme.TextStyle
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Sizes a run of text from the font's own metrics.
 *
 * [style] and [font] arrive already resolved. A policy never reads a `CompositionLocal` -- the
 * composable resolves them and hands concrete values in, which is what keeps a subtree measurable
 * in isolation.
 *
 * Single-line for now: no wrapping, no ellipsis. Both need a break iterator and a decision about
 * where soft-wrap opportunities are, which is its own change.
 */
class TextMeasurePolicy(
    private val text: String,
    private val style: TextStyle,
    private val font: UiFont,
) : MeasurePolicy {

    override fun MeasureScope.measure(
        measurables: List<Measurable>,
        constraints: Constraints,
    ): MeasureResult {
        val glyphPx = glyphPixels()
        val width = ceil(runWidth(glyphPx)).roundToInt()
        val height = ceil(glyphPx * font.lineHeightEm).roundToInt()
        return layout(constraints.constrainWidth(width), constraints.constrainHeight(height)) {}
    }

    /**
     * Em size in device pixels.
     *
     * Snapped to the atlas's own scale step: a bitmap or distance-field atlas is sampled at
     * discrete sizes, and asking for one in between resamples and blurs. `ui-core` learned this the
     * same way -- see `pixelPerfectTextScale`.
     */
    private fun MeasureScope.glyphPixels(): Float {
        val sizeSp = style.size ?: DefaultFontSize
        val snapped = pixelPerfectTextScale(style.scale, font.textScaleStep)
        return (sizeSp.value * density * fontScale * snapped).coerceAtLeast(1f)
    }

    private fun MeasureScope.runWidth(glyphPx: Float): Float {
        if (text.isEmpty()) return 0f
        var total = 0f
        for (i in text.indices) {
            total += font.advanceFor(text[i], glyphPx)
        }
        // Letter spacing sits between glyphs, not after the last one -- the same off-by-one that
        // puts a phantom gap on the trailing edge of a centred label.
        val gaps = (text.length - 1).coerceAtLeast(0)
        return total + style.letterSpacing.value * density * gaps
    }

    /** The same arithmetic painting and the caret use, so the three cannot drift apart. */
    internal fun runFor(density: Float, fontScale: Float): TextRun =
        TextRun(text, style, font, glyphPixels(density, fontScale), density)

    internal fun glyphPixels(density: Float, fontScale: Float): Float {
        val sizeSp = style.size ?: DefaultFontSize
        val snapped = pixelPerfectTextScale(style.scale, font.textScaleStep)
        return (sizeSp.value * density * fontScale * snapped).coerceAtLeast(1f)
    }
}
