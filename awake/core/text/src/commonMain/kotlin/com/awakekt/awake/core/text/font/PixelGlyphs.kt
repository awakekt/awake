/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Glyphs rasterized from a font's outlines at an exact whole-pixel size, drawn 1:1 for small text.
 *
 * A distance field is built for text that scales. At 11 to 14 pixels its anti-aliased edge is about as
 * wide as a stroke, so a stem draws as two or three grey pixels. These glyphs are the area each pixel of
 * the outline covers, computed at the size the text is drawn at, so a one-pixel stem is one solid pixel
 * when it lands on the grid, and they draw with linear-light blending ([UiFont.linearCoverage]).
 *
 * Each glyph comes in [PixelGlyph.subpixelSteps] variants, one for each fraction of a pixel the pen can
 * sit at. In every variant the main stem is on the pixel grid and the rest of the glyph is where the pen's
 * fraction puts it, so a run's gaps follow layout instead of the rounding of each stem.
 *
 * A text renderer asks for them when [UiFont.pixelGlyphs] is not null, its size in device pixels is a
 * whole number no larger than [UiFont.pixelGlyphMaxSize], and the line's origin and baseline sit on
 * whole device pixels; otherwise, such as under a scale animation, it draws from the distance field.
 */
fun interface PixelGlyphs {
    /**
     * [codePoint]'s glyph at [weight], rasterized at [pixelSize] device pixels, or the font's stand-in
     * when it has no glyph for it; null when [pixelSize] is out of range. This is the variant for a pen
     * on a whole pixel.
     *
     * The first request for a size rasterizes the block of glyphs [codePoint] belongs to and keeps it, so
     * the cost is paid once per font and size and later calls only look the glyph up. Not thread-safe:
     * call it from the thread that draws the UI.
     */
    fun glyph(codePoint: Int, weight: FontWeight, pixelSize: Int): PixelGlyph?

    /**
     * [glyph] for a pen [subpixel] steps right of a whole pixel, each step `1 / PixelGlyph.subpixelSteps`
     * of one. A source without variants answers [glyph] for every step. Not thread-safe, like [glyph].
     *
     * @param codePoint The character to draw.
     * @param weight The weight to draw it at.
     * @param pixelSize The size, in device pixels.
     * @param subpixel A step in `0 until PixelGlyph.subpixelSteps` of the glyph [glyph] answers for the same arguments.
     */
    fun glyph(codePoint: Int, weight: FontWeight, pixelSize: Int, subpixel: Int): PixelGlyph? =
        glyph(codePoint, weight, pixelSize)

    /** Bounds on the sizes [glyph] serves. */
    companion object {
        /** The largest size, in device pixels, any font rasterizes pixel glyphs at. */
        const val MAX_PIXEL_SIZE: Int = 128
    }
}

/**
 * One glyph rasterized at a whole-pixel size for one position of the pen across a pixel: its bitmap's place
 * in [atlas] and where it sits.
 *
 * A renderer picks the variant from the pen's fraction. With `steps = subpixelSteps`, a pen at `penX` is
 * step `q = floor(penX * steps + 0.5)`, the nearest of `steps` positions across each pixel; the glyph is
 * [PixelGlyphs.glyph] for variant `q mod steps`, drawn at `x = floor(q / steps) + left` and
 * `y = round(lineTop + baselineEm * size) + top`, [width] by [height] device pixels, with no scaling. It
 * then sits within `1 / (2 * steps)` of a pixel of where layout puts the pen, apart from its main stem,
 * which is on the pixel grid whichever variant that is: moved by under half a pixel, as light hinting does.
 *
 * @property atlas The page to draw the bitmap from: a [UiFontSamplingMode.CoverageAlpha] font that
 *   renderers upload once, like any other atlas. All of a block's glyphs and variants share one page.
 * @property subpixelSteps How many positions across a pixel the glyph's pen is told apart at: 1 for a glyph
 *   with no ink, such as a space, whose position does not matter.
 * @property left The bitmap's left edge, in pixels, from the whole pixel the pen is on; negative when it hangs left.
 * @property top The bitmap's top edge, in pixels, from the baseline; negative above it.
 * @property width The bitmap's width in pixels; 0 for a glyph with no ink, such as a space.
 * @property height The bitmap's height in pixels.
 * @property baselineEm The glyph's face's baseline below its line top, in em: the face's [UiFont.ascentEm].
 * @property u0 The bitmap's left edge in [atlas], as a fraction of its width.
 * @property v0 The bitmap's top edge in [atlas], as a fraction of its height.
 * @property u1 The bitmap's right edge in [atlas], as a fraction of its width.
 * @property v1 The bitmap's bottom edge in [atlas], as a fraction of its height.
 */
