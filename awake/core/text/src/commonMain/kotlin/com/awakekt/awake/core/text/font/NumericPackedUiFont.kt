/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * Builds a numeric-figure variant of [base] for [numerals], or returns [base] unchanged when the face has
 * no outlines or its numeric glyphs are already the ones [base] draws and advances by.
 *
 * The variant draws every glyph it shares with [base] from [base]'s own atlas, the same texture, and only
 * the substituted figures from a small atlas page of their own, so a variant copies and uploads no more than
 * those figures.
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

/**
 * [base] with its numeric figures swapped for a `GSUB` feature's.
 *
 * Its atlas is [base]'s own: the same pixels, never copied, and [fontFor] answers [base] for every glyph
 * the two share, so a renderer samples the texture it already uploaded for [base]. The substituted figures
 * sit on [atlasPages]' second page, a strip baked from the outlines, and [fontFor] answers that page for them.
 */
@Suppress("TooManyFunctions", "LongParameterList")
private class NumericPackedUiFont(
    private val base: PackedUiFont,
    name: String,
    private val font: OpenTypeFont,
    private val codes: IntArray,
    private val baseGlyphs: IntArray,
    private val substituted: IntArray,
    extraGlyphs: List<FontGlyph>,
    extraSlotByGlyph: Map<Int, Int>,
    stripLayout: GlyphLayout?,
    private val fallbackCode: Int,
) : UiFont {
    override val samplingMode: UiFontSamplingMode = base.samplingMode
    override val cellSize: Int = base.cellSize
    override val textScaleStep: Float = base.textScaleStep
    override val atlasWidth: Int = base.atlasWidth
    override val atlasHeight: Int = base.atlasHeight
    override val atlasPixelsRgba: ByteArray get() = base.atlasPixelsRgba
    override val distanceFieldRangePx: Float = base.distanceFieldRangePx
    override val effectFieldRangePx: Float = base.effectFieldRangePx
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

    /** Each code point drawn from the strip, by its glyph's index there; two code points can share one glyph. */
    private val stripIndexByCode: Map<Int, Int> = buildMap {
        for (slot in codes.indices) {
            if (substituted[slot] != baseGlyphs[slot]) extraSlotByGlyph[substituted[slot]]?.let { put(codes[slot], it) }
        }
    }

    private val strip: NumericStripPage? = stripLayout?.let { NumericStripPage(base, font, it, extraGlyphs, stripIndexByCode, name) }

    /** [base], whose atlas this is, then the strip of substituted figures when there is one. */
    override val atlasPages: List<UiFont> = listOfNotNull(base, strip)

    private val glyphsByCode: Map<Int, GlyphRect> by lazy(::buildGlyphRects)
    private val numericAdvancesEm: Map<Int, Float> by lazy(::buildNumericAdvances)
    private val kerningPairs: KerningPairs by lazy { KerningPairs.read(font, codes, substituted) }

    override fun uvFor(char: Char): GlyphRect? = glyphFor(char.code, FontWeight.Normal)

    override fun hasGlyph(char: Char): Boolean = base.hasGlyph(char)

    override fun hasGlyph(codePoint: Int): Boolean = base.hasGlyph(codePoint)

    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = glyphFor(char.code, weight)

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? =
        glyphsByCode[codePoint] ?: glyphsByCode[fallbackCode]

    /** [base]'s tile, or the strip's for a substituted figure, from the page [fontFor] answers. */
    override fun effectGlyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        val page = fontFor(codePoint, weight)
        return if (page === base) base.effectGlyphFor(codePoint, weight) else page.effectGlyphFor(codePoint, weight)
    }

    /** The strip for a substituted figure, and [base], whose texture this font's atlas is, for every other glyph. */
    override fun fontFor(codePoint: Int, weight: FontWeight): UiFont {
        val page = strip ?: return base
        val code = if (codePoint in glyphsByCode) codePoint else fallbackCode
        return if (code in stripIndexByCode) page else base
    }

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
        for (code in codes) {
            result[code] = strip?.glyphFor(code, FontWeight.Normal) ?: base.glyphFor(code, FontWeight.Normal) ?: continue
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
}

