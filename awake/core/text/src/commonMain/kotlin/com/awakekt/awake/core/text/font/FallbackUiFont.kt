/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

/**
 * A chain of fonts that draws each code point from the first face that has it, made by [UiFonts.fallback].
 *
 * Faces are asked by code point, not `Char`, so a face that draws emoji or CJK Extension B
 * ideographs is found for them too.
 *
 * Faces that share the primary face's sampling mode are stacked into one atlas texture so a line mixing
 * scripts still draws from one atlas in a single batch. Faces with disparate sampling modes (e.g.
 * full-colour emoji beside distance-field text) remain distinct and are resolved via [fontFor]
 * so each draws with its appropriate shader and texture atlas.
 */
@Suppress("TooManyFunctions")
internal class FallbackUiFont(private val faces: List<UiFont>) : UiFont {
    private val primary: UiFont = faces.first()
    private val stackedFaces: List<UiFont> = faces.filter { it.samplingMode == primary.samplingMode }
    private val stackedOffsets: Map<UiFont, Int> = buildMap {
        var y = 0
        stackedFaces.forEach { face ->
            put(face, y)
            y += face.atlasHeight
        }
    }

    override val samplingMode: UiFontSamplingMode = primary.samplingMode
    override val cellSize: Int = primary.cellSize
    override val textScaleStep: Float = primary.textScaleStep
    override val atlasWidth: Int = stackedFaces.maxOf { it.atlasWidth }
    override val atlasHeight: Int = stackedFaces.sumOf { it.atlasHeight }
    override val atlasPixelsRgba: ByteArray by lazy(::combineAtlases)
    override val lineHeightEm: Float = primary.lineHeightEm
    override val distanceFieldRangePx: Float = primary.distanceFieldRangePx
    override val visibleTopEm: Float = primary.visibleTopEm
    override val visibleBottomEm: Float = primary.visibleBottomEm
    override val ascentEm: Float = primary.ascentEm
    override val descentEm: Float = primary.descentEm
    override val capHeightEm: Float = primary.capHeightEm

    override fun uvFor(char: Char): GlyphRect? = glyphFor(char.code, FontWeight.Normal)

    override fun hasGlyph(char: Char): Boolean = hasGlyph(char.code)

    override fun hasGlyph(codePoint: Int): Boolean = faces.any { it.hasGlyph(codePoint) }

    override fun glyphFor(char: Char, weight: FontWeight): GlyphRect? = glyphFor(char.code, weight)

    override fun glyphFor(codePoint: Int, weight: FontWeight): GlyphRect? {
        val index = faceIndex(codePoint)
        val face = faces[index]
        val glyph = face.glyphFor(codePoint, weight)
        return when {
            glyph == null || face.samplingMode != primary.samplingMode -> glyph
            else -> {
                val yOffset = stackedOffsets[face] ?: 0
                glyph.copy(
                    u0 = glyph.u0 * face.atlasWidth / atlasWidth,
                    u1 = glyph.u1 * face.atlasWidth / atlasWidth,
                    v0 = (glyph.v0 * face.atlasHeight + yOffset) / atlasHeight,
                    v1 = (glyph.v1 * face.atlasHeight + yOffset) / atlasHeight,
                )
            }
        }
    }

    override fun fontFor(codePoint: Int, weight: FontWeight): UiFont {
        val face = faces[faceIndex(codePoint)]
        return if (face.samplingMode == primary.samplingMode) this else face.fontFor(codePoint, weight)
    }

    override fun advanceFor(char: Char, glyphPx: Float): Float = faces[faceIndex(char.code)].advanceFor(char, glyphPx)

    override fun advanceFor(char: Char, glyphPx: Float, weight: FontWeight): Float = advanceFor(char.code, glyphPx, weight)

    override fun advanceFor(codePoint: Int, glyphPx: Float, weight: FontWeight): Float =
        faces[faceIndex(codePoint)].advanceFor(codePoint, glyphPx, weight)

    /** The first face that has [codePoint], or the primary face, which draws its own stand-in. */
    private fun faceIndex(codePoint: Int): Int {
        for (index in faces.indices) {
            if (faces[index].hasGlyph(codePoint)) return index
        }
        return 0
    }

    private fun combineAtlases(): ByteArray {
        val result = ByteArray(atlasWidth * atlasHeight * RGBA_CHANNELS)
        stackedFaces.forEach { face ->
            val yOffset = stackedOffsets[face] ?: 0
            val source = face.atlasPixelsRgba
            val rowBytes = face.atlasWidth * RGBA_CHANNELS
            repeat(face.atlasHeight) { row ->
                source.copyInto(
                    destination = result,
                    destinationOffset = ((yOffset + row) * atlasWidth) * RGBA_CHANNELS,
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