// One value per field a renderer reads: a glyph is drawn every frame, so its numbers are held flat
// rather than in a rect and a UV pair that would each be another object.
@Suppress("LongParameterList")
class PixelGlyph internal constructor(
    val atlas: UiFont,
    val subpixelSteps: Int,
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val baselineEm: Float,
    val u0: Float,
    val v0: Float,
    val u1: Float,
    val v1: Float,
) {
    /**
     * Where the glyph's main stem starts, measured from the pen, in the whole-pixel placement
     * `round(penX + anchorX) + left` that variants replaced: always 0, which is right for variant 0, the
     * glyph a pen on a whole pixel draws, with its stem already on the grid. A renderer that still reads it
     * keeps working, drawing variant 0 at the rounded pen.
     */
    @Deprecated(
        message = "The subpixel variants replace it: pick the variant for the pen's fraction, see subpixelSteps. " +
            "It is always 0 and will be removed in a later minor.",
        replaceWith = ReplaceWith("0f"),
    )
    val anchorX: Float get() = 0f
}

/**
 * [PixelGlyphs] for one face, from the outlines of [outlines].
 *
 * The face's glyphs are numbered in [codePoints] order and rasterized in blocks of [BLOCK_SIZE]: a size's
 * block is one atlas page, built the first time any of its glyphs is asked for, with every subpixel variant
 * of every glyph in it: a renderer draws a run from one page, and a page is uploaded once, so it cannot
 * grow later. A face with a few hundred glyphs is one or two pages a size; a large CJK face builds only the
 * blocks its text uses.
 *
 * @param outlines The face's outlines, read once a glyph at a time and kept.
 * @param codePoints The code points the face draws, in its glyph order.
 * @param fallbackCode The code point a missing one draws as.
 * @param baselineEm The face's baseline below its line top, in em, so its glyphs line up with its atlas glyphs.
 * @param subpixelSteps How many positions across a pixel each glyph is rasterized at.
 */
