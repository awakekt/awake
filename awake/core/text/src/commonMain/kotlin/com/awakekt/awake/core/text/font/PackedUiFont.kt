/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalEncodingApi::class)

package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.text.unicode.CodePoints
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal interface PackedUiFontData {
    val name: String
    val baseCellSize: Int

    /** Ascent + descent as a multiple of the em size (Roboto: ~1.19). A font's ink band is
     * taller than its nominal size, so a slot sized at exactly glyphPx cannot contain its own
     * text. Explicit because baseCellSize used to double as the line height and stopped being
     * able to once em values were normalised against the font size. */
    val lineHeightEm: Float
    val textScaleStep: Float
    val atlasWidth: Int
    val atlasHeight: Int
    val samplingMode: UiFontSamplingMode
    val fallbackChar: Char

    /** Distance-field spread, in ATLAS TEXELS -- the `-pxrange` the atlas was generated with.
     * The glyph shader divides this by the atlas size to recover a UV-space range, so it must
     * stay in texels, not screen pixels. Ignored for [UiFontSamplingMode.CoverageAlpha]. */
    val distanceFieldRangePx: Float get() = 0f

    /** Channels per texel in [encodedAtlasBase64]: 1 for a coverage-alpha atlas, 4 for an
     * MTSDF one (RGB carries the multi-channel distance, A a true signed distance). */
    val atlasChannels: Int get() = 1

    /** Raw atlas bytes, [atlasChannels] per texel, row-major from the top-left, as base64. */
    val encodedAtlasBase64: String

    /** The code points the atlas holds, in the order of the per-glyph arrays; a surrogate pair is one. */
    val glyphOrder: String
    val uvBoundsPx: IntArray

    /** Per-glyph RENDER quad (offsetX, offsetY, width, height in em, pen/line-top relative),
     * covering exactly the texels [uvBoundsPx] samples -- outline plus crop bleed plus the
     * texel snap. Sizing the quad to anything smaller than the sampled rect squeezes the atlas
     * region into it and every glyph renders below its own metrics. */
    val quadMetricsEm: FloatArray

    /** Per-glyph INK bounds (same layout), outline-exact with no bleed. This is what metrics
     * (cap height, baseline, visible band, advance clamping) must derive from; deriving them
     * from [quadMetricsEm] bakes the bleed into the type's measurements. Defaults to
     * [quadMetricsEm] for data generated before the split. */
    val inkMetricsEm: FloatArray get() = quadMetricsEm
    val advancesEm: FloatArray

    /** The atlas bytes [encodedAtlasBase64] holds. A font baked at run time hands its bytes over instead. */
    fun atlasBytes(): ByteArray = Base64.Default.decode(encodedAtlasBase64.filterNot(Char::isWhitespace))

    /** The outlines the atlas was drawn from, for [PackedUiFont.pixelGlyphs]; null when they were not kept. */
    fun outlineFont(): OpenTypeFont? = null

    /** The font file's line box, for [UiFont.lineAscentEm] and its siblings; null to derive it from the atlas. */
    fun lineMetrics(): FontLineMetrics? = null

    /**
     * The atlas glyphs' pair kerning, already read; null to read it from [outlineFont] on first use, or to
     * have none when there are no outlines either.
     */
    fun kerning(): KerningPairs? = null

    /** The resolution this atlas's distance field was baked at. */
    fun distanceFieldBake(): DistanceFieldBake = DistanceFieldBake.GENERATOR

    /** The effect field's size in texels ([EffectFields]); 0 by 0 for a font without one. */
    val effectAtlasWidth: Int get() = 0
    val effectAtlasHeight: Int get() = 0

    /** The effect field's spread in its texels, as [distanceFieldRangePx] is the atlas's. */
    val effectFieldRangePx: Float get() = 0f

    /** Each glyph's effect tile (left, top, right, bottom) in effect-field texels, in glyph order. */
    val effectUvBoundsPx: IntArray get() = IntArray(0)

    /** The quad each effect tile covers (offsetX, offsetY, width, height in em, pen/line-top relative), in glyph order. */
    val effectQuadMetricsEm: FloatArray get() = FloatArray(0)

    /** The effect field as [EffectFields.decode] reads it. */
    val encodedEffectFieldBase64: String get() = ""

    /** The effect field's bytes, one a texel; null when there is none. A font baked at run time hands them over. */
    fun effectFieldBytes(): ByteArray? =
        if (effectAtlasHeight > 0) EffectFields.decode(encodedEffectFieldBase64, effectAtlasWidth, effectAtlasHeight) else null
}

