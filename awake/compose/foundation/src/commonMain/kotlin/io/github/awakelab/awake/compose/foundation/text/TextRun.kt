/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.foundation.text

import io.github.awakelab.awake.compose.ui.graphics.drawscope.DrawScope
import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.core.text.theme.TextStyle

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
    private val wrapWidthPx: Float = Float.POSITIVE_INFINITY,
) {
    private val letterSpacing = style.letterSpacing.value * density

    /** The visual lines this run occupies, with their source ranges. */
    private val lineLayouts: List<TextLineLayout> = breakIntoLineLayouts(text, wrapWidthPx) { line ->
        var total = 0f
        for (i in line.indices) total += font.advanceFor(line[i], glyphPx, style.weight) + letterSpacing
        total
    }

    /** The line advance, widened for multiline text when a compact authored box cannot contain the font. */
    internal val lineHeightPx: Float = maxOf(
        style.lineHeight?.let { it.value * density } ?: (glyphPx * font.lineHeightEm),
        if (lineLayouts.size > 1) glyphPx * font.lineHeightEm else 0f,
    )

    private val lineTopPaddingPx: Float =
        ((lineHeightPx - glyphPx * font.lineHeightEm) / 2f).coerceAtLeast(0f)

    /** The lines this run occupies, hard breaks and soft wraps alike. */
    internal val lines: List<String> = lineLayouts.map(TextLineLayout::text)

    /** Left edge of the character at [index], or the run's end when [index] is its length. */
    fun offsetAt(index: Int): Float {
        var x = 0f
        for (i in 0 until index.coerceIn(0, text.length)) {
            x += font.advanceFor(text[i], glyphPx, style.weight) + letterSpacing
        }
        return x
    }

    /** The line box the caret occupies, including soft wrapping. */
    fun caretPositionAt(index: Int): TextCaretPosition {
        val bounded = index.coerceIn(0, text.length)
        val lineIndex = lineLayouts.indexOfFirst { bounded <= it.endExclusive }
            .let { if (it >= 0) it else lineLayouts.lastIndex }
        val line = lineLayouts[lineIndex]
        val x = offsetWithin(text.substring(line.start, bounded.coerceIn(line.start, line.endExclusive)))
        return TextCaretPosition(x, lineIndex * lineHeightPx)
    }

    /** Horizontal caret position within the visual line containing [index]. */
    fun offsetAtLine(index: Int): Float = caretPositionAt(index).x

    /**
     * The character boundary nearest ([x], [y]).
     *
     * Hit testing uses the same visual lines as painting: choosing against the original, unwrapped
     * string makes every click below the first soft-wrapped line resolve to a first-line index.
     */
    fun indexAt(x: Float, y: Float = 0f): Int {
        val lineIndex = (y / lineHeightPx).toInt().coerceIn(0, lineLayouts.lastIndex)
        val line = lineLayouts[lineIndex]
        var best = line.start
        var bestDistance = Float.MAX_VALUE
        for (i in line.start..line.endExclusive) {
            val distance = kotlin.math.abs(offsetWithin(text.substring(line.start, i)) - x)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    /** Calls [block] for the visual rectangles occupied by the source selection. */
    fun forEachSelectionSegment(start: Int, end: Int, block: (x: Float, y: Float, width: Float, height: Float) -> Unit) {
        val selectionStart = start.coerceIn(0, text.length)
        val selectionEnd = end.coerceIn(selectionStart, text.length)
        if (selectionStart == selectionEnd) return
        for (lineIndex in lineLayouts.indices) {
            val line = lineLayouts[lineIndex]
            val segmentStart = maxOf(selectionStart, line.start)
            val segmentEnd = minOf(selectionEnd, line.endExclusive)
            if (segmentStart < segmentEnd) {
                val x = offsetWithin(text.substring(line.start, segmentStart))
                val right = offsetWithin(text.substring(line.start, segmentEnd))
                block(x, lineIndex * lineHeightPx, right - x, lineHeightPx)
            }
        }
    }

    fun paint(scope: DrawScope, color: Color, offsetX: Float = 0f, offsetY: Float = 0f) {
        var y = offsetY
        for (line in lineLayouts) {
            paintLine(line.text, scope, color, offsetX, y + lineTopPaddingPx)
            y += lineHeightPx
        }
    }

    private fun paintLine(line: String, scope: DrawScope, color: Color, offsetX: Float, y: Float) {
        var x = offsetX
        for (i in line.indices) {
            val uv = font.glyphFor(line[i], style.weight)
            if (uv != null) {
                scope.drawGlyph(
                    x = x + uv.offsetXEm * glyphPx,
                    y = y + uv.offsetYEm * glyphPx,
                    width = uv.widthEm * glyphPx,
                    height = uv.heightEm * glyphPx,
                    u0 = uv.u0,
                    v0 = uv.v0,
                    u1 = uv.u1,
                    v1 = uv.v1,
                    color = color,
                )
            }
            x += font.advanceFor(line[i], glyphPx, style.weight) + letterSpacing
        }
    }

    private fun offsetWithin(value: String): Float {
        var x = 0f
        for (character in value) x += font.advanceFor(character, glyphPx, style.weight) + letterSpacing
        return x
    }

}

internal data class TextCaretPosition(val x: Float, val y: Float)
