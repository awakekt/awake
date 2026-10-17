/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.text.unicode.CodePoints
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A distance-field atlas baked from a TrueType or OpenType file at run time, in the layout and
 * encoding `:awake:ui:font-atlas-generator` gives the built-in Roboto, so [PackedUiFont] and the
 * glyph shader read it unchanged.
 *
 * The generator stores msdfgen's MTSDF: a multi-channel distance in RGB and a true signed distance
 * in A. This stores one exact signed distance in all four channels. The shader draws the median of
 * R, G and B, and the median of three equal values is that distance, so it draws this atlas as it
 * is, with corners a little rounder than a multi-channel field keeps them.
 *
 * Only basic arithmetic and square roots touch a texel, and those round the same on every target,
 * so the same file gives the same bytes on desktop, Android, iOS and the web.
 */
internal class OpenTypeUiFontData private constructor(
    override val name: String,
    override val glyphOrder: String,
    private val layout: GlyphLayout,
    private val atlas: ByteArray,
) : PackedUiFontData {
    override val lineHeightEm: Float = (layout.ascentPx + layout.descentPx) / OVERSAMPLE / LOGICAL_CELL
    override val atlasWidth: Int get() = layout.atlasWidth
    override val atlasHeight: Int get() = layout.atlasHeight
    override val baseCellSize: Int = LOGICAL_CELL
    override val textScaleStep: Float = TEXT_SCALE_STEP
    override val samplingMode: UiFontSamplingMode = UiFontSamplingMode.DistanceField
    override val fallbackChar: Char = FALLBACK_CHAR
    override val atlasChannels: Int = RGBA
    override val distanceFieldRangePx: Float = DISTANCE_FIELD_RANGE_PX.toFloat()
    override val encodedAtlasBase64: String = ""
    override val uvBoundsPx: IntArray get() = layout.uvBoundsPx
    override val quadMetricsEm: FloatArray get() = layout.quadMetricsEm
    override val inkMetricsEm: FloatArray get() = layout.inkMetricsEm
    override val advancesEm: FloatArray get() = layout.advancesEm

    override fun atlasBytes(): ByteArray = atlas

    companion object {
        /**
         * Reads [bytes] and bakes the glyphs of [chars] that the font has, plus [FALLBACK_CHAR],
         * which every other char falls back to. [chars] is read by code point, so a surrogate pair
         * such as a CJK Extension B ideograph is one glyph. The font's `.notdef` stands in for a font with no
         * fallback glyph of its own. [name] only labels errors and the data.
         *
         * @throws IllegalArgumentException when [bytes] are not a font this reads, naming the table at fault.
         */
        fun load(bytes: ByteArray, name: String, chars: String): OpenTypeUiFontData {
            val font = OpenTypeFont(bytes, name)
            val picked = LinkedHashMap<Int, Int>()
            var index = 0
            while (index < chars.length) {
                val codePoint = CodePoints.at(chars, index)
                val glyph = font.glyphFor(codePoint)
                if (glyph != 0) picked.getOrPut(codePoint) { glyph }
                index += CodePoints.charCount(codePoint)
            }
            if (FALLBACK_CHAR.code !in picked) picked[FALLBACK_CHAR.code] = font.glyphFor(FALLBACK_CHAR.code)
            val glyphs = picked.map { (codePoint, glyph) -> FontGlyph(codePoint, font, glyph) }
            val layout = GlyphLayout(font, glyphs)
            require(layout.atlasWidth <= MAX_ATLAS_SIZE && layout.atlasHeight <= MAX_ATLAS_SIZE) {
                "$name: ${glyphs.size} glyphs need a ${layout.atlasWidth}x${layout.atlasHeight} atlas, over $MAX_ATLAS_SIZE"
            }
            return OpenTypeUiFontData(name, glyphOrderOf(glyphs), layout, layout.bakeAtlas(glyphs, name))
        }
    }
}

// The generator's constants (`awake/ui/font-atlas-generator` Main.kt); a change there is a change here.
private const val LOGICAL_CELL = 16
private const val OVERSAMPLE = 2
private const val TEXELS_PER_EM = LOGICAL_CELL * OVERSAMPLE
private const val PADDING = 6
private const val COLUMNS = 16
private const val CROP_BLEED = 1
private const val BLANK_GLYPH_DIVISOR = 4
private const val DISTANCE_FIELD_RANGE_PX = 2
private const val TEXT_SCALE_STEP = 0.25f
private const val FALLBACK_CHAR = '?'
private const val RGBA = 4
private const val BYTE_MAX = 255.0