/**
 * A UI font implementation backed by pre-baked packed glyph atlas data.
 *
 * @param data The underlying packed font data.
 * @property cellSize The base cell size in pixels for this font instance.
 * @param lineMetrics The font file's line box; null derives it from [ascentEm] and [lineHeightEm].
 * @param outlines The outlines the atlas was drawn from, read on first use for [pixelGlyphs], and for the
 *   pair kerning [kerningFor] reads when [data] does not already carry it.
 */
// UiFont's Char and code point lookups are each one short override here.
@Suppress("TooManyFunctions")
class PackedUiFont internal constructor(
    private val data: PackedUiFontData,
    override val cellSize: Int = data.baseCellSize,
    private val lineMetrics: FontLineMetrics? = data.lineMetrics(),
    private val outlines: () -> OpenTypeFont? = data::outlineFont,
) : UiFont {
    override val samplingMode: UiFontSamplingMode = data.samplingMode
    override val textScaleStep: Float = data.textScaleStep
    override val atlasWidth: Int = maxOf(data.atlasWidth, data.effectAtlasWidth)
    override val atlasHeight: Int = data.atlasHeight + data.effectAtlasHeight
    override val atlasPixelsRgba: ByteArray by lazy(::decodeAtlasPixels)
    override val distanceFieldRangePx: Float = data.distanceFieldRangePx
    override val effectFieldRangePx: Float = if (data.effectAtlasHeight > 0) data.effectFieldRangePx else 0f
    override val visibleTopEm: Float by lazy { computeVisibleBand().first }
    override val visibleBottomEm: Float by lazy { computeVisibleBand().second }

    /** Distance from a line's layout origin down to its baseline, measured off the atlas rather
     * than inherited from [UiFont]'s fabricated 0.8 default. */
    override val ascentEm: Float by lazy { baselineEm }

    /** Ink below the baseline. Uses the atlas's deepest glyph, so it covers every descender the
     * font can actually draw. */
    override val descentEm: Float by lazy { visibleBottomEm - baselineEm }

    override val capHeightEm: Float by lazy { measureCapHeightEm() }

    /**
     * Glyphs rasterized from the outlines this atlas was drawn from, at exact pixel sizes; null for an
     * atlas whose outlines were not kept, such as a colour emoji atlas. The outlines are read on first
     * use, and each size's glyphs are rasterized the first time it is drawn.
     */
    override val pixelGlyphs: PixelGlyphs? by lazy {
        outlines()?.let { font ->
            val codes = ArrayList<Int>(data.advancesEm.size)
            forEachGlyphCode { _, code -> codes += code }
            OutlinePixelGlyphs(font, codes.toIntArray(), data.fallbackChar.code, ascentEm)
        }
    }

    /**
     * Baseline position, read off a flat-bottomed capital rather than assumed.
     *
     * Round glyphs ('O', 'S') overshoot the baseline slightly by design, and every descender
     * sits well below it, so the atlas's own ink extremes cannot locate it -- [visibleBottomEm]
     * is 1.265625 here, set by '@'. [BASELINE_REFERENCE_GLYPHS] are all flat-bottomed with no
     * overshoot, so their ink bottom IS the baseline; they agree exactly in the packed Roboto
     * atlas, and the first one present wins.
     */
    private val baselineEm: Float by lazy {
        BASELINE_REFERENCE_GLYPHS.firstNotNullOfOrNull { char ->
            inkByCode[char.code]?.takeIf { it.heightEm > 0f }?.let { it.offsetYEm + it.heightEm }
        } ?: visibleBottomEm
    }

    // Keyed by code point, so a font loaded with characters past U+FFFF finds them.
    private val glyphsByCode: Map<Int, GlyphRect> by lazy(::decodeGlyphRects)
    private val inkByCode: Map<Int, InkBounds> by lazy(::decodeInkBounds)
    private val advancesByCode: Map<Int, Float> by lazy(::decodeAdvances)
    private val effectsByCode: Map<Int, GlyphRect> by lazy {
        val codes = ArrayList<Int>(data.advancesEm.size)
        forEachGlyphCode { _, code -> codes += code }
        EffectFields.tiles(codes.toIntArray(), data.effectUvBoundsPx, data.effectQuadMetricsEm, data.atlasHeight, atlasWidth, atlasHeight)
    }
    internal val fontName: String get() = data.name
    internal val fallbackCode: Int = data.fallbackChar.code

    /**
     * The pairs [kerningFor] reads: the data's own, or those of the outlines the atlas was drawn from among
     * its code points, read on first use. The built-in Roboto's outlines carry its `GPOS` kerning
     * (`tools/fonts-tooling/subset_roboto_outlines.py`).
     */
    private val kerningPairs: KerningPairs by lazy {
        data.kerning() ?: outlines()?.let { font ->
            val codes = ArrayList<Int>(data.advancesEm.size)
            forEachGlyphCode { _, code -> codes += code }
            KerningPairs.read(font, codes.toIntArray())
        } ?: KerningPairs.EMPTY
    }

    /** How many pairs of this font's code points it kerns. */
    internal val kerningPairCount: Int get() = kerningPairs.size

    override val lineHeightEm: Float get() = data.lineHeightEm
    override val lineAscentEm: Float get() = lineMetrics?.ascentEm ?: ascentEm
    override val lineDescentEm: Float get() = lineMetrics?.descentEm ?: (lineHeightEm - ascentEm)
    override val lineGapEm: Float get() = lineMetrics?.gapEm ?: 0f

    override fun uvFor(char: Char): GlyphRect? = glyphFor(char.code, FontWeight.Normal)

    override fun hasGlyph(char: Char): Boolean = char.code in glyphsByCode

    override fun hasGlyph(codePoint: Int): Boolean = codePoint in glyphsByCode

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? =
        glyphsByCode[codePoint] ?: glyphsByCode[fallbackCode]

    override fun effectGlyphFor(codePoint: Int, weight: FontWeight): GlyphRect? =
        if (codePoint in glyphsByCode) effectsByCode[codePoint] else effectsByCode[fallbackCode]

    /**
     * A glyph's outline-exact ink box, in em, without the crop bleed and texel snap that
     * [uvFor]'s quad carries.
     *
     * [uvFor] deliberately returns the RENDER quad, which covers the same texels the UV rect
     * samples -- that equality is what keeps ink from being squeezed. It is therefore the wrong
     * geometry to derive or verify type metrics against: measuring a baseline or cap height off
     * it reads the padding as if it were ink. `ascentEm`/`descentEm`/`capHeightEm` already come
     * from this ink data; exposing it lets a test check them against the same source rather
     * than against the padded quad.
     */
    fun inkFor(char: Char): GlyphRect? =
        (inkByCode[char.code] ?: inkByCode[fallbackCode])?.let { ink ->
            GlyphRect(
                u0 = 0f,
                v0 = 0f,
                u1 = 0f,
                v1 = 0f,
                offsetXEm = ink.offsetXEm,
                offsetYEm = ink.offsetYEm,
                widthEm = ink.widthEm,
                heightEm = ink.heightEm,
            )
        }

    /**
     * The embedded Roboto data's declared advance is supposed to trail the glyph's own quad
     * right edge (`offsetXEm + widthEm`) by only "a few percent" (see [measureTextWidth]'s
     * doc), just enough that the *next* glyph's quad redraws over the sliver of overhang.
     * In practice several common letters ('t', 'r', 'f', 'k', ...) declare an advance
     * 20-40% narrower than their own ink -- e.g. 'f' overhangs by 42% of its advance width --
     * which at real UI text sizes reads as adjacent glyphs visibly touching/merging (the
     * per-character misalignment this was reported as). Clamping the pen step to never be
     * smaller than the glyph's own right edge keeps every glyph's ink inside its own advance,
     * eliminating the collision while leaving glyphs with correctly-tight metrics untouched.
     *
     * `offsetXEm` is generated pen-relative (measured from the same pen origin as
     * `advanceEm`, see generate_ui_font_atlas.py), so `rightEdgeEm` is directly comparable to
     * `advanceEm` here -- it must NOT carry any atlas-cell-relative padding term, or this
     * `maxOf` degenerates into clamping essentially every glyph by a padding-sized (not
     * overhang-sized) amount, which varies per glyph because ink width varies per glyph. That
     * variable inflation was the actual root cause of the reported uneven letter spacing.
     */
    override fun advanceFor(char: Char, glyphPx: Float): Float = advanceFor(char.code, glyphPx, FontWeight.Normal)

    override fun advanceFor(codePoint: Int, glyphPx: Float, weight: FontWeight): Float {
        val advanceEm = advancesByCode[codePoint] ?: advancesByCode[fallbackCode] ?: 1f
        // Clamp against the INK right edge, not the render quad's: the quad carries the crop
        // bleed, and clamping to it would inflate spacing by a bleed-sized, per-glyph amount --
        // the exact variable-inflation failure described above.
        val ink = inkByCode[codePoint] ?: inkByCode[fallbackCode]
        val rightEdgeEm = if (ink != null) ink.offsetXEm + ink.widthEm else 0f
        return glyphPx * maxOf(advanceEm, rightEdgeEm)
    }

    override fun kerningFor(left: Int, right: Int, glyphPx: Float, weight: FontWeight): Float =
        glyphPx * kerningPairs.em(left, right)

    private val proportionalFont: UiFont by lazy { numericVariant(FontNumerals.Proportional) }
    private val tabularFont: UiFont by lazy { numericVariant(FontNumerals.Tabular) }

    override fun withNumerals(numerals: FontNumerals?): UiFont = when (numerals) {
        null -> this
        FontNumerals.Proportional -> proportionalFont
        FontNumerals.Tabular -> tabularFont
    }

    private fun numericVariant(numerals: FontNumerals): UiFont {
        val codes = ArrayList<Int>(data.advancesEm.size)
        forEachGlyphCode { _, code -> codes += code }
        return buildNumericPackedUiFont(
            base = this,
            bake = data.distanceFieldBake(),
            codes = codes.toIntArray(),
            outlines = outlines,
            numerals = numerals,
        )
    }

    /** The atlas's texels, then the effect field's in the rows under them ([EffectFields.stack]). */
    private fun decodeAtlasPixels(): ByteArray {
        val atlas = decodeGlyphPixels()
        val field = data.effectFieldBytes() ?: return atlas
        return EffectFields.stack(atlas, data.atlasWidth, data.atlasHeight, field, data.effectAtlasWidth, data.effectAtlasHeight)
    }

    private fun decodeGlyphPixels(): ByteArray {
        val decoded = data.atlasBytes()
        // An MTSDF atlas is already RGBA -- RGB carries the multi-channel distance the shader
        // takes a median of, A a true signed distance. Handing it back verbatim is the point;
        // rewriting RGB to white (as the coverage path must) would erase the distance field.
        if (data.atlasChannels == RGBA_CHANNELS) return decoded
        val rgba = ByteArray(decoded.size * RGBA_CHANNELS)
        var sourceIndex = 0
        var targetIndex = 0
        while (sourceIndex < decoded.size) {
            rgba[targetIndex] = -1
            rgba[targetIndex + 1] = -1
            rgba[targetIndex + 2] = -1
            rgba[targetIndex + 3] = decoded[sourceIndex]
            sourceIndex += 1
            targetIndex += RGBA_CHANNELS
        }
        return rgba
    }

    private fun decodeGlyphRects(): Map<Int, GlyphRect> {
        val result = LinkedHashMap<Int, GlyphRect>(data.glyphOrder.length)
        var uvIndex = 0
        var metricIndex = 0
        forEachGlyphCode { _, code ->
            result[code] = GlyphRect(
                u0 = data.uvBoundsPx[uvIndex].toFloat() / atlasWidth,
                v0 = data.uvBoundsPx[uvIndex + 1].toFloat() / atlasHeight,
                u1 = data.uvBoundsPx[uvIndex + 2].toFloat() / atlasWidth,
                v1 = data.uvBoundsPx[uvIndex + 3].toFloat() / atlasHeight,
                offsetXEm = data.quadMetricsEm[metricIndex],
                offsetYEm = data.quadMetricsEm[metricIndex + 1],
                widthEm = data.quadMetricsEm[metricIndex + 2],
                heightEm = data.quadMetricsEm[metricIndex + 3],
            )
            uvIndex += 4
            metricIndex += 4
        }
        return result
    }

    private fun decodeInkBounds(): Map<Int, InkBounds> {
        val result = LinkedHashMap<Int, InkBounds>(data.glyphOrder.length)
        var metricIndex = 0
        forEachGlyphCode { _, code ->
            result[code] = InkBounds(
                offsetXEm = data.inkMetricsEm[metricIndex],
                offsetYEm = data.inkMetricsEm[metricIndex + 1],
                widthEm = data.inkMetricsEm[metricIndex + 2],
                heightEm = data.inkMetricsEm[metricIndex + 3],
            )
            metricIndex += 4
        }
        return result
    }

    private fun decodeAdvances(): Map<Int, Float> {
        val result = LinkedHashMap<Int, Float>(data.glyphOrder.length)
        forEachGlyphCode { glyph, code ->
            result[code] = data.advancesEm[glyph]
        }
        return result
    }

    /** Calls [action] with each glyph's index into the per-glyph arrays and its code point. */
    private inline fun forEachGlyphCode(action: (glyph: Int, code: Int) -> Unit) {
        val order = data.glyphOrder
        var index = 0
        var glyph = 0
        while (index < order.length) {
            val code = CodePoints.at(order, index)
            action(glyph++, code)
            index += CodePoints.charCount(code)
        }
    }

    /** Outline-exact ink rect, pen/line-top relative in em -- the measurement-facing twin of
     * the render-facing [GlyphRect]. */
    private data class InkBounds(
        val offsetXEm: Float,
        val offsetYEm: Float,
        val widthEm: Float,
        val heightEm: Float,
    )

    /** Cap height is the reference capital's own ink height -- flat top, flat bottom, so the
     * ink rect spans exactly baseline-to-cap with no overshoot to subtract. Read from ink, not
     * the render quad: the quad carries crop bleed and would overstate the metric. */
    private fun measureCapHeightEm(): Float =
        BASELINE_REFERENCE_GLYPHS.firstNotNullOfOrNull { char ->
            inkByCode[char.code]?.heightEm?.takeIf { it > 0f }
        } ?: super.capHeightEm

    /**
     * The ink band of the atlas's glyphs, without the Latin-1 Supplement and Latin Extended-A letters and signs.
     * Their accents, cedillas, commas below (Ģ, ļ) and § reach past the line box a font is laid out in, by
     * design, and counting them would grow the deepest descender [descentEm] reports, and with it the line
     * `TextLineBox` keeps the ink inside of, by a pixel at sizes such as 12, 16 and 17 for every line of text
     * a font with the wider set draws. A font with no glyph outside them is measured by all of them.
     */
    private fun computeVisibleBand(): Pair<Float, Float> =
        visibleBand(skipLatinMarks = true) ?: visibleBand(skipLatinMarks = false) ?: (0f to 1f)

    private fun visibleBand(skipLatinMarks: Boolean): Pair<Float, Float>? {
        var top = Float.POSITIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        forEachGlyphCode { glyph, code ->
            val offsetYEm = data.inkMetricsEm[glyph * 4 + 1]
            val heightEm = data.inkMetricsEm[glyph * 4 + 3]
            if (heightEm > 0f && !(skipLatinMarks && code in LATIN_SUPPLEMENT_FIRST..LATIN_EXTENDED_A_LAST)) {
                top = minOf(top, offsetYEm)
                bottom = maxOf(bottom, offsetYEm + heightEm)
            }
        }
        return if (top.isFinite() && bottom.isFinite() && bottom > top) top to bottom else null
    }

    private companion object {
        /** Flat-bottomed capitals, in preference order. Ordered by how reliably a font draws
         * them without overshoot or optical correction. */
        val BASELINE_REFERENCE_GLYPHS = listOf('H', 'I', 'E', 'T', 'X')
        const val RGBA_CHANNELS = 4
    }
}

/** U+00A0 to U+017F: Latin-1 Supplement and Latin Extended-A, which [PackedUiFont]'s ink band leaves out. */
private const val LATIN_SUPPLEMENT_FIRST = 0x00A0
private const val LATIN_EXTENDED_A_LAST = 0x017F
