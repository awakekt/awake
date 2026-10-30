/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * A font family backed by several packed faces and one combined atlas.
 *
 * Compose resolves a requested [FontWeight] to the closest face in a [FontFamily]. Awake keeps
 * that same contract with one atlas for the whole family: the face atlases are stacked
 * vertically once, and each weighted glyph receives UVs in its own slice.
 *
 * A face's own atlas is its first atlas page. A glyph a face draws from another page of its own, such as a
 * numeric variant's substituted figures ([withNumerals]), draws from that page, and the faces' extra pages
 * are stacked into one more page of the family. A numeric variant of the family whose faces keep their
 * atlases draws from this family's combined atlas, so it copies and uploads none of it again.
 */
// UiFont's Char and code point lookups are each one short override here.
@Suppress("TooManyFunctions")
class WeightedUiFont internal constructor(
    faceMap: Map<FontWeight, UiFont>,
    private val origin: WeightedUiFont?,
) : UiFont {
    /**
     * Builds a weighted font family from [faceMap].
     */
    constructor(faceMap: Map<FontWeight, UiFont>) : this(faceMap, null)
    private val faces = faceMap.entries
        .sortedBy { it.key.value }
        .map { Face(it.key, it.value) }
        .also { require(it.isNotEmpty()) { "A weighted font needs at least one face" } }

    private val offsetsByFace: Map<Face, Int> = buildMap {
        var y = 0
        faces.forEach { face ->
            put(face, y)
            y += face.font.atlasHeight
        }
    }

    private val baseFace: Face = faces.firstOrNull { it.weight == FontWeight.Normal } ?: faces.first()

    /**
     * The font whose texture holds this family's combined atlas: [origin], when this numeric variant's faces
     * have the same atlases as its faces, and otherwise this family.
     */
    private val atlasOwner: UiFont = origin?.takeIf { source ->
        source.faces.size == faces.size && faces.indices.all { faces[it].ownPage === source.faces[it].ownPage }
    } ?: this

    private val extraPages = ExtraAtlasPages(faces.flatMap { it.extraPages }, baseFace.font, pageIndex = 1)

    override val samplingMode: UiFontSamplingMode = baseFace.font.samplingMode
    override val cellSize: Int = baseFace.font.cellSize
    override val textScaleStep: Float = baseFace.font.textScaleStep
    override val atlasWidth: Int = faces.maxOf { it.font.atlasWidth }
    override val atlasHeight: Int = faces.sumOf { it.font.atlasHeight }
    override val atlasPixelsRgba: ByteArray by lazy { if (atlasOwner === this) combineAtlases() else atlasOwner.atlasPixelsRgba }
    override val lineHeightEm: Float = baseFace.font.lineHeightEm
    override val distanceFieldRangePx: Float = baseFace.font.distanceFieldRangePx

    /** The faces' shared effect-field spread; 0, and decorated text stamped, unless every face has the same one. */
    override val effectFieldRangePx: Float = baseFace.font.effectFieldRangePx
        .takeIf { range -> faces.all { it.font.effectFieldRangePx == range } } ?: 0f
    override val visibleTopEm: Float = baseFace.font.visibleTopEm
    override val visibleBottomEm: Float = baseFace.font.visibleBottomEm
    override val ascentEm: Float = baseFace.font.ascentEm
    override val descentEm: Float = baseFace.font.descentEm
    override val capHeightEm: Float = baseFace.font.capHeightEm
    override val lineAscentEm: Float = baseFace.font.lineAscentEm
    override val lineDescentEm: Float = baseFace.font.lineDescentEm
    override val lineGapEm: Float = baseFace.font.lineGapEm

    /** Each weight's glyphs from its own face's outlines; null unless every face has outlines. */
    override val pixelGlyphs: PixelGlyphs? by lazy {
        if (faces.all { it.font.pixelGlyphs != null }) {
            object : PixelGlyphs {
                override fun glyph(codePoint: Int, weight: FontWeight, pixelSize: Int): PixelGlyph? = glyph(codePoint, weight, pixelSize, 0)

                override fun glyph(codePoint: Int, weight: FontWeight, pixelSize: Int, subpixel: Int): PixelGlyph? {
                    val face = resolve(weight)
                    return face.font.pixelGlyphs?.glyph(codePoint, face.weight, pixelSize, subpixel)
                }
            }
        } else {
            null
        }
    }

    override fun uvFor(char: Char): GlyphRect? = glyphFor(char, baseFace.weight)

    override fun hasGlyph(char: Char): Boolean = baseFace.font.hasGlyph(char)

    override fun hasGlyph(codePoint: Int): Boolean = baseFace.font.hasGlyph(codePoint)

    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? {
        val face = resolve(weight)
        return face.place(char.code, face.font.glyphFor(char, face.weight) ?: return null)
    }

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        val face = resolve(weight)
        return face.place(codePoint, face.font.glyphFor(codePoint, face.weight) ?: return null)
    }

    /** [atlasOwner] for a glyph in its face's own atlas, and the extra page that holds any other. */

    /** The resolved face's tile, moved into its slice; none for a glyph drawn from one of a face's extra pages. */
    override fun effectGlyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        val face = resolve(weight)
        val onOwnPage = face.extraPages.isEmpty() || face.font.fontFor(codePoint, face.weight) === face.ownPage
        val tile = if (effectFieldRangePx > 0f && onOwnPage) face.font.effectGlyphFor(codePoint, face.weight) else null
        return tile?.let { face.slice(it) }
    }

    override fun fontFor(codePoint: Int, weight: FontWeight): UiFont {
        val face = resolve(weight)
        if (face.extraPages.isEmpty()) return atlasOwner
        val owner = face.font.fontFor(codePoint, face.weight)
        return if (owner === face.ownPage) atlasOwner else extraPages.fontFor(owner)
    }

    /** The family's combined atlas ([atlasOwner]), then its faces' extra pages: every font [fontFor] can answer. */
    override val atlasPages: List<UiFont> = listOf(atlasOwner) + extraPages.pages

    /** [glyph], this face's glyph for [codePoint], placed in the texture [fontFor] answers for it. */
    private fun Face.place(codePoint: Int, glyph: GlyphRect): GlyphRect {
        if (extraPages.isEmpty()) return slice(glyph)
        val owner = font.fontFor(codePoint, weight)
        return if (owner === ownPage) slice(glyph) else this@WeightedUiFont.extraPages.glyphFor(owner, glyph)
    }

    /** [glyph], a glyph of this face, with its UVs moved into the face's slice of the combined atlas. */
    private fun Face.slice(glyph: GlyphRect): GlyphRect {
        val face = this
        val yOffset = offsetsByFace.getValue(face)
        return glyph.copy(
            u0 = glyph.u0 * face.font.atlasWidth / atlasWidth,
            u1 = glyph.u1 * face.font.atlasWidth / atlasWidth,
            v0 = (glyph.v0 * face.font.atlasHeight + yOffset) / atlasHeight,
            v1 = (glyph.v1 * face.font.atlasHeight + yOffset) / atlasHeight,
        )
    }

    override fun advanceFor(char: Char, glyphPx: Float): Float =
        resolve(FontWeight.Normal).font.advanceFor(char, glyphPx)

    override fun advanceFor(char: Char, glyphPx: Float, weight: FontWeight): Float =
        resolve(weight).font.advanceFor(char, glyphPx)

    override fun advanceFor(codePoint: Int, glyphPx: Float, weight: FontWeight): Float {
        val face = resolve(weight)
        return face.font.advanceFor(codePoint, glyphPx, face.weight)
    }

    override fun kerningFor(left: Int, right: Int, glyphPx: Float, weight: FontWeight): Float {
        val face = resolve(weight)
        return face.font.kerningFor(left, right, glyphPx, face.weight)
    }

    private val proportionalFont: UiFont by lazy { buildNumericVariant(FontNumerals.Proportional) }
    private val tabularFont: UiFont by lazy { buildNumericVariant(FontNumerals.Tabular) }

    override fun withNumerals(numerals: FontNumerals?): UiFont = when {
        numerals == null -> this
        origin != null -> origin.withNumerals(numerals)
        numerals == FontNumerals.Proportional -> proportionalFont
        else -> tabularFont
    }

    private fun buildNumericVariant(numerals: FontNumerals): UiFont {
        var changed = false
        val mapped = buildMap(faces.size) {
            for (face in faces) {
                val variant = face.font.withNumerals(numerals)
                if (variant !== face.font) changed = true
                put(face.weight, variant)
            }
        }
        return if (changed) WeightedUiFont(mapped, this) else this
    }

    /**
     * The face closest to [weight], the heavier of two equally close: Compose's resolution. A plain loop,
     * not a comparator, because every glyph of every frame comes through here.
     */
    private fun resolve(weight: FontWeight): Face {
        var best = baseFace
        var bestRank = Int.MAX_VALUE
        for (index in faces.indices) {
            val face = faces[index]
            // Twice the distance, plus one for a lighter face: equally close, the heavier ranks first.
            val rank = 2 * kotlin.math.abs(face.weight.value - weight.value) + if (face.weight.value < weight.value) 1 else 0
            if (rank < bestRank) {
                best = face
                bestRank = rank
            }
        }
        return best
    }

    private fun combineAtlases(): ByteArray {
        val result = ByteArray(atlasWidth * atlasHeight * RGBA_CHANNELS)
        var destinationY = 0
        faces.forEach { face ->
            val source = face.font.atlasPixelsRgba
            val rowBytes = face.font.atlasWidth * RGBA_CHANNELS
            repeat(face.font.atlasHeight) { row ->
                source.copyInto(
                    destination = result,
                    destinationOffset = ((destinationY + row) * atlasWidth) * RGBA_CHANNELS,
                    startIndex = row * rowBytes,
                    endIndex = (row + 1) * rowBytes,
                )
            }
            destinationY += face.font.atlasHeight
        }
        return result
    }

    private data class Face(val weight: FontWeight, val font: UiFont) {
        private val pages: List<UiFont> = font.atlasPages

        /** The page that holds this face's own atlas, the one stacked into the family's. */
        val ownPage: UiFont = pages.first()

        /** Pages this face draws some glyphs from beyond its own atlas. */
        val extraPages: List<UiFont> = pages.drop(1)
    }

    private companion object {
        const val RGBA_CHANNELS = 4
    }
}
