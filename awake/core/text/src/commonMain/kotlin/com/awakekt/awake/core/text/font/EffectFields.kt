/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalEncodingApi::class)

package com.awakekt.awake.core.text.font

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * A font's effect field: one coarse, wide-reaching signed distance per texel, 0.5 on the outline, that an outline
 * and a soft shadow are drawn from (#1061). The atlas's own distance field reaches a texel or two past the edge,
 * far too little for a 4 dp outline at 12 px. Its tiles sit in rows under the atlas's glyphs, in the same texture,
 * so a decorated glyph samples one texture for its fill and its decoration alike.
 */
internal object EffectFields {
    /**
     * The field the generator encoded as [encoded]: base64 of a zlib stream of [width] by [height] bytes, each row
     * stored as differences from the texel to its left (PNG's `Sub` filter).
     */
    fun decode(encoded: String, width: Int, height: Int): ByteArray {
        val filtered = DeflateInflater.inflateZlib(Base64.Default.decode(encoded.filterNot(Char::isWhitespace)))
        require(filtered.size == width * height) { "An effect field of ${filtered.size} bytes is not $width by $height" }
        for (index in filtered.indices) {
            if (index % width != 0) filtered[index] = (filtered[index] + filtered[index - 1]).toByte()
        }
        return filtered
    }

    /**
     * [atlas], [atlasWidth] by [atlasHeight] RGBA texels, with [field], [fieldWidth] by [fieldHeight], in the rows
     * under it, its value in all four channels: the texture a font with an effect field uploads. The atlas's texels
     * keep their columns and rows, so its glyphs sample what they sampled before.
     */
    // An atlas and a field, each as bytes and a size: grouping them would only wrap them for this one call.
    @Suppress("LongParameterList")
    fun stack(atlas: ByteArray, atlasWidth: Int, atlasHeight: Int, field: ByteArray, fieldWidth: Int, fieldHeight: Int): ByteArray {
        val width = maxOf(atlasWidth, fieldWidth)
        val result = ByteArray(width * (atlasHeight + fieldHeight) * RGBA)
        for (row in 0 until atlasHeight) {
            atlas.copyInto(result, row * width * RGBA, row * atlasWidth * RGBA, (row + 1) * atlasWidth * RGBA)
        }
        for (row in 0 until fieldHeight) {
            val target = (atlasHeight + row) * width * RGBA
            for (column in 0 until fieldWidth) {
                val value = field[row * fieldWidth + column]
                result.fill(value, target + column * RGBA, target + (column + 1) * RGBA)
            }
        }
        return result
    }

    /**
     * Each glyph's effect tile from [uvBoundsPx] and [quadMetricsEm] (four values a glyph, in glyph order), placed in
     * a texture [textureWidth] by [textureHeight] whose field starts [fieldTop] rows down, keyed by [codes]. A glyph
     * whose tile covers nothing, a space, has none.
     */
    @Suppress("LongParameterList")
    fun tiles(
        codes: IntArray,
        uvBoundsPx: IntArray,
        quadMetricsEm: FloatArray,
        fieldTop: Int,
        textureWidth: Int,
        textureHeight: Int,
    ): Map<Int, GlyphRect> {
        val result = LinkedHashMap<Int, GlyphRect>(codes.size)
        for (glyph in codes.indices) {
            val at = glyph * 4
            if (at + 3 >= quadMetricsEm.size || quadMetricsEm[at + 2] <= 0f || quadMetricsEm[at + 3] <= 0f) continue
            result[codes[glyph]] = GlyphRect(
                u0 = uvBoundsPx[at].toFloat() / textureWidth,
                v0 = (uvBoundsPx[at + 1] + fieldTop).toFloat() / textureHeight,
                u1 = uvBoundsPx[at + 2].toFloat() / textureWidth,
                v1 = (uvBoundsPx[at + 3] + fieldTop).toFloat() / textureHeight,
                offsetXEm = quadMetricsEm[at],
                offsetYEm = quadMetricsEm[at + 1],
                widthEm = quadMetricsEm[at + 2],
                heightEm = quadMetricsEm[at + 3],
            )
        }
        return result
    }

    private const val RGBA = 4
}
