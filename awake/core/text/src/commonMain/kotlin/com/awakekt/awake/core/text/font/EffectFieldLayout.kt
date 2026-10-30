/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * The effect field of a font baked at run time ([UiFonts.load]), laid out and baked as the generator does it
 * (`EffectField.kt` in `:awake:ui:font-atlas-generator`): [TEXELS_PER_EM] texels per em, reaching [REACH_TEXELS]
 * texels past each glyph's ink, shelves [atlasWidth] wide to sit under its atlas. [GlyphField] bakes it, as it bakes
 * the atlas, so the same file gives the same bytes on every target.
 *
 * @param font The font [glyphs] come from, for its em and ascender.
 * @param glyphs The glyphs, in glyph order.
 * @param rects Each glyph's tile rect from its pen origin and line top, in field texels.
 * @param tileXs Each glyph's tile's left edge in the field, margin included.
 * @param tileYs Each glyph's tile's top edge in the field, margin included.
 * @property width The field's width in texels.
 * @property height Its height in texels.
 */
// The layout's own parts, each read by the bake; a holder for them would be this class again.
@Suppress("LongParameterList")
internal class EffectFieldLayout private constructor(
    private val font: OpenTypeFont,
    private val glyphs: List<FontGlyph>,
    private val rects: IntArray,
    private val tileXs: IntArray,
    private val tileYs: IntArray,
    val width: Int,
    val height: Int,
) {
    private val unitsPerEm = font.unitsPerEm.toDouble()

    /** Each glyph's tile rect (left, top, right, bottom) in field texels, in glyph order. */
    val uvBoundsPx = IntArray(glyphs.size * 4)

    /** The quad each tile covers, in em from the pen origin and line top, in glyph order. */
    val quadMetricsEm = FloatArray(glyphs.size * 4)

    init {
        for (index in glyphs.indices) {
            val at = index * 4
            val x = tileXs[index] + MARGIN
            val y = tileYs[index] + MARGIN
            intArrayOf(x, y, x + rects[at + 2] - rects[at], y + rects[at + 3] - rects[at + 1]).copyInto(uvBoundsPx, at)
            if (glyphs[index].bounds == null) continue
            val scale = TEXELS_PER_EM.toFloat()
            floatArrayOf(
                rects[at] / scale,
                rects[at + 1] / scale,
                (rects[at + 2] - rects[at]) / scale,
                (rects[at + 3] - rects[at + 1]) / scale,
            ).copyInto(quadMetricsEm, at)
        }
    }

    /** Every glyph's field in its tile, one byte a texel, 0.5 on the outline; texels past the reach stay 0. */
    fun bake(): ByteArray {
        val field = ByteArray(width * height)
        val scale = TEXELS_PER_EM / unitsPerEm
        for (index in glyphs.indices) {
            if (glyphs[index].bounds == null) continue
            val at = index * 4
            val left = tileXs[index] + MARGIN
            val top = tileYs[index] + MARGIN
            val penX = (left - rects[at]).toDouble()
            val baseline = top - rects[at + 1] + TEXELS_PER_EM * (font.ascender / unitsPerEm)
            val glyphField = GlyphField(glyphs[index].outline, penX, baseline, scale, REACH_TEXELS.toDouble())
            for (y in top until top + rects[at + 3] - rects[at + 1]) {
                for (x in left until left + rects[at + 2] - rects[at]) {
                    val value = ceil(BYTE_MAX * glyphField.value(x + 0.5, y + 0.5) - 0.5).toInt()
                    field[y * width + x] = value.coerceIn(0, BYTE_MAX.toInt()).toByte()
                }
            }
        }
        return field
    }

    companion object {
        /** The generator's `EFFECT_TEXELS_PER_EM`; a change there is a change here. */
        const val TEXELS_PER_EM = 16

        /** Half the generator's `EFFECT_RANGE_TEXELS`. */
        const val REACH_TEXELS = 8

        /** The spread [UiFont.effectFieldRangePx] reports for this field. */
        const val RANGE_TEXELS = 2f * REACH_TEXELS
        private const val MARGIN = 1
        private const val BYTE_MAX = 255.0

        /**
         * Texels baked times their glyph's segments, a tenth of a second or so: an effect field is a nicety, so a
         * glyph set this costly loads without one and stamps its decorated text, rather than loading slowly.
         */
        private const val MAX_WORK = 100_000_000L

        /**
         * [glyphs]' effect field under an atlas [atlasWidth] by [atlasHeight], or null when it would cost more than
         * [MAX_WORK] or make the texture taller than [maxTextureSize].
         */
        fun of(font: OpenTypeFont, glyphs: List<FontGlyph>, atlasWidth: Int, atlasHeight: Int, maxTextureSize: Int): EffectFieldLayout? {
            val rects = IntArray(glyphs.size * 4)
            var work = 0L
            var fits = glyphs.isNotEmpty()
            for (index in glyphs.indices) {
                val rect = rectOf(font, glyphs[index]) ?: intArrayOf(0, 0, 1, 1)
                rect.copyInto(rects, index * 4)
                work += (rect[2] - rect[0]).toLong() * (rect[3] - rect[1]) * glyphs[index].outline.size
                fits = fits && work <= MAX_WORK && rect[2] - rect[0] <= maxTextureSize && rect[3] - rect[1] <= maxTextureSize
            }
            if (!fits) return null
            val packing = ShelfPacking.packToWidth(
                atlasWidth,
                IntArray(glyphs.size) { rects[it * 4 + 2] - rects[it * 4] + 2 * MARGIN },
                IntArray(glyphs.size) { rects[it * 4 + 3] - rects[it * 4 + 1] + 2 * MARGIN },
            )
            return EffectFieldLayout(font, glyphs, rects, packing.xs, packing.ys, packing.width, packing.height)
                .takeIf { packing.width <= maxTextureSize && atlasHeight + packing.height <= maxTextureSize }
        }

        /** [glyph]'s tile from its pen origin and line top in whole field texels: its ink and the reach around it. */
        private fun rectOf(font: OpenTypeFont, glyph: FontGlyph): IntArray? {
            val bounds = glyph.bounds ?: return null
            val scale = TEXELS_PER_EM / font.unitsPerEm.toDouble()
            return intArrayOf(
                floor(bounds[0] * scale - REACH_TEXELS).toInt(),
                floor((font.ascender - bounds[3]) * scale - REACH_TEXELS).toInt(),
                ceil(bounds[2] * scale + REACH_TEXELS).toInt(),
                ceil((font.ascender - bounds[1]) * scale + REACH_TEXELS).toInt(),
            ).takeIf { min(it[2] - it[0], it[3] - it[1]) > 0 && max(it[2] - it[0], it[3] - it[1]) < Int.MAX_VALUE / 2 }
        }
    }
}
