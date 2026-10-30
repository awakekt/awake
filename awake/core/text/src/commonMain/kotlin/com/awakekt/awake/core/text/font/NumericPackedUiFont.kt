/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * Builds a numeric-figure variant of [base] for [numerals], or returns [base] unchanged when the face has
 * no outlines or its numeric glyphs are already the ones [base] draws and advances by.
 */
internal fun buildNumericPackedUiFont(
    base: PackedUiFont,
    bake: DistanceFieldBake,
    codes: IntArray,
    outlines: () -> OpenTypeFont?,
    numerals: FontNumerals,
): UiFont {
    val font = if (base.samplingMode == UiFontSamplingMode.DistanceField) outlines() else null
    if (font == null) return base
    val baseGlyphs = IntArray(codes.size) { font.glyphFor(codes[it]) }
    val substituted = NumericGlyphs.read(font, codes, numerals)
    val needsVariant = substituted != null || (numerals == FontNumerals.Tabular && tabularDigitsDriftIn(base, font))
    return if (!needsVariant) {
        base
    } else {
        val effectiveGlyphs = substituted ?: baseGlyphs
        val extraSlotByGlyph = LinkedHashMap<Int, Int>()
        val extraGlyphs = ArrayList<FontGlyph>()
        for (slot in codes.indices) {
            val glyph = effectiveGlyphs[slot]
            if (glyph != baseGlyphs[slot] && glyph != 0 && glyph !in extraSlotByGlyph) {
                extraSlotByGlyph[glyph] = extraGlyphs.size
                extraGlyphs += FontGlyph(codes[slot], font, glyph)
            }
        }
        val stripLayout = if (extraGlyphs.isEmpty()) null else GlyphLayout(font, extraGlyphs, bake)
        NumericPackedUiFont(
            base = base,
            name = base.fontName,
            font = font,
            codes = codes,
            baseGlyphs = baseGlyphs,
            substituted = effectiveGlyphs,
            extraGlyphs = extraGlyphs,
            extraSlotByGlyph = extraSlotByGlyph,
            stripLayout = stripLayout,
            fallbackCode = base.fallbackCode,
        )
    }
}

/**
 * True when [font]'s `cmap` digits `0`..`9` already share one `hmtx` advance, yet [base] advances them at
 * slightly different widths (for example from rounded pre-baked floats or an overhanging digit ink box).
 */
private fun tabularDigitsDriftIn(base: PackedUiFont, font: OpenTypeFont): Boolean {
    val zeroGlyph = font.glyphFor('0'.code)
    val zeroAdvance = if (zeroGlyph == 0) 0 else font.advance(zeroGlyph)
    val uniformInFont = zeroAdvance > 0 && ('1'..'9').all { digit ->
        val glyph = font.glyphFor(digit.code)
        glyph != 0 && font.advance(glyph) == zeroAdvance
    }
    return uniformInFont && base.advanceFor('0', PROBE_PX).let { refPx ->
        ('1'..'9').any { base.advanceFor(it, PROBE_PX) != refPx }
    }
}

private const val PROBE_PX = 100f

