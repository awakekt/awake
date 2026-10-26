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

/**
 * A distance-field atlas baked from a TrueType or OpenType file at run time, in the layout and
 * encoding `:awake:ui:font-atlas-generator` gives the built-in Roboto, so [PackedUiFont] and the
 * glyph shader read it unchanged. The generator's grid is 32 texels per em with a 2-texel range
 * ([DistanceFieldBake.GENERATOR]); a project font is baked finer, at [DistanceFieldBake.RUNTIME], on
 * the same layout rules scaled up. The file's outlines are kept for [PackedUiFont.pixelGlyphs].
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
    private val font: OpenTypeFont,
    /** The resolution the atlas was baked at. */
    val bake: DistanceFieldBake,
) : PackedUiFontData {
    override val lineHeightEm: Float = (layout.ascentPx + layout.descentPx) / bake.texelsPerEm
    override val atlasWidth: Int get() = layout.atlasWidth
    override val atlasHeight: Int get() = layout.atlasHeight
    override val baseCellSize: Int = LOGICAL_CELL
    override val textScaleStep: Float = TEXT_SCALE_STEP
    override val samplingMode: UiFontSamplingMode = UiFontSamplingMode.DistanceField
    override val fallbackChar: Char = FALLBACK_CHAR
    override val atlasChannels: Int = RGBA
    override val distanceFieldRangePx: Float = bake.rangeTexels.toFloat()
    override val encodedAtlasBase64: String = ""
    override val uvBoundsPx: IntArray get() = layout.uvBoundsPx
    override val quadMetricsEm: FloatArray get() = layout.quadMetricsEm
    override val inkMetricsEm: FloatArray get() = layout.inkMetricsEm
    override val advancesEm: FloatArray get() = layout.advancesEm

    /** The pairs the font kerns among the baked glyphs, read as the data is made, so a corrupt table fails the load. */
    private val kerning: KerningPairs = KerningPairs.read(font, codePointsOf(glyphOrder))

    override fun atlasBytes(): ByteArray = atlas

    override fun outlineFont(): OpenTypeFont = font

    override fun lineMetrics(): FontLineMetrics = font.lineMetrics

    override fun kerning(): KerningPairs = kerning

    companion object {
        /**
         * Reads [bytes] and bakes the glyphs of [chars] that the font has, plus [FALLBACK_CHAR],
         * which every other char falls back to. [chars] is read by code point, so a surrogate pair
         * such as a CJK Extension B ideograph is one glyph. The font's `.notdef` stands in for a font with no
         * fallback glyph of its own. [name] only labels errors and the data.
         *
         * The pairs the font kerns among the baked glyphs are read here too ([KerningPairs.read]), so a
         * corrupt kerning table fails the load rather than the first layout.
         *
         * The atlas is baked at [bake]. A glyph set whose atlas would pass [MAX_ATLAS_SIZE], or a finer bake
         * whose atlas would pass [MAX_FINE_ATLAS_TEXELS] or whose work would pass [MAX_BAKE_WORK], steps down
         * through [DistanceFieldBake.FALLBACKS]. A finer resolution never accepts a font, or costs more time or
         * memory, than the generator's resolution would, so a font that loaded at 32 texels per em still loads,
         * and a corrupt one fails the same way.
         *
         * @throws IllegalArgumentException when [bytes] are not a font this reads, naming the table at fault.
         */
        fun load(
            bytes: ByteArray,
            name: String,
            chars: String,
            bake: DistanceFieldBake = DistanceFieldBake.RUNTIME,
        ): OpenTypeUiFontData {
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
            val candidates = listOf(bake) + DistanceFieldBake.FALLBACKS.filter { it.texelsPerEm < bake.texelsPerEm }
            var chosen = candidates.last()
            var layout = GlyphLayout(font, glyphs, chosen)
            for (candidate in candidates.dropLast(1)) {
                val trial = GlyphLayout(font, glyphs, candidate)
                if (trial.affordable(glyphs)) {
                    chosen = candidate
                    layout = trial
                    break
                }
            }
            require(layout.fits()) {
                "$name: ${glyphs.size} glyphs need a ${layout.atlasWidth}x${layout.atlasHeight} atlas, over $MAX_ATLAS_SIZE"
            }
            return OpenTypeUiFontData(name, glyphOrderOf(glyphs), layout, layout.bakeAtlas(glyphs, name), font, chosen)
        }
    }
}

