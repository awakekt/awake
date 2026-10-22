/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.clusterAdvance
import com.awakekt.awake.core.text.font.ellipsize
import com.awakekt.awake.core.text.font.forEachGlyphInCluster
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.core.text.unicode.GraphemeClusters
import kotlin.math.floor

/**
 * Where each character sits in a run, in device pixels.
 *
 * Shared by painting and by the caret: a text field whose caret is positioned by different arithmetic
 * than its glyphs drifts by a pixel per character, and the drift only shows up at the end of a long
 * line where nobody is looking.
 *
 * A character is a grapheme cluster ([GraphemeClusters]): the run measures, paints, places the caret
 * and hit-tests one cluster at a time, so a flag or a ZWJ emoji is one character to all four, and
 * letter spacing falls between clusters, never inside one.
 */
internal class TextRun(
    private val text: String,
    private val style: TextStyle,
    private val font: UiFont,
    private val glyphPx: Float,
    private val density: Float,
    private val wrapWidthPx: Float = Float.POSITIVE_INFINITY,
    private val singleLine: Boolean = false,
    private val overflow: TextOverflow = TextOverflow.Clip,
    private val softWrap: Boolean = true,
    private val maxLines: Int = Int.MAX_VALUE,
) {
    private val letterSpacing = style.letterSpacing.value * density

    /** The visual lines this run occupies, with their source ranges. */
    private val lineLayouts: List<TextLineLayout> = breakIntoLineLayouts(
        text = text,
        maxWidthPx = wrapWidthPx,
        softWrap = softWrap && !singleLine,
        maxLines = if (singleLine) 1 else maxLines,
        overflow = overflow,
        ellipsize = { str, maxW, always ->
            font.ellipsize(
                text = str,
                maxWidthPx = maxW,
                glyphPx = glyphPx,
                weight = style.weight,
                alwaysEllipsize = always,
            )
        },
    ) { line ->
        widthBetween(line, 0, line.length)
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
    fun offsetAt(index: Int): Float = widthBetween(text, 0, index.coerceIn(0, text.length))

    /** The line box the caret occupies, including soft wrapping. */
    fun caretPositionAt(index: Int): TextCaretPosition {
        val bounded = index.coerceIn(0, text.length)
        val lineIndex = lineLayouts.indexOfFirst { bounded <= it.endExclusive }
            .let { if (it >= 0) it else lineLayouts.lastIndex }
        val line = lineLayouts[lineIndex]
        val x = widthBetween(text, line.start, bounded.coerceIn(line.start, line.endExclusive))
        return TextCaretPosition(x, lineIndex * lineHeightPx)
    }

    /** Horizontal caret position within the visual line containing [index]. */
    fun offsetAtLine(index: Int): Float = caretPositionAt(index).x

    /**
     * The cluster boundary nearest ([x], [y]), so a click never lands inside a character.
     *
     * Hit testing uses the same visual lines as painting: choosing against the original, unwrapped
     * string makes every click below the first soft-wrapped line resolve to a first-line index.
     */
    fun indexAt(x: Float, y: Float = 0f): Int {
        val lineIndex = (y / lineHeightPx).toInt().coerceIn(0, lineLayouts.lastIndex)
        val line = lineLayouts[lineIndex]
        var best = line.start
        var bestDistance = kotlin.math.abs(x)
        var pen = 0f
        var start = line.start
        while (start < line.endExclusive) {
            val end = GraphemeClusters.nextBoundary(text, start).coerceAtMost(line.endExclusive)
            pen += font.clusterAdvance(text, start, end, glyphPx, style.weight) + letterSpacing
            val distance = kotlin.math.abs(pen - x)
            if (distance < bestDistance) {
                bestDistance = distance
                best = end
            }
            start = end
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
                val x = widthBetween(text, line.start, segmentStart)
                val right = widthBetween(text, line.start, segmentEnd)
                block(x, lineIndex * lineHeightPx, right - x, lineHeightPx)
            }
        }
    }

    /**
     * [paint], under its style's shadow and outline: the run again in the shadow's colour, moved by its
     * offset, then in the outline's colour moved a step in each of eight directions, then the text.
     */
    fun paintDecorated(scope: DrawScope, color: Color, offsetX: Float = 0f, offsetY: Float = 0f) {
        style.shadow?.let { paint(scope, it.color, offsetX + it.offsetX * density, offsetY + it.offsetY * density) }
        style.outline?.let { ring ->
            val step = ring.width * density
            for (i in OUTLINE_X.indices) paint(scope, ring.color, offsetX + OUTLINE_X[i] * step, offsetY + OUTLINE_Y[i] * step)
        }
        paint(scope, color, offsetX, offsetY)
    }

    fun paint(scope: DrawScope, color: Color, offsetX: Float = 0f, offsetY: Float = 0f) {
        // A distance-field glyph is antialiased by its coverage at the pixel it lands on, so a stem
        // that straddles two pixel columns is two grey columns instead of one solid one. Whole
        // device pixels are what the line's start and baseline snap to; the advances inside the
        // line stay fractional so letter spacing is not disturbed. A scaled scope keeps fractional
        // positions, or text under an animated scale would move in one-pixel steps.
        val snap = scope.isDevicePixelAligned
        val originX = if (snap) wholePixel(offsetX) else offsetX
        val ascentPx = font.ascentEm * glyphPx
        var y = offsetY
        // Indexed: this runs for every text node every frame, and a List iterator is an allocation.
        for (i in lineLayouts.indices) {
            var top = y + lineTopPaddingPx
            if (snap) {
                val baseline = top + ascentPx
                top += wholePixel(baseline) - baseline
            }
            paintLine(lineLayouts[i].text, scope, color, originX, top)
            y += lineHeightPx
        }
    }

    /** [value] rounded half up to a whole pixel. */
    private fun wholePixel(value: Float): Float = floor(value + 0.5f)

    private fun paintLine(line: String, scope: DrawScope, color: Color, offsetX: Float, y: Float) {
        var x = offsetX
        var start = 0
        while (start < line.length) {
            val end = GraphemeClusters.nextBoundary(line, start)
            val clusterX = x
            x += font.forEachGlyphInCluster(line, start, end, glyphPx, style.weight) { codePoint, penX ->
                val uv = font.glyphFor(codePoint, style.weight)
                if (uv != null) {
                    scope.drawGlyph(
                        x = clusterX + penX + uv.offsetXEm * glyphPx,
                        y = y + uv.offsetYEm * glyphPx,
                        width = uv.widthEm * glyphPx,
                        height = uv.heightEm * glyphPx,
                        u0 = uv.u0,
                        v0 = uv.v0,
                        u1 = uv.u1,
                        v1 = uv.v1,
                        color = color,
                        font = font,
                    )
                }
            } + letterSpacing
            start = end
        }
    }

    /**
     * The pen distance of `value[start until end]`, whole clusters only: one that [end] falls inside
     * is left out. [start] is a cluster boundary, as a line's start is.
     */
    private fun widthBetween(value: String, start: Int, end: Int): Float {
        var x = 0f
        var clusterStart = start
        while (clusterStart < end) {
            val clusterEnd = GraphemeClusters.nextBoundary(value, clusterStart)
            if (clusterEnd > end) break
            x += font.clusterAdvance(value, clusterStart, clusterEnd, glyphPx, style.weight) + letterSpacing
            clusterStart = clusterEnd
        }
        return x
    }
}

internal data class TextCaretPosition(val x: Float, val y: Float)

// Unit steps to the eight neighbours; the diagonals are shortened so the ring is round, not square.
private val OUTLINE_X = floatArrayOf(1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f, 0f, 0.7071f)
private val OUTLINE_Y = floatArrayOf(0f, 0.7071f, 1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f)
