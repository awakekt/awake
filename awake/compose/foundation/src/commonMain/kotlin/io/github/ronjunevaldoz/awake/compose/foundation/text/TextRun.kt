// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.text

import io.github.ronjunevaldoz.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.ronjunevaldoz.awake.core.color.Color
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.ui.theme.TextStyle

/**
 * Where each character sits in a run, in device pixels.
 *
 * Shared by painting and by the caret: a text field whose caret is positioned by different arithmetic
 * than its glyphs drifts by a pixel per character, and the drift only shows up at the end of a long
 * line where nobody is looking.
 */
internal class TextRun(
    private val text: String,
    private val style: TextStyle,
    private val font: UiFont,
    private val glyphPx: Float,
    private val density: Float,
) {
    private val letterSpacing = style.letterSpacing.value * density

    /** Left edge of the character at [index], or the run's end when [index] is its length. */
    fun offsetAt(index: Int): Float {
        var x = 0f
        for (i in 0 until index.coerceIn(0, text.length)) {
            x += font.advanceFor(text[i], glyphPx) + letterSpacing
        }
        return x
    }

    /** The character boundary nearest [x] -- what a click on a text field resolves to. */
    fun indexAt(x: Float): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for (i in 0..text.length) {
            val distance = kotlin.math.abs(offsetAt(i) - x)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    fun paint(scope: DrawScope, color: Color) {
        var x = 0f
        for (i in text.indices) {
            val uv = font.uvFor(text[i])
            if (uv != null) {
                scope.drawGlyph(
                    x = x + uv.offsetXEm * glyphPx,
                    y = uv.offsetYEm * glyphPx,
                    width = uv.widthEm * glyphPx,
                    height = uv.heightEm * glyphPx,
                    u0 = uv.u0,
                    v0 = uv.v0,
                    u1 = uv.u1,
                    v1 = uv.v1,
                    color = color,
                )
            }
            x += font.advanceFor(text[i], glyphPx) + letterSpacing
        }
    }
}