@Suppress("TooManyFunctions", "LongParameterList")
private class NumericPackedUiFont(
    private val base: PackedUiFont,
    private val name: String,
    private val font: OpenTypeFont,
    private val codes: IntArray,
    private val baseGlyphs: IntArray,
    private val substituted: IntArray,
    private val extraGlyphs: List<FontGlyph>,
    private val extraSlotByGlyph: Map<Int, Int>,
    private val stripLayout: GlyphLayout?,
    private val fallbackCode: Int,
) : UiFont {
    override val samplingMode: UiFontSamplingMode = base.samplingMode
    override val cellSize: Int = base.cellSize
    override val textScaleStep: Float = base.textScaleStep
    override val atlasWidth: Int = maxOf(base.atlasWidth, stripLayout?.atlasWidth ?: 0)
    override val atlasHeight: Int = base.atlasHeight + (stripLayout?.atlasHeight ?: 0)
    override val atlasPixelsRgba: ByteArray by lazy(::combineAtlasPixels)
    override val distanceFieldRangePx: Float = base.distanceFieldRangePx
    override val lineHeightEm: Float = base.lineHeightEm
    override val visibleTopEm: Float = base.visibleTopEm
    override val visibleBottomEm: Float = base.visibleBottomEm
    override val ascentEm: Float = base.ascentEm
    override val descentEm: Float = base.descentEm
    override val capHeightEm: Float = base.capHeightEm
    override val lineAscentEm: Float = base.lineAscentEm
    override val lineDescentEm: Float = base.lineDescentEm
    override val lineGapEm: Float = base.lineGapEm

    override val pixelGlyphs: PixelGlyphs =
        OutlinePixelGlyphs(font, codes, fallbackCode, base.ascentEm, glyphIds = substituted)

    private val glyphsByCode: Map<Int, GlyphRect> by lazy(::buildGlyphRects)
    private val numericAdvancesEm: Map<Int, Float> by lazy(::buildNumericAdvances)
    private val kerningPairs: KerningPairs by lazy { KerningPairs.read(font, codes, substituted) }

    override fun uvFor(char: Char): GlyphRect? = glyphFor(char.code, FontWeight.Normal)

    override fun hasGlyph(char: Char): Boolean = base.hasGlyph(char)

    override fun hasGlyph(codePoint: Int): Boolean = base.hasGlyph(codePoint)

    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = glyphFor(char.code, weight)

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? =
        glyphsByCode[codePoint] ?: glyphsByCode[fallbackCode]

    override fun advanceFor(char: Char, glyphPx: Float): Float = advanceFor(char.code, glyphPx, FontWeight.Normal)

    override fun advanceFor(char: Char, glyphPx: Float, weight: FontWeight): Float = advanceFor(char.code, glyphPx, weight)

    override fun advanceFor(codePoint: Int, glyphPx: Float, weight: FontWeight): Float {
        val advanceEm = numericAdvancesEm[codePoint]
        return if (advanceEm != null) glyphPx * advanceEm else base.advanceFor(codePoint, glyphPx, weight)
    }

    override fun kerningFor(left: Int, right: Int, glyphPx: Float, weight: FontWeight): Float =
        glyphPx * kerningPairs.em(left, right)

    override fun withNumerals(numerals: FontNumerals?): UiFont =
        if (numerals == null) this else base.withNumerals(numerals)

    private fun buildGlyphRects(): Map<Int, GlyphRect> {
        val result = LinkedHashMap<Int, GlyphRect>(codes.size)
        val strip = stripLayout
        for (slot in codes.indices) {
            val code = codes[slot]
            val target = substituted[slot]
            val extra = if (target != baseGlyphs[slot]) extraSlotByGlyph[target] else null
            if (extra != null && strip != null) {
                val uvAt = extra * QUAD_STRIDE
                result[code] = GlyphRect(
                    u0 = strip.uvBoundsPx[uvAt].toFloat() / atlasWidth,
                    v0 = (base.atlasHeight + strip.uvBoundsPx[uvAt + 1]).toFloat() / atlasHeight,
                    u1 = strip.uvBoundsPx[uvAt + 2].toFloat() / atlasWidth,
                    v1 = (base.atlasHeight + strip.uvBoundsPx[uvAt + 3]).toFloat() / atlasHeight,
                    offsetXEm = strip.quadMetricsEm[uvAt],
                    offsetYEm = strip.quadMetricsEm[uvAt + 1],
                    widthEm = strip.quadMetricsEm[uvAt + 2],
                    heightEm = strip.quadMetricsEm[uvAt + 3],
                )
            } else {
                val baseGlyph = base.glyphFor(code, FontWeight.Normal) ?: continue
                result[code] = if (atlasWidth == base.atlasWidth && atlasHeight == base.atlasHeight) {
                    baseGlyph
                } else {
                    baseGlyph.copy(
                        u0 = baseGlyph.u0 * base.atlasWidth / atlasWidth,
                        u1 = baseGlyph.u1 * base.atlasWidth / atlasWidth,
                        v0 = baseGlyph.v0 * base.atlasHeight / atlasHeight,
                        v1 = baseGlyph.v1 * base.atlasHeight / atlasHeight,
                    )
                }
            }
        }
        return result
    }

    private fun buildNumericAdvances(): Map<Int, Float> {
        val unitsPerEm = font.unitsPerEm.toFloat()
        val result = HashMap<Int, Float>()
        for (slot in codes.indices) {
            val code = codes[slot]
            if (code in '0'.code..'9'.code || substituted[slot] != baseGlyphs[slot]) {
                result[code] = font.advance(substituted[slot]) / unitsPerEm
            }
        }
        return result
    }

    private fun combineAtlasPixels(): ByteArray {
        val strip = stripLayout ?: return base.atlasPixelsRgba
        val combined = ByteArray(atlasWidth * atlasHeight * RGBA_CHANNELS)
        copyRows(
            source = base.atlasPixelsRgba,
            sourceWidth = base.atlasWidth,
            sourceHeight = base.atlasHeight,
            destination = combined,
            destinationY = 0,
        )
        copyRows(
            source = strip.bakeAtlas(extraGlyphs, name),
            sourceWidth = strip.atlasWidth,
            sourceHeight = strip.atlasHeight,
            destination = combined,
            destinationY = base.atlasHeight,
        )
        return combined
    }

    private fun copyRows(
        source: ByteArray,
        sourceWidth: Int,
        sourceHeight: Int,
        destination: ByteArray,
        destinationY: Int,
    ) {
        val rowBytes = sourceWidth * RGBA_CHANNELS
        repeat(sourceHeight) { row ->
            source.copyInto(
                destination = destination,
                destinationOffset = (destinationY + row) * atlasWidth * RGBA_CHANNELS,
                startIndex = row * rowBytes,
                endIndex = (row + 1) * rowBytes,
            )
        }
    }

    private companion object {
        const val QUAD_STRIDE = 4
        const val RGBA_CHANNELS = 4
    }
}
