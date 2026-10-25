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
 */
// UiFont's Char and code point lookups are each one short override here.
@Suppress("TooManyFunctions")
class WeightedUiFont(
    faceMap: Map<FontWeight, UiFont>,
) : UiFont {
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

    override val samplingMode: UiFontSamplingMode = baseFace.font.samplingMode
    override val cellSize: Int = baseFace.font.cellSize
    override val textScaleStep: Float = baseFace.font.textScaleStep
    override val atlasWidth: Int = faces.maxOf { it.font.atlasWidth }
    override val atlasHeight: Int = faces.sumOf { it.font.atlasHeight }
    override val atlasPixelsRgba: ByteArray by lazy(::combineAtlases)
    override val lineHeightEm: Float = baseFace.font.lineHeightEm
    override val distanceFieldRangePx: Float = baseFace.font.distanceFieldRangePx
    override val visibleTopEm: Float = baseFace.font.visibleTopEm
    override val visibleBottomEm: Float = baseFace.font.visibleBottomEm
    override val ascentEm: Float = baseFace.font.ascentEm
    override val descentEm: Float = baseFace.font.descentEm
    override val capHeightEm: Float = baseFace.font.capHeightEm

    /** Each weight's glyphs from its own face's outlines; null unless every face has outlines. */
    override val pixelGlyphs: PixelGlyphs? by lazy {
        if (faces.all { it.font.pixelGlyphs != null }) {
            PixelGlyphs { codePoint, weight, pixelSize ->
                val face = resolve(weight)
                face.font.pixelGlyphs?.glyph(codePoint, face.weight, pixelSize)
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
        return face.slice(face.font.glyphFor(char, face.weight) ?: return null)
    }

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        val face = resolve(weight)
        return face.slice(face.font.glyphFor(codePoint, face.weight) ?: return null)
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

    private data class Face(val weight: FontWeight, val font: UiFont)

    private companion object {
        const val RGBA_CHANNELS = 4
    }
}
