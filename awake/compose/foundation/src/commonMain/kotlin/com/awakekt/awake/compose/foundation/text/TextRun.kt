/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.ui.graphics.drawscope.DrawScope
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.font.GlyphRect
import com.awakekt.awake.core.text.font.PixelGlyphs
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.core.text.font.UiFontSamplingMode
import com.awakekt.awake.core.text.font.clusterAdvance
import com.awakekt.awake.core.text.font.ellipsize
import com.awakekt.awake.core.text.font.forEachGlyphInCluster
import com.awakekt.awake.core.text.font.kerningBefore
import com.awakekt.awake.core.text.theme.TextStyle
import com.awakekt.awake.core.text.unicode.GraphemeClusters
import com.awakekt.awake.core.text.unicode.WordBreak
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
    font: UiFont,
    private val glyphPx: Float,
    private val density: Float,
    private val emDp: Float = glyphPx / density,
    private val wrapWidthPx: Float = Float.POSITIVE_INFINITY,
    private val singleLine: Boolean = false,
    private val overflow: TextOverflow = TextOverflow.Clip,
    private val softWrap: Boolean = true,
    private val maxLines: Int = Int.MAX_VALUE,
    private val fontScale: Float = 1f,
    private val forceDistanceField: Boolean = false,
) {
    private val font: UiFont = font.withNumerals(style.numerals)
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
        wordBreak = style.wordBreak ?: WordBreak.Normal,
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
    internal val lineHeightPx: Float =
        TextLineBox.advancePx(font, emDp, glyphPx, density, authoredLineHeightPx, forceDistanceField)

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

    /** The style's outline and shadow in device pixels, or null when it has neither. Read once, as the run is laid out. */
    private val effects: GlyphEffects? = GlyphEffects.of(style, density)

    /**
     * [paint], under its style's shadow and outline. A font with an effect field draws each glyph once, its outline and
     * shadow in the same draw, at every size from the distance field, so their weight is the same on both sides of the
     * pixel-glyph limit (#1061), at rest, in a scale or size animation and moving by subpixel steps alike. A font
     * without one stamps the run: in the shadow's colour moved by its offset, then in the outline's moved a step in
     * each of eight directions, then the text.
     */
    fun paintDecorated(
        scope: DrawScope,
        color: Color,
        offsetX: Float = 0f,
        offsetY: Float = 0f,
        motion: TextMotionState? = null,
    ) {
        val plan = motion?.resolvePaint(scope.isScaled, scope.isDevicePixelAligned, offsetX, offsetY)
        val effects = effects
        when {
            effects == null -> paintPass(scope, color, offsetX, offsetY, 0f, 0f, plan)
            font.effectFieldRangePx > 0f && font.samplingMode == UiFontSamplingMode.DistanceField ->
                paintPass(scope, color, offsetX, offsetY, 0f, 0f, plan, effects)
            else -> effects.stamp(color) { stampColor, dx, dy -> paintPass(scope, stampColor, offsetX, offsetY, dx, dy, plan) }
        }
    }

    /**
     * The font's exact-size glyphs, when this run draws from them: its size is a whole number of device
     * pixels no larger than the font's [UiFont.pixelGlyphMaxSize], and no scale/size animation is holding
     * the run on the distance field. Read once, as the run is laid out.
     */
    private val pixelGlyphs: PixelGlyphs? = if (forceDistanceField) {
        null
    } else {
        font.pixelGlyphs?.takeIf {
            glyphPx == floor(glyphPx) && glyphPx >= 1f && glyphPx <= font.pixelGlyphMaxSize
        }
    }

    @Suppress("LongParameterList")
    private fun paintPass(
        scope: DrawScope,
        color: Color,
        baseOffsetX: Float,
        baseOffsetY: Float,
        extraX: Float,
        extraY: Float,
        plan: TextPaintMotion?,
        effects: GlyphEffects? = null,
    ) {
        if (plan == null || (!plan.forceDistanceField && !plan.subpixelTranslated)) {
            val subpixelUnscaled = !scope.isScaled && !forceDistanceField && !scope.isDevicePixelAligned
            val passX = baseOffsetX + if (subpixelUnscaled) wholePixel(extraX) else extraX
            val passY = baseOffsetY + if (subpixelUnscaled) wholePixel(extraY) else extraY
            paint(scope, color, passX, passY, effects)
            return
        }
        if (plan.forceDistanceField) {
            paintFieldLines(scope, color, baseOffsetX + extraX, baseOffsetY + extraY + glyphTopPx, effects)
            return
        }
        // At unit scale, resting passes snap their decoration offset (`wholePixel(extraX)`), so keeping
        // that snapped ring/shadow offset while translating by `residualX` moves fill, shadow, and outline
        // as one rigid unit without popping the 8-tap outline ring at integer pixel crossings.
        val originX = wholePixel(baseOffsetX) + wholePixel(extraX) + plan.residualX
        val originY = wholePixel(baseOffsetY) + wholePixel(extraY) + plan.residualY
        if (pixelGlyphs != null && effects == null) {
            paintSubpixelPixelLines(scope, color, originX, originY)
        } else {
            paintFieldLines(scope, color, originX, originY + glyphTopPx, effects)
        }
    }

    private fun paintSubpixelPixelLines(scope: DrawScope, color: Color, originX: Float, originY: Float) {
        for (i in lineLayouts.indices) paintSubpixelPixelLine(lineLayouts[i].text, scope, color, originX, originY, i)
    }

    fun paint(scope: DrawScope, color: Color, offsetX: Float = 0f, offsetY: Float = 0f) = paint(scope, color, offsetX, offsetY, null)

    private fun paint(scope: DrawScope, color: Color, offsetX: Float, offsetY: Float, effects: GlyphEffects?) {
        // A distance-field glyph is antialiased by its coverage at the pixel it lands on, so a stem
        // that straddles two pixel columns is two grey columns instead of one solid one. Whole
        // device pixels are what the line's start and baseline snap to; the advances inside the
        // line stay fractional so letter spacing is not disturbed. A scaled scope keeps fractional
        // positions, or text under an animated scale would move in one-pixel steps.
        val scaled = scope.isScaled || forceDistanceField
        val aligned = scope.isDevicePixelAligned && !forceDistanceField
        // Decorated text at rest draws from the distance field at every size (#1061), never the pixel glyphs here.
        val pixelRun = pixelGlyphs != null && effects == null
        if (!scaled && !aligned && pixelRun) {
            paintSubpixelPixelLines(scope, color, offsetX, offsetY)
            return
        }
        val snap = aligned
        val originX = if (snap) wholePixel(offsetX) else offsetX
        // The line advance and the baseline offset are whole pixels, so snapping the run's top once puts
        // every line's baseline on the grid, each the same distance below the last.
        val originY = (if (snap) wholePixel(offsetY) else offsetY) + glyphTopPx
        // Small text on the pixel grid draws exact-size coverage glyphs 1:1. Anything scaled draws from
        // the distance field, which is what scales.
        val pixels = if (snap && pixelRun) pixelGlyphs else null
        if (pixels != null) {
            for (i in lineLayouts.indices) {
                paintPixelLine(lineLayouts[i].text, scope, color, originX, originY + i * lineHeightPx)
            }
            return
        }
        paintFieldLines(scope, color, originX, originY, effects)
    }

    private fun paintFieldLines(scope: DrawScope, color: Color, originX: Float, originY: Float, effects: GlyphEffects? = null) {
        val pages = font.atlasPages
        val pageCount = pages.size
        val passes = if (pageCount <= 1) 1 else pageCount
        for (p in 0 until passes) {
            val targetPage = if (pageCount <= 1) null else pages[p]
            // Indexed: this runs for every text node every frame, and a List iterator is an allocation.
            for (i in lineLayouts.indices) {
                paintLine(lineLayouts[i].text, scope, color, originX, originY + i * lineHeightPx, targetPage, effects)
            }
        }
    }

    /** [value] rounded half up to a whole pixel. */
    private fun wholePixel(value: Float): Float = floor(value + 0.5f)

    /**
     * Subpixel-translated sibling of [paintPixelLine]: selects the exact same run-local pixel-glyph
     * variant as [paintPixelLine] at rest so stems never re-hint or snap to new columns mid-motion,
     * and expands each quad by half a transparent border texel on all four sides (`PixelGlyphPage`'s
     * 1-texel gap) so linear atlas filtering shifts ink smoothly across fractional pixel steps while
     * matching [paintPixelLine] bit-for-bit at every whole-pixel offset.
     */
    @Suppress("LongParameterList")
    private fun paintSubpixelPixelLine(
        line: String,
        scope: DrawScope,
        color: Color,
        originX: Float,
        originYOffset: Float,
        lineIndex: Int,
    ) {
        val pixels = pixelGlyphs ?: return
        val size = glyphPx.toInt()
        val localLineTop = glyphTopPx + lineIndex * lineHeightPx
        var localX = 0f
        var start = 0
        var previous = -1
        while (start < line.length) {
            val end = GraphemeClusters.nextBoundary(line, start)
            localX += font.kerningBefore(line, previous, start, glyphPx, style.weight)
            if (kerning) previous = start
            val clusterLocalX = localX
            localX += font.forEachGlyphInCluster(line, start, end, glyphPx, style.weight) { codePoint, penX ->
                val glyph = pixels.glyph(codePoint, style.weight, size)
                val uv = if (glyph == null) font.glyphFor(codePoint, style.weight) else null
                if (uv != null) {
                    scope.drawPlainGlyph(uv, originX + clusterLocalX + penX, originYOffset + localLineTop, color, font.fontFor(codePoint, style.weight))
                } else if (glyph != null && glyph.width > 0) {
                    val steps = glyph.subpixelSteps
                    val localPen = clusterLocalX + penX
                    var drawn = glyph
                    var localWholeX = wholePixel(localPen)
                    if (steps > 1) {
                        val step = floor(localPen * steps + 0.5f)
                        localWholeX = floor(step / steps)
                        drawn = pixels.glyph(codePoint, style.weight, size, (step - localWholeX * steps).toInt()) ?: glyph
                    }
                    val localTopY = wholePixel(localLineTop + drawn.baselineEm * glyphPx) + drawn.top
                    val padU = SUBPIXEL_PAD_TEXELS / drawn.atlas.atlasWidth
                    val padV = SUBPIXEL_PAD_TEXELS / drawn.atlas.atlasHeight
                    scope.drawGlyph(
                        x = originX + localWholeX + drawn.left - SUBPIXEL_PAD_PX,
                        y = originYOffset + localTopY - SUBPIXEL_PAD_PX,
                        width = drawn.width + SUBPIXEL_PAD_PX * 2f,
                        height = drawn.height + SUBPIXEL_PAD_PX * 2f,
                        u0 = drawn.u0 - padU,
                        v0 = drawn.v0 - padV,
                        u1 = drawn.u1 + padU,
                        v1 = drawn.v1 + padV,
                        color = color,
                        font = drawn.atlas,
                    )
                }
            } + letterSpacing
            start = end
        }
    }

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
                    scope.drawPlainGlyph(uv, clusterX + penX, lineTop, color, font.fontFor(codePoint, style.weight))
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
        effects: GlyphEffects? = null,
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
                    val pen = clusterX + penX
                    // A blank glyph, a space, has nothing to decorate, and no effect tile.
                    val inked = uv != null && uv.widthEm > 0f && uv.heightEm > 0f
                    if (inked && effects != null) {
                        val drawn = effects.draw(scope, font, glyphFont, codePoint, style.weight, uv, pen, y, glyphPx, color)
                        if (!drawn) {
                            effects.stamp(color) { stampColor, dx, dy -> scope.drawPlainGlyph(uv, pen + dx, y + dy, stampColor, glyphFont) }
                        }
                    } else if (uv != null) {
                        scope.drawPlainGlyph(uv, pen, y, color, glyphFont)
                    }
                }
            } + letterSpacing
            start = end
        }
    }

    /** [uv] with its pen at [pen] on the line whose em box starts at [lineTop], as [paintLine] draws a plain glyph. */
    private fun DrawScope.drawPlainGlyph(uv: GlyphRect, pen: Float, lineTop: Float, color: Color, glyphFont: UiFont) {
        drawGlyph(
            x = pen + uv.offsetXEm * glyphPx,
            y = lineTop + uv.offsetYEm * glyphPx,
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

// Half-pixel quad/UV pad into PixelGlyphPage's 1-texel transparent border for subpixel translation.
private const val SUBPIXEL_PAD_PX = 0.5f
private const val SUBPIXEL_PAD_TEXELS = 0.5f
