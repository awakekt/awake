/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * A chain of fonts that draws each char from the first face that has it, made by [UiFonts.fallback].
 *
 * Like [WeightedUiFont], the faces' atlases are stacked into one texture once, and each glyph gets
 * UVs in its own face's slice, so a line mixing scripts still draws from one atlas.
 */
internal class FallbackUiFont(private val faces: List<UiFont>) : UiFont {
    private val primary: UiFont = faces.first()

    init {
        faces.forEach {
            require(it.samplingMode == primary.samplingMode) {
                "A fallback font samples as ${it.samplingMode}, but the primary font samples as ${primary.samplingMode}."
            }
        }
    }

    private val offsets: IntArray = IntArray(faces.size).also { out ->
        var y = 0
        faces.forEachIndexed { index, face ->
            out[index] = y
            y += face.atlasHeight
        }
    }

    override val samplingMode: UiFontSamplingMode = primary.samplingMode
    override val cellSize: Int = primary.cellSize
    override val textScaleStep: Float = primary.textScaleStep
    override val atlasWidth: Int = faces.maxOf { it.atlasWidth }
    override val atlasHeight: Int = faces.sumOf { it.atlasHeight }
    override val atlasPixelsRgba: ByteArray by lazy(::combineAtlases)
    override val lineHeightEm: Float = primary.lineHeightEm
    override val distanceFieldRangePx: Float = primary.distanceFieldRangePx
    override val visibleTopEm: Float = primary.visibleTopEm
    override val visibleBottomEm: Float = primary.visibleBottomEm
    override val ascentEm: Float = primary.ascentEm
    override val descentEm: Float = primary.descentEm
    override val capHeightEm: Float = primary.capHeightEm

    override fun uvFor(char: Char): GlyphRect? = glyphFor(char, FontWeight.Normal)

    override fun hasGlyph(char: Char): Boolean = faces.any { it.hasGlyph(char) }

    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? {
        val index = faceIndex(char)
        val face = faces[index]
        val glyph = face.glyphFor(char, weight) ?: return null
        val yOffset = offsets[index]
        return glyph.copy(
            u0 = glyph.u0 * face.atlasWidth / atlasWidth,
            u1 = glyph.u1 * face.atlasWidth / atlasWidth,
            v0 = (glyph.v0 * face.atlasHeight + yOffset) / atlasHeight,
            v1 = (glyph.v1 * face.atlasHeight + yOffset) / atlasHeight,
        )
    }

    override fun advanceFor(char: Char, glyphPx: Float): Float = faces[faceIndex(char)].advanceFor(char, glyphPx)

    override fun advanceFor(char: Char, glyphPx: Float, weight: FontWeight): Float =
        faces[faceIndex(char)].advanceFor(char, glyphPx, weight)

    /** The first face that has [char], or the primary face, which draws its own stand-in. */
    private fun faceIndex(char: Char): Int {
        for (index in faces.indices) {
            if (faces[index].hasGlyph(char)) return index
        }
        return 0
    }

    private fun combineAtlases(): ByteArray {
        val result = ByteArray(atlasWidth * atlasHeight * RGBA_CHANNELS)
        faces.forEachIndexed { index, face ->
            val source = face.atlasPixelsRgba
            val rowBytes = face.atlasWidth * RGBA_CHANNELS
            repeat(face.atlasHeight) { row ->
                source.copyInto(
                    destination = result,
                    destinationOffset = ((offsets[index] + row) * atlasWidth) * RGBA_CHANNELS,
                    startIndex = row * rowBytes,
                    endIndex = (row + 1) * rowBytes,
                )
            }
        }
        return result
    }

    private companion object {
        const val RGBA_CHANNELS = 4
    }
}