/**
 * The second atlas page of a [NumericPackedUiFont]: the substituted figures only, baked from the outlines at
 * [base]'s resolution, the first time a renderer asks for its pixels. It draws as [base] draws.
 */
private class NumericStripPage(
    private val base: PackedUiFont,
    font: OpenTypeFont,
    private val layout: GlyphLayout,
    private val glyphs: List<FontGlyph>,
    indexByCode: Map<Int, Int>,
    private val name: String,
) : UiFont by base {
    /** The strip's own effect field, under its glyphs, when [base] has one too, so substituted figures outline alike. */
    private val effect: EffectFieldLayout? = if (base.effectFieldRangePx == 0f) {
        null
    } else {
        EffectFieldLayout.of(font, glyphs, layout.atlasWidth, layout.atlasHeight, MAX_TEXTURE_SIZE)
    }
    override val atlasWidth: Int get() = maxOf(layout.atlasWidth, effect?.width ?: 0)
    override val atlasHeight: Int get() = layout.atlasHeight + (effect?.height ?: 0)
    override val atlasPixelsRgba: ByteArray by lazy {
        val atlas = layout.bakeAtlas(glyphs, name)
        val field = effect ?: return@lazy atlas
        EffectFields.stack(atlas, layout.atlasWidth, layout.atlasHeight, field.bake(), field.width, field.height)
    }
    override val effectFieldRangePx: Float get() = if (effect != null) EffectFieldLayout.RANGE_TEXELS else 0f
    override val pixelGlyphs: PixelGlyphs? get() = null
    override val atlasPages: List<UiFont> = listOf(this)

    private val glyphsByCode: Map<Int, GlyphRect> = indexByCode.mapValues { glyphAt(it.value) }
    private val effectsByCode: Map<Int, GlyphRect> = effect?.let { field ->
        val tiles = EffectFields.tiles(
            IntArray(glyphs.size) { it },
            field.uvBoundsPx,
            field.quadMetricsEm,
            layout.atlasHeight,
            atlasWidth,
            atlasHeight,
        )
        indexByCode.mapNotNull { (code, index) -> tiles[index]?.let { code to it.copy(page = 1) } }.toMap()
    } ?: emptyMap()

    /** The rectangle of the strip's [index]th glyph, on page 1 of the font it belongs to. */
    private fun glyphAt(index: Int): GlyphRect {
        val at = index * QUAD_STRIDE
        return GlyphRect(
            u0 = layout.uvBoundsPx[at].toFloat() / atlasWidth,
            v0 = layout.uvBoundsPx[at + 1].toFloat() / atlasHeight,
            u1 = layout.uvBoundsPx[at + 2].toFloat() / atlasWidth,
            v1 = layout.uvBoundsPx[at + 3].toFloat() / atlasHeight,
            offsetXEm = layout.quadMetricsEm[at],
            offsetYEm = layout.quadMetricsEm[at + 1],
            widthEm = layout.quadMetricsEm[at + 2],
            heightEm = layout.quadMetricsEm[at + 3],
            page = 1,
        )
    }

    override fun effectGlyphFor(codePoint: Int, weight: FontWeight): GlyphRect? = effectsByCode[codePoint]

    override fun uvFor(char: Char): GlyphRect? = glyphsByCode[char.code]

    override fun hasGlyph(char: Char): Boolean = char.code in glyphsByCode

    override fun hasGlyph(codePoint: Int): Boolean = codePoint in glyphsByCode

    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = glyphsByCode[char.code]

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? = glyphsByCode[codePoint]

    override fun fontFor(codePoint: Int, weight: FontWeight): UiFont = this

    override fun pageFont(pageIndex: Int): UiFont = this

    override fun withNumerals(numerals: FontNumerals?): UiFont = this

    private companion object {
        const val QUAD_STRIDE = 4

        /** WebGPU's default texture size limit, as `OpenTypeUiFontData` keeps to. */
        const val MAX_TEXTURE_SIZE = 8192
    }
}
