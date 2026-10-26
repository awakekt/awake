/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.PixelGlyphs
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.clusterAdvance
import com.awakekt.awake.core.text.font.ellipsize
import com.awakekt.awake.core.text.font.forEachGlyphInCluster
import com.awakekt.awake.core.text.font.kerningBefore
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
 * letter spacing falls between clusters, never inside one. So does the font's pair kerning
 * ([kerningBefore]), unless the style turns it off: every walk below adds it between a cluster and the
 * next on the same line, so a kerned glyph, the box around it and the caret beside it agree. A caret
 * between two kerned clusters sits where the second one's pen starts, as a browser puts it.
 */
// Measuring, the caret, hit testing and two paint paths share one layout here, so they cannot disagree.
@Suppress("TooManyFunctions")
internal class TextRun(
    private val text: String,
    private val style: TextStyle,
    private val font: UiFont,
    private val glyphPx: Float,
    private val density: Float,
    private val emDp: Float = glyphPx / density,
    private val wrapWidthPx: Float = Float.POSITIVE_INFINITY,
    private val singleLine: Boolean = false,
    private val overflow: TextOverflow = TextOverflow.Clip,
    private val softWrap: Boolean = true,
    private val maxLines: Int = Int.MAX_VALUE,
    private val fontScale: Float = 1f,
) {
    private val letterSpacing = style.letterSpacing.value * density

    /** Whether adjacent clusters kern: unless the style turns it off, as a browser's `font-kerning: auto`. */
    private val kerning = style.kerning != false

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
                kerning = kerning,
            )
        },
    ) { line ->
        widthBetween(line, 0, line.length)
    }

    private val authoredLineHeightPx: Float? = style.lineHeight?.let { it.value * density * fontScale }

    /**
     * The distance from each line's top to the next, a whole number of device pixels and the same for
     * every line: the authored line height, tighter or looser than the font's, or the font's own.
     * See [TextLineBox].
     */
    internal val lineHeightPx: Float = TextLineBox.advancePx(font, emDp, glyphPx, density, authoredLineHeightPx)

    /**
     * How far below its line's top the em box the glyph metrics are measured from begins: the whole-pixel
     * baseline offset less the font's [UiFont.ascentEm]. Negative when a tight line height lifts the
     * glyphs above the line box.
     */
    private val glyphTopPx: Float =
        TextLineBox.baselinePx(font, emDp, density, authoredLineHeightPx) - font.ascentEm * glyphPx

    /** The lines this run occupies, hard breaks and soft wraps alike. */
    internal val lines: List<String> = lineLayouts.map(TextLineLayout::text)

    /** Left edge of the character at [index], kerned to the one before it, or the run's end when [index] is its length. */
    fun offsetAt(index: Int): Float = widthBetween(text, 0, index.coerceIn(0, text.length), text.length)

    /** The line box the caret occupies, including soft wrapping. */
    fun caretPositionAt(index: Int): TextCaretPosition {
        val bounded = index.coerceIn(0, text.length)
        val lineIndex = lineLayouts.indexOfFirst { bounded <= it.endExclusive }
            .let { if (it >= 0) it else lineLayouts.lastIndex }
        val line = lineLayouts[lineIndex]
        val x = widthBetween(text, line.start, bounded.coerceIn(line.start, line.endExclusive), line.endExclusive)
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
            // The boundary after this cluster is where the next one's kerned pen starts, as the caret puts it.
            pen += font.clusterAdvance(text, start, end, glyphPx, style.weight) + letterSpacing +
                kerningAfter(text, start, end, line.endExclusive)
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
                val x = widthBetween(text, line.start, segmentStart, line.endExclusive)
                val right = widthBetween(text, line.start, segmentEnd, line.endExclusive)
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

    /**
     * The font's exact-size glyphs, when this run draws from them: its size is a whole number of device
     * pixels no larger than the font's [UiFont.pixelGlyphMaxSize]. Read once, as the run is laid out.
     */
    private val pixelGlyphs: PixelGlyphs? = font.pixelGlyphs?.takeIf {
        glyphPx == floor(glyphPx) && glyphPx >= 1f && glyphPx <= font.pixelGlyphMaxSize
    }

    fun paint(scope: DrawScope, color: Color, offsetX: Float = 0f, offsetY: Float = 0f) {
        // A distance-field glyph is antialiased by its coverage at the pixel it lands on, so a stem
        // that straddles two pixel columns is two grey columns instead of one solid one. Whole
        // device pixels are what the line's start and baseline snap to; the advances inside the
        // line stay fractional so letter spacing is not disturbed. A scaled scope keeps fractional
        // positions, or text under an animated scale would move in one-pixel steps.
        val snap = scope.isDevicePixelAligned
        val originX = if (snap) wholePixel(offsetX) else offsetX
        // The line advance and the baseline offset are whole pixels, so snapping the run's top once puts
        // every line's baseline on the grid, each the same distance below the last.
        val originY = (if (snap) wholePixel(offsetY) else offsetY) + glyphTopPx
        // Small text on the pixel grid draws exact-size coverage glyphs 1:1. Anything scaled draws from
        // the distance field, which is what scales.
        val pixels = if (snap) pixelGlyphs else null
        if (pixels != null) {
            for (i in lineLayouts.indices) {
                paintPixelLine(lineLayouts[i].text, scope, color, originX, originY + i * lineHeightPx)
            }
            return
        }
        val pages = font.atlasPages
        val pageCount = pages.size
        val passes = if (pageCount <= 1) 1 else pageCount
        for (p in 0 until passes) {
            val targetPage = if (pageCount <= 1) null else pages[p]
            // Indexed: this runs for every text node every frame, and a List iterator is an allocation.
            for (i in lineLayouts.indices) {
                paintLine(lineLayouts[i].text, scope, color, originX, originY + i * lineHeightPx, targetPage)
            }
        }
    }

    /** [value] rounded half up to a whole pixel. */
    private fun wholePixel(value: Float): Float = floor(value + 0.5f)

    /**
     * [paintLine] for exact-size glyphs: the same pen positions, each glyph the variant for the nearest
     * quarter of a pixel of its pen, with its stem on the pixel grid, on the baseline of its own face rounded
     * to a whole pixel. A code point a face has no
     * pixel glyph for, such as a colour emoji, draws from its atlas as [paintLine] would.
     */
    private fun paintPixelLine(line: String, scope: DrawScope, color: Color, offsetX: Float, lineTop: Float) {
        val pixels = pixelGlyphs ?: return
        val size = glyphPx.toInt()
        var x = offsetX
        var start = 0
        var previous = -1
        while (start < line.length) {
            val end = GraphemeClusters.nextBoundary(line, start)
            // Kerning moves the pen before the subpixel step is chosen, so a kerned pair is spaced as measured.
            x += font.kerningBefore(line, previous, start, glyphPx, style.weight)
            if (kerning) previous = start
            val clusterX = x
            x += font.forEachGlyphInCluster(line, start, end, glyphPx, style.weight) { codePoint, penX ->
                val glyph = pixels.glyph(codePoint, style.weight, size)
                val uv = if (glyph == null) font.glyphFor(codePoint, style.weight) else null
                if (uv != null) {
                    scope.drawGlyph(
                        x = clusterX + penX + uv.offsetXEm * glyphPx,
                        y = lineTop + uv.offsetYEm * glyphPx,
                        width = uv.widthEm * glyphPx,
                        height = uv.heightEm * glyphPx,
                        u0 = uv.u0,
                        v0 = uv.v0,
                        u1 = uv.u1,
                        v1 = uv.v1,
                        color = color,
                        font = font.fontFor(codePoint, style.weight),
                        page = uv.page,
                    )
                } else if (glyph != null && glyph.width > 0) {
                    // The pen lands on the nearest of the glyph's subpixel steps, and that step's glyph has its
                    // stem on the pixel grid, so the gaps in a run follow layout and the stems stay crisp.
                    val steps = glyph.subpixelSteps
                    val pen = clusterX + penX
                    var drawn = glyph
                    var wholeX = wholePixel(pen)
                    if (steps > 1) {
                        val step = floor(pen * steps + 0.5f)
                        wholeX = floor(step / steps)
                        drawn = pixels.glyph(codePoint, style.weight, size, (step - wholeX * steps).toInt()) ?: glyph
                    }
                    scope.drawGlyph(
                        x = wholeX + drawn.left,
                        y = wholePixel(lineTop + drawn.baselineEm * glyphPx) + drawn.top,
                        width = drawn.width.toFloat(),
                        height = drawn.height.toFloat(),
                        u0 = drawn.u0,
                        v0 = drawn.v0,
                        u1 = drawn.u1,
                        v1 = drawn.v1,
                        color = color,
                        font = drawn.atlas,
                    )
                }
            } + letterSpacing
            start = end
        }
    }

    private fun paintLine(
        line: String,
        scope: DrawScope,
        color: Color,
        offsetX: Float,
        y: Float,
        targetPage: UiFont? = null,
    ) {
        var x = offsetX
        var start = 0
        var previous = -1
        while (start < line.length) {
            val end = GraphemeClusters.nextBoundary(line, start)
            x += font.kerningBefore(line, previous, start, glyphPx, style.weight)
            if (kerning) previous = start
            val clusterX = x
            x += font.forEachGlyphInCluster(line, start, end, glyphPx, style.weight) { codePoint, penX ->
                val glyphFont = font.fontFor(codePoint, style.weight)
                if (targetPage == null || glyphFont === targetPage) {
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
                            font = glyphFont,
                            page = uv.page,
                        )
                    }
                }
            } + letterSpacing
            start = end
        }
    }

    /**
     * The pen distance of `value[start until end]`, whole clusters only: one that [end] falls inside
     * is left out. [start] is a cluster boundary, as a line's start is, and nothing kerns with the
     * cluster there. Each cluster kerns with the next one before [lineEnd], the last included: a caret
     * at [end] sits where the next cluster's pen starts, and a line measured to its end has nothing after it.
     */
    private fun widthBetween(value: String, start: Int, end: Int, lineEnd: Int = end): Float {
        var x = 0f
        var clusterStart = start
        while (clusterStart < end) {
            val clusterEnd = GraphemeClusters.nextBoundary(value, clusterStart)
            if (clusterEnd > end) break
            x += font.clusterAdvance(value, clusterStart, clusterEnd, glyphPx, style.weight) + letterSpacing +
                kerningAfter(value, clusterStart, clusterEnd, lineEnd)
            clusterStart = clusterEnd
        }
        return x
    }

    /** The kerning between the cluster at [start] and the one at [next], or 0 when [next] is not before [lineEnd]. */
    private fun kerningAfter(value: String, start: Int, next: Int, lineEnd: Int): Float =
        if (kerning && next < lineEnd) font.kerningBefore(value, start, next, glyphPx, style.weight) else 0f
}

internal data class TextCaretPosition(val x: Float, val y: Float)

// Unit steps to the eight neighbours; the diagonals are shortened so the ring is round, not square.
private val OUTLINE_X = floatArrayOf(1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f, 0f, 0.7071f)
private val OUTLINE_Y = floatArrayOf(0f, 0.7071f, 1f, 0.7071f, 0f, -0.7071f, -1f, -0.7071f)