/**
 * The resolution a distance-field atlas is baked at: [texelsPerEm] atlas texels to the em, and a field that
 * reaches [rangeTexels] / 2 texels either side of the outline.
 *
 * More texels per em keep thin strokes and corners at large sizes; a wider range keeps the shader's edge
 * one screen pixel wide when the atlas is drawn smaller than it was baked (its `screenPxRange` stays
 * above 1). Texels per em is a multiple of [GENERATOR]'s 32.
 */
internal class DistanceFieldBake(val texelsPerEm: Int, val rangeTexels: Int) {
    /**
     * The texels a glyph's sample rect reaches past its ink. The generator's resolution keeps the generator's one
     * texel, so its tiles stay the built-in atlas's. A finer bake reaches one texel past the field's whole
     * [fieldReach], so a quad's edge samples a field already at zero and its antialiased fringe fades out inside
     * the quad: a rect of one texel per 32 texels per em cut the fringe of a glyph drawn small at a hard edge.
     */
    val cropBleed: Int
        get() = if (texelsPerEm <= GENERATOR_texelsPerEm) 1 else maxOf(texelsPerEm / GENERATOR_texelsPerEm, rangeTexels / 2 + 1)

    /** The distance an atlas texel holds, either side of the outline: half of [rangeTexels]. */
    val fieldReach: Double get() = rangeTexels / 2.0

    companion object {
        private const val GENERATOR_texelsPerEm = 32

        /**
         * What `:awake:ui:font-atlas-generator` bakes the built-in Roboto at: its `LOGICAL_CELL` 16 times
         * `OVERSAMPLE` 2, and `-pxrange 2`. A change there is a change here.
         */
        val GENERATOR = DistanceFieldBake(GENERATOR_texelsPerEm, 2)

        /** What [UiFonts.load] bakes a project font at: 64 texels per em and an 8-texel range. */
        val RUNTIME = DistanceFieldBake(64, 8)

        /** The resolutions a glyph set too large for its atlas steps down through, largest first. */
        val FALLBACKS = listOf(DistanceFieldBake(48, 6), GENERATOR)
    }
}

// The generator's constants (`awake/ui/font-atlas-generator` Main.kt); a change there is a change here.
private const val LOGICAL_CELL = 16
private const val TILE_MARGIN = 2
private const val BLANK_GLYPH_DIVISOR = 4
private const val TEXT_SCALE_STEP = 0.25f
private const val FALLBACK_CHAR = '?'
private const val RGBA = 4
private const val BYTE_MAX = 255.0

/**
 * Texels baked times their glyph's segments: about 400 times the built-in set's 1.2 million at 32 texels per
 * em, or seconds of work. A finer bake that would pass it steps down rather than taking longer.
 */
private const val MAX_BAKE_WORK = 500_000_000L

/**
 * The most texels a finer-than-generator atlas may have, 64 MB of RGBA. A finer bake that would pass it steps
 * down, so a large glyph set (or a corrupt font's huge glyphs) costs no more memory than the generator's
 * resolution allows for it.
 */
private const val MAX_FINE_ATLAS_TEXELS = 4096L * 4096L

/** WebGPU's default texture size limit, the smallest a backend here guarantees. */
private const val MAX_ATLAS_SIZE = 8192

/** The code points of [glyphs] as one string, a surrogate pair for each past U+FFFF. */
private fun glyphOrderOf(glyphs: List<FontGlyph>): String = buildString {
    for (glyph in glyphs) append(CodePoints.asString(glyph.codePoint))
}