/** Texels baked times their glyph's segments: about 400 times the built-in set's 1.2 million, or seconds of work. */
private const val MAX_BAKE_WORK = 500_000_000L

/** WebGPU's default texture size limit, the smallest a backend here guarantees. */
private const val MAX_ATLAS_SIZE = 8192

/** The distance an atlas texel can hold, either side of the outline: half of [DISTANCE_FIELD_RANGE_PX]. */
private const val FIELD_REACH = DISTANCE_FIELD_RANGE_PX / 2.0

/** The code points of [glyphs] as one string, a surrogate pair for each past U+FFFF. */
private fun glyphOrderOf(glyphs: List<FontGlyph>): String = buildString {
    for (glyph in glyphs) append(CodePoints.asString(glyph.codePoint))
}

/** One code point's glyph: its outline and its outline-exact box, both in font units, y up. */
private class FontGlyph(val codePoint: Int, font: OpenTypeFont, glyph: Int) {
    val outline: List<OutlineSegment> = font.outline(glyph)
    val advance: Int = font.advance(glyph)

    /** minX, minY, maxX, maxY, or null for a glyph with no ink. */
    val bounds: DoubleArray? = outlineBounds(outline)
}

/**
 * Where each glyph sits in the atlas and what it measures, computed as the generator computes it:
 * same cells, same sample rects, same em values. A glyph whose ink reaches past the generator's
 * padding (a script font's swash, say) widens every cell rather than being cut off; no built-in
 * face does.
 */
private class GlyphLayout(private val font: OpenTypeFont, glyphs: List<FontGlyph>) {
    private val unitsPerEm = font.unitsPerEm.toDouble()
    val ascentPx: Float = TEXELS_PER_EM.toFloat() * font.ascender / font.unitsPerEm
    val descentPx: Float = TEXELS_PER_EM.toFloat() * -font.descender / font.unitsPerEm
    val inkMetricsEm = FloatArray(glyphs.size * 4)
    val advancesEm = FloatArray(glyphs.size) { glyphs[it].advance / unitsPerEm.toFloat() }
    private val originX: Int
    private val originY: Int
    private val cellWidth: Int
    private val cellHeight: Int
    private val columns: Int
    val atlasWidth: Int
    val atlasHeight: Int
    val uvBoundsPx = IntArray(glyphs.size * 4)
    val quadMetricsEm = FloatArray(glyphs.size * 4)

    init {
        glyphs.forEachIndexed { index, glyph ->
            val bounds = glyph.bounds ?: return@forEachIndexed
            inkMetricsEm[index * 4] = (bounds[0] / unitsPerEm).toFloat()
            inkMetricsEm[index * 4 + 1] = ((font.ascender - bounds[3]) / unitsPerEm).toFloat()
            inkMetricsEm[index * 4 + 2] = ((bounds[2] - bounds[0]) / unitsPerEm).toFloat()
            inkMetricsEm[index * 4 + 3] = ((bounds[3] - bounds[1]) / unitsPerEm).toFloat()
        }
        val inked = glyphs.indices.filter { glyphs[it].bounds != null }
        fun extreme(pick: (Int) -> Float, largest: Boolean): Float =
            inked.map(pick).let { if (largest) it.maxOrNull() else it.minOrNull() } ?: 0f
        // The sample rect's bleed plus one texel, so bilinear sampling at a rect's edge reads its own cell.
        val edge = CROP_BLEED + 1f
        val maxAdvancePx = glyphs.indices.filter { glyphs[it].codePoint != ' '.code }.maxOfOrNull { advancesEm[it] * TEXELS_PER_EM } ?: 0f
        originX = max(PADDING, ceil(edge - extreme({ inkMetricsEm[it * 4] * TEXELS_PER_EM }, largest = false)).toInt())
        originY = max(PADDING, ceil(edge - extreme({ inkMetricsEm[it * 4 + 1] * TEXELS_PER_EM }, largest = false)).toInt())
        val inkRight = extreme({ (inkMetricsEm[it * 4] + inkMetricsEm[it * 4 + 2]) * TEXELS_PER_EM }, largest = true)
        val inkBottom = extreme({ (inkMetricsEm[it * 4 + 1] + inkMetricsEm[it * 4 + 3]) * TEXELS_PER_EM }, largest = true)
        cellWidth = originX + max(ceil(maxAdvancePx.toDouble()).toInt() + PADDING, ceil(inkRight + edge).toInt())
        cellHeight = originY + max(ceil((ascentPx + descentPx).toDouble()).toInt() + PADDING, ceil(inkBottom + edge).toInt())
        columns = max(min(glyphs.size, COLUMNS), ceil(sqrt(glyphs.size.toDouble())).toInt())
        atlasWidth = cellWidth * columns
        atlasHeight = cellHeight * ((glyphs.size + columns - 1) / columns)
        glyphs.indices.forEach(::place)
    }