internal class OutlinePixelGlyphs(
    private val outlines: OpenTypeFont,
    private val codePoints: IntArray,
    fallbackCode: Int,
    private val baselineEm: Float,
    private val subpixelSteps: Int = SUBPIXEL_STEPS,
) : PixelGlyphs {
    private val slotByCode: Map<Int, Int> = HashMap<Int, Int>(codePoints.size * 2).also { map ->
        for (slot in codePoints.indices) map.getOrPut(codePoints[slot]) { slot }
    }
    private val fallbackSlot: Int = slotByCode[fallbackCode] ?: 0
    private val glyphIds = IntArray(codePoints.size) { outlines.glyphFor(codePoints[it]) }
    private val outlineCache = arrayOfNulls<List<OutlineSegment>>(codePoints.size)
    private val scalePerUnit = 1.0 / outlines.unitsPerEm
    private val hinting = VerticalHinting.measure(outlines)

    /** Each size's pages, by block; null until the size is first drawn. */
    private val sizes = arrayOfNulls<Array<Array<PixelGlyph>?>>(PixelGlyphs.MAX_PIXEL_SIZE + 1)
    private val blockCount = (codePoints.size + BLOCK_SIZE - 1) / BLOCK_SIZE

    override fun glyph(codePoint: Int, weight: FontWeight, pixelSize: Int): PixelGlyph? = glyph(codePoint, weight, pixelSize, 0)

    override fun glyph(codePoint: Int, weight: FontWeight, pixelSize: Int, subpixel: Int): PixelGlyph? {
        if (pixelSize !in 1..PixelGlyphs.MAX_PIXEL_SIZE || codePoints.isEmpty()) return null
        val slot = slotByCode[codePoint] ?: fallbackSlot
        val blocks = sizes[pixelSize] ?: arrayOfNulls<Array<PixelGlyph>?>(blockCount).also { sizes[pixelSize] = it }
        val block = slot / BLOCK_SIZE
        val glyphs = blocks[block] ?: buildBlock(pixelSize, block).also { blocks[block] = it }
        val count = glyphs.size / subpixelSteps
        return glyphs[subpixel.coerceIn(0, subpixelSteps - 1) * count + slot - block * BLOCK_SIZE]
    }

    private fun outlineOf(slot: Int): List<OutlineSegment> =
        outlineCache[slot] ?: outlines.outline(glyphIds[slot]).also { outlineCache[slot] = it }

    /**
     * Rasterizes block [block] at [pixelSize] into one page and returns its glyphs: [subpixelSteps] runs of
     * one glyph per slot, in slot order, the variant at step `v` of slot `i` at `v * count + i`. A glyph
     * with no ink has its one glyph in every run.
     */
    private fun buildBlock(pixelSize: Int, block: Int): Array<PixelGlyph> {
        val first = block * BLOCK_SIZE
        val count = minOf(BLOCK_SIZE, codePoints.size - first)
        val scale = pixelSize * scalePerUnit
        val heights = hinting.at(scale)
        val rasterizer = CoverageRasterizer()
        val lines = LineBuffer()
        val bitmaps = ArrayList<GlyphBitmap>(count * subpixelSteps)
        // The bitmap each glyph and step draws, by index into bitmaps.
        val bitmapAt = IntArray(count * subpixelSteps)
        for (index in 0 until count) {
            val outline = outlineOf(first + index)
            val mapY = heights.snappingBars(horizontalBars(outline, outlines.unitsPerEm.toDouble()), scale)
            for (step in 0 until subpixelSteps) {
                val bitmap = if (step > 0 && bitmaps[bitmapAt[index]].width == 0) {
                    bitmaps[bitmapAt[index]]
                } else {
                    rasterizeGlyph(outline, scale, mapY, rasterizer, lines, subpixel = step.toDouble() / subpixelSteps)
                }
                // A step that draws what an earlier one does, as a stem's does until the pen crosses the half
                // pixel that moves the stem, shares its bitmap.
                val same = (0 until step).firstOrNull { bitmaps[bitmapAt[it * count + index]].sameAs(bitmap) }
                if (same != null) {
                    bitmapAt[step * count + index] = bitmapAt[same * count + index]
                } else {
                    bitmapAt[step * count + index] = bitmaps.size
                    bitmaps += bitmap
                }
            }
        }
        val page = PixelGlyphPage.pack(bitmaps.toTypedArray(), pixelSize)
        val glyphs = arrayOfNulls<PixelGlyph>(bitmaps.size)
        return Array(count * subpixelSteps) { at ->
            val index = bitmapAt[at]
            glyphs[index] ?: pixelGlyph(page, bitmaps[index], index).also { glyphs[index] = it }
        }
    }

    private fun pixelGlyph(page: PixelGlyphPage, bitmap: GlyphBitmap, index: Int): PixelGlyph = PixelGlyph(
        atlas = page,
        subpixelSteps = if (bitmap.width > 0) subpixelSteps else 1,
        left = bitmap.left,
        top = bitmap.top,
        width = bitmap.width,
        height = bitmap.height,
        baselineEm = baselineEm,
        u0 = page.placedX[index].toFloat() / page.atlasWidth,
        v0 = page.placedY[index].toFloat() / page.atlasHeight,
        u1 = (page.placedX[index] + bitmap.width).toFloat() / page.atlasWidth,
        v1 = (page.placedY[index] + bitmap.height).toFloat() / page.atlasHeight,
    )

    companion object {
        /** Glyphs per atlas page. */
        const val BLOCK_SIZE: Int = 128

        /** Positions across a pixel each glyph is rasterized at. */
        const val SUBPIXEL_STEPS: Int = 4
    }
}