/** The code points of [text], a surrogate pair as one. */
private fun codePointsOf(text: String): IntArray {
    val codes = ArrayList<Int>(text.length)
    CodePoints.forEach(text) { _, code -> codes += code }
    return codes.toIntArray()
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
 * same sample rects, same em values, same tiles packed in the same shelves ([ShelfPacking]). A glyph's tile
 * is its sample rect and [TILE_MARGIN] texels around it, so a glyph whose ink reaches far (a script font's
 * swash, say) widens only its own tile rather than being cut off, and an atlas holds the ink and little else.
 */
private class GlyphLayout(private val font: OpenTypeFont, glyphs: List<FontGlyph>, private val bake: DistanceFieldBake) {
    private val texelsPerEm = bake.texelsPerEm
    private val cropBleed = bake.cropBleed
    private val unitsPerEm = font.unitsPerEm.toDouble()
    val ascentPx: Float = texelsPerEm.toFloat() * font.ascender / font.unitsPerEm
    val descentPx: Float = texelsPerEm.toFloat() * -font.descender / font.unitsPerEm
    val inkMetricsEm = FloatArray(glyphs.size * 4)
    val advancesEm = FloatArray(glyphs.size) { glyphs[it].advance / unitsPerEm.toFloat() }

    /** Each glyph's sample rect (left, top, right, bottom) in whole texels from its pen origin and line top. */
    private val rects = IntArray(glyphs.size * 4)

    /** Whether every tile, and all of them together, could be an atlas at all. */
    private val tilesFit: Boolean
    private val tileXs: IntArray
    private val tileYs: IntArray
    private val tileWidths = IntArray(glyphs.size)
    private val tileHeights = IntArray(glyphs.size)
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
        glyphs.indices.forEach(::sampleRect)
        // A corrupt font's extreme coordinates can overflow a tile's size into one that looks small, or
        // negative, so each is checked on its own, as a Long, before they are added up or packed.
        val widths = LongArray(glyphs.size) { rects[it * 4 + 2].toLong() - rects[it * 4] + 2 * TILE_MARGIN }
        val heights = LongArray(glyphs.size) { rects[it * 4 + 3].toLong() - rects[it * 4 + 1] + 2 * TILE_MARGIN }
        tilesFit = glyphs.isEmpty() || (
            widths.all { it in 1..MAX_ATLAS_SIZE } && heights.all { it in 1..MAX_ATLAS_SIZE } &&
                widths.indices.sumOf { widths[it] * heights[it] } <= MAX_ATLAS_SIZE.toLong() * MAX_ATLAS_SIZE
            )
        if (tilesFit) {
            widths.forEachIndexed { index, width -> tileWidths[index] = width.toInt() }
            heights.forEachIndexed { index, height -> tileHeights[index] = height.toInt() }
            val packing = ShelfPacking.pack(tileWidths, tileHeights)
            tileXs = packing.xs
            tileYs = packing.ys
            atlasWidth = packing.width
            atlasHeight = packing.height
        } else {
            // No atlas to make: say how large one would need to be at least, for the error that names it.
            tileXs = IntArray(glyphs.size)
            tileYs = IntArray(glyphs.size)
            val widest = widths.maxOrNull() ?: 0L
            atlasWidth = widest.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val area = widths.indices.sumOf { widths[it].coerceAtLeast(0L) * heights[it].coerceAtLeast(0L) }
            atlasHeight = (area / widest.coerceAtLeast(1L)).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
        glyphs.indices.forEach(::place)
    }

    /**
     * Whether the atlas fits the smallest texture size every backend supports. The tiles are checked on
     * their own first, as the packing is not made for a set that cannot fit.
     */
    fun fits(): Boolean = tilesFit && atlasWidth in 1..MAX_ATLAS_SIZE && atlasHeight in 1..MAX_ATLAS_SIZE

    /** Whether this finer-than-generator bake fits, and stays within the generator's memory and time budgets. */
    fun affordable(glyphs: List<FontGlyph>): Boolean =
        fits() && atlasWidth.toLong() * atlasHeight <= MAX_FINE_ATLAS_TEXELS && regionsAndWork(glyphs).second <= MAX_BAKE_WORK

    /** Each glyph's baked region, and the texels baked times each glyph's segments: what the bake costs. */
    private fun regionsAndWork(glyphs: List<FontGlyph>): Pair<List<IntArray?>, Long> {
        val scale = texelsPerEm / unitsPerEm
        val regions = glyphs.mapIndexed { index, glyph -> glyph.bounds?.let { region(index, it, scale) } }
        val work = glyphs.indices.sumOf { index ->
            regions[index]?.let { max(0, it[2] - it[0]).toLong() * max(0, it[3] - it[1]) * glyphs[index].outline.size } ?: 0L
        }
        return regions to work
    }

    /** The generator's `sampleRect`: the ink box plus the bleed, snapped out to whole texels, from the pen and line top. */
    private fun sampleRect(index: Int) {
        val at = index * 4
        if (inkMetricsEm[at + 2] <= 0f || inkMetricsEm[at + 3] <= 0f) {
            val placeholder = max(1, texelsPerEm / BLANK_GLYPH_DIVISOR)
            intArrayOf(0, 0, placeholder, placeholder).copyInto(rects, at)
            return
        }
        val rawLeft = inkMetricsEm[at] * texelsPerEm
        val rawTop = inkMetricsEm[at + 1] * texelsPerEm
        intArrayOf(
            floor(rawLeft - cropBleed).toInt(),
            floor(rawTop - cropBleed).toInt(),
            ceil(rawLeft + inkMetricsEm[at + 2] * texelsPerEm + cropBleed).toInt(),
            ceil(rawTop + inkMetricsEm[at + 3] * texelsPerEm + cropBleed).toInt(),
        ).copyInto(rects, at)
    }

    /** The generator's UV rect in the atlas, and the quad derived from the sample rect. */
    private fun place(index: Int) {
        val at = index * 4
        val left = rects[at]
        val top = rects[at + 1]
        val right = rects[at + 2]
        val bottom = rects[at + 3]
        val x = tileXs[index] + TILE_MARGIN
        val y = tileYs[index] + TILE_MARGIN
        intArrayOf(x, y, x + (right - left), y + (bottom - top)).copyInto(uvBoundsPx, at)
        if (inkMetricsEm[at + 2] <= 0f || inkMetricsEm[at + 3] <= 0f) return
        val scale = texelsPerEm.toFloat()
        floatArrayOf(left / scale, top / scale, (right - left) / scale, (bottom - top) / scale).copyInto(quadMetricsEm, at)
    }

    /**
     * Every glyph's field in its tile, RGBA with the same distance in each channel; texels far outside stay 0.
     *
     * A texel's cost grows with its glyph's segment count, so a file whose glyphs are huge or absurdly
     * detailed is refused up front rather than left to run for minutes.
     */
    fun bakeAtlas(glyphs: List<FontGlyph>, name: String): ByteArray {
        val scale = texelsPerEm / unitsPerEm
        val (regions, work) = regionsAndWork(glyphs)
        require(work <= MAX_BAKE_WORK) { "$name: its glyphs are too large or too detailed to bake ($work texel-segments)" }
        val atlas = ByteArray(atlasWidth * atlasHeight * RGBA)
        glyphs.forEachIndexed { index, glyph ->
            val region = regions[index] ?: return@forEachIndexed
            val field = GlyphField(glyph.outline, penX(index), baseline(index), scale, bake.fieldReach)
            for (y in region[1] until region[3]) {
                for (x in region[0] until region[2]) {
                    val value = ceil(BYTE_MAX * field.value(x + 0.5, y + 0.5) - 0.5).toInt().coerceIn(0, BYTE_MAX.toInt()).toByte()
                    atlas.fill(value, (y * atlasWidth + x) * RGBA, (y * atlasWidth + x + 1) * RGBA)
                }
            }
        }
        return atlas
    }

    private fun penX(index: Int): Double = (tileXs[index] + TILE_MARGIN - rects[index * 4]).toDouble()

    private fun lineTop(index: Int): Int = tileYs[index] + TILE_MARGIN - rects[index * 4 + 1]

    private fun baseline(index: Int): Double = lineTop(index) + texelsPerEm * (font.ascender / unitsPerEm)

    /** The texels whose value can differ from plain outside: the ink box and the field's reach around it, within the tile. */
    private fun region(index: Int, bounds: DoubleArray, scale: Double): IntArray {
        val reach = bake.fieldReach + 1
        return intArrayOf(
            max(tileXs[index], floor(penX(index) + bounds[0] * scale - reach).toInt()),
            max(tileYs[index], floor(baseline(index) - bounds[3] * scale - reach).toInt()),
            min(tileXs[index] + tileWidths[index], ceil(penX(index) + bounds[2] * scale + reach).toInt()),
            min(tileYs[index] + tileHeights[index], ceil(baseline(index) - bounds[1] * scale + reach).toInt()),
        )
    }
}