    private fun cellX(index: Int): Int = index % columns * cellWidth

    private fun cellY(index: Int): Int = index / columns * cellHeight

    /** The generator's `sampleRect` and the quad derived from it. */
    private fun place(index: Int) {
        val cellX = cellX(index)
        val cellY = cellY(index)
        val penX = cellX + originX
        val lineTop = cellY + originY
        val at = index * 4
        if (inkMetricsEm[at + 2] <= 0f || inkMetricsEm[at + 3] <= 0f) {
            val placeholder = max(1, TEXELS_PER_EM / BLANK_GLYPH_DIVISOR)
            intArrayOf(penX, lineTop, penX + placeholder, lineTop + placeholder).copyInto(uvBoundsPx, at)
            return
        }
        val rawLeft = penX + inkMetricsEm[at] * TEXELS_PER_EM
        val rawTop = lineTop + inkMetricsEm[at + 1] * TEXELS_PER_EM
        val left = max(cellX, floor(rawLeft - CROP_BLEED).toInt())
        val top = max(cellY, floor(rawTop - CROP_BLEED).toInt())
        val right = min(cellX + cellWidth, ceil(rawLeft + inkMetricsEm[at + 2] * TEXELS_PER_EM + CROP_BLEED).toInt())
        val bottom = min(cellY + cellHeight, ceil(rawTop + inkMetricsEm[at + 3] * TEXELS_PER_EM + CROP_BLEED).toInt())
        intArrayOf(left, top, right, bottom).copyInto(uvBoundsPx, at)
        val scale = TEXELS_PER_EM.toFloat()
        floatArrayOf((left - penX) / scale, (top - lineTop) / scale, (right - left) / scale, (bottom - top) / scale)
            .copyInto(quadMetricsEm, at)
    }

    /**
     * Every glyph's field in its cell, RGBA with the same distance in each channel; texels far outside stay 0.
     *
     * A texel's cost grows with its glyph's segment count, so a file whose glyphs are huge or absurdly
     * detailed is refused up front rather than left to run for minutes.
     */
    fun bakeAtlas(glyphs: List<FontGlyph>, name: String): ByteArray {
        val scale = TEXELS_PER_EM / unitsPerEm
        val regions = glyphs.mapIndexed { index, glyph -> glyph.bounds?.let { region(index, it, scale) } }
        val work = glyphs.indices.sumOf { index ->
            regions[index]?.let { max(0, it[2] - it[0]).toLong() * max(0, it[3] - it[1]) * glyphs[index].outline.size } ?: 0L
        }
        require(work <= MAX_BAKE_WORK) { "$name: its glyphs are too large or too detailed to bake ($work texel-segments)" }
        val atlas = ByteArray(atlasWidth * atlasHeight * RGBA)
        glyphs.forEachIndexed { index, glyph ->
            val region = regions[index] ?: return@forEachIndexed
            val field = GlyphField(glyph.outline, penX(index), baseline(index), scale)
            for (y in region[1] until region[3]) {
                for (x in region[0] until region[2]) {
                    val value = ceil(BYTE_MAX * field.value(x + 0.5, y + 0.5) - 0.5).toInt().coerceIn(0, BYTE_MAX.toInt()).toByte()
                    atlas.fill(value, (y * atlasWidth + x) * RGBA, (y * atlasWidth + x + 1) * RGBA)
                }
            }
        }
        return atlas
    }

    private fun penX(index: Int): Double = (cellX(index) + originX).toDouble()

    private fun baseline(index: Int): Double = cellY(index) + originY + TEXELS_PER_EM * (font.ascender / unitsPerEm)

    /** The texels whose value can differ from plain outside: the ink box and the field's reach around it, within the cell. */
    private fun region(index: Int, bounds: DoubleArray, scale: Double): IntArray {
        val reach = FIELD_REACH + 1
        return intArrayOf(
            max(cellX(index), floor(penX(index) + bounds[0] * scale - reach).toInt()),
            max(cellY(index), floor(baseline(index) - bounds[3] * scale - reach).toInt()),
            min(cellX(index) + cellWidth, ceil(penX(index) + bounds[2] * scale + reach).toInt()),
            min(cellY(index) + cellHeight, ceil(baseline(index) - bounds[1] * scale + reach).toInt()),
        )
    }
}