/** One glyph's coverage mask and where it sits, before it is packed into a page. */
internal class GlyphBitmap(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val mask: ByteArray,
) {
    /** Whether [other] is the same bitmap in the same place. */
    fun sameAs(other: GlyphBitmap): Boolean =
        left == other.left && top == other.top && width == other.width && height == other.height && mask.contentEquals(other.mask)
}

/**
 * Rasterizes [outline] (font units) at [scale] pixels per unit, with [mapY] the vertical hinting, for a pen
 * [subpixel] of a pixel (0 up to 1) right of the whole pixel the bitmap's [GlyphBitmap.left] counts from.
 *
 * The glyph's main stem, its longest vertical line, is moved onto a whole pixel: the bitmap is drawn with
 * the stem's left edge on a pixel boundary, whatever [subpixel] is, and the glyph's other parts keep the
 * places the pen puts them, joined to the stem by a short ramp ([StemSnap]). Moving a stem by under half a
 * pixel is what light hinting does, without changing its width or the glyph's advance.
 */
internal fun rasterizeGlyph(
    outline: List<OutlineSegment>,
    scale: Double,
    mapY: (Double) -> Double,
    rasterizer: CoverageRasterizer,
    lines: LineBuffer,
    alignStem: Boolean = true,
    subpixel: Double = 0.0,
): GlyphBitmap {
    val bounds = outlineBounds(outline) ?: return EMPTY_BITMAP
    val snap = if (alignStem) StemSnap.of(outline, scale, subpixel) else null
    val minX = bounds[0] * scale
    val maxX = bounds[2] * scale
    val left = floor((snap?.apply(minX) ?: minX) + subpixel).toInt()
    val right = ceil((snap?.apply(maxX) ?: maxX) + subpixel).toInt()
    val top = floor(-mapY(bounds[3] * scale)).toInt()
    val bottom = ceil(-mapY(bounds[1] * scale)).toInt()
    val width = right - left
    val height = bottom - top
    return if (width > 0 && height > 0) {
        // flattenOutline negates y after mapping, so the + top here lands as - top: rows count from the bitmap's top.
        flattenOutline(outline, scale, subpixel - left, { y -> mapY(y) + top }, lines, snap)
        GlyphBitmap(left, top, width, height, rasterizer.rasterize(lines, width, height))
    } else {
        EMPTY_BITMAP
    }
}

/** A glyph with no ink, such as a space. */
private val EMPTY_BITMAP = GlyphBitmap(0, 0, 0, 0, ByteArray(0))

/**
 * A vertical hinting map, from an unhinted height above the baseline to a hinted one, both in pixels.
 *
 * @property pins The heights, in pixels and ascending from the baseline's 0, that the map takes to themselves:
 *   the snapped baseline, x-height and cap height, which a finer snap, such as [snappingBars], must keep.
 * @param map The mapping itself.
 */
internal class VerticalMap(val pins: DoubleArray, private val map: (Double) -> Double) : (Double) -> Double {
    override fun invoke(y: Double): Double = map(y)
}

/**
 * Light vertical hinting: the baseline stays put, and the x-height and cap height each move onto a whole
 * pixel, with everything between stretched to fit. Flat tops then fill one solid row instead of two grey
 * ones; descenders are left as they are, and ascenders above the cap height move with it.
 *
 * The heights come from the font's own `x` and `H`, whose tops are flat. A face without them is not hinted.
 */
internal class VerticalHinting private constructor(private val xHeightUnits: Double, private val capHeightUnits: Double) {
    /** The mapping from an unhinted height above the baseline to a hinted one, both in pixels, at [scale] pixels per unit. */
    fun at(scale: Double): VerticalMap {
        if (xHeightUnits <= 0.0 || capHeightUnits <= xHeightUnits) return IDENTITY
        val xHeight = xHeightUnits * scale
        val capHeight = capHeightUnits * scale
        val snappedX = max(1.0, floor(xHeight + HALF))
        val snappedCap = max(snappedX + 1.0, floor(capHeight + HALF))
        val lower = snappedX / xHeight
        val upper = (snappedCap - snappedX) / (capHeight - xHeight)
        val above = snappedCap - capHeight
        return VerticalMap(doubleArrayOf(0.0, snappedX, snappedCap)) { y ->
            when {
                y <= 0.0 -> y
                y <= xHeight -> y * lower
                y <= capHeight -> snappedX + (y - xHeight) * upper
                else -> y + above
            }
        }
    }

    companion object {
        val NONE = VerticalHinting(0.0, 0.0)
        val IDENTITY = VerticalMap(doubleArrayOf(0.0)) { it }

        fun measure(font: OpenTypeFont): VerticalHinting {
            val xTop = flatTop(font, 'x'.code)
            val capTop = flatTop(font, 'H'.code)
            return if (xTop == null || capTop == null) NONE else VerticalHinting(xTop, capTop)
        }

        private fun flatTop(font: OpenTypeFont, code: Int): Double? {
            val glyph = font.glyphFor(code)
            if (glyph == 0) return null
            return outlineBounds(font.outline(glyph))?.get(3)
        }
    }
}

/**
 * One atlas page of pixel glyphs: coverage in alpha, white in RGB, packed in rows with a one-pixel gap so
 * a bitmap sampled at its own texel centres never reads a neighbour.
 */
internal class PixelGlyphPage private constructor(
    override val atlasWidth: Int,
    override val atlasHeight: Int,
    override val atlasPixelsRgba: ByteArray,
    override val cellSize: Int,
    val placedX: IntArray,
    val placedY: IntArray,
) : UiFont {
    override val samplingMode: UiFontSamplingMode = UiFontSamplingMode.CoverageAlpha
    override val textScaleStep: Float = 1f
    override val linearCoverage: Boolean = true

    // A page is drawn only through PixelGlyph, never looked up by character.
    override fun uvFor(char: Char): GlyphRect? = null

    override val pixelGlyphs: PixelGlyphs? get() = null

    companion object {
        private const val GAP = 1
        private const val RGBA = 4
        private const val MIN_WIDTH = 64
        private const val MAX_WIDTH = 4096

        /** Packs [bitmaps] in rows, left to right in order, into a page about as wide as it is tall. */
        fun pack(bitmaps: Array<GlyphBitmap>, pixelSize: Int): PixelGlyphPage {
            var area = 0L
            var widest = 0
            for (bitmap in bitmaps) {
                area += (bitmap.width + GAP).toLong() * (bitmap.height + GAP)
                widest = max(widest, bitmap.width + GAP * 2)
            }
            val width = ceil(sqrt(area.toDouble())).toInt().coerceIn(max(MIN_WIDTH, widest), max(MAX_WIDTH, widest))
            val placedX = IntArray(bitmaps.size)
            val placedY = IntArray(bitmaps.size)
            var x = GAP
            var y = GAP
            var rowHeight = 0
            for (index in bitmaps.indices) {
                val bitmap = bitmaps[index]
                if (x + bitmap.width + GAP > width) {
                    x = GAP
                    y += rowHeight + GAP
                    rowHeight = 0
                }
                placedX[index] = x
                placedY[index] = y
                x += bitmap.width + GAP
                rowHeight = max(rowHeight, bitmap.height)
            }
            val height = max(1, y + rowHeight + GAP)
            val pixels = ByteArray(width * height * RGBA)
            for (index in bitmaps.indices) {
                val bitmap = bitmaps[index]
                for (row in 0 until bitmap.height) {
                    for (column in 0 until bitmap.width) {
                        val at = ((placedY[index] + row) * width + placedX[index] + column) * RGBA
                        pixels[at] = -1
                        pixels[at + 1] = -1
                        pixels[at + 2] = -1
                        pixels[at + 3] = bitmap.mask[row * bitmap.width + column]
                    }
                }
            }
            return PixelGlyphPage(width, height, pixels, pixelSize, placedX, placedY)
        }
    }
}

private const val HALF = 0.5
