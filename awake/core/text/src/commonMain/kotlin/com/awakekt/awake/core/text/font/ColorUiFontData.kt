/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.text.unicode.CodePoints
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Packed font data for full-colour emoji fonts (COLRv0/CPAL, CBDT/CBLC, sbix).
 */
internal class ColorUiFontData private constructor(
    override val name: String,
    override val glyphOrder: String,
    private val layout: ColorGlyphLayout,
    private val atlas: ByteArray,
) : PackedUiFontData {
    override val lineHeightEm: Float = layout.lineHeightEm
    override val atlasWidth: Int get() = layout.atlasWidth
    override val atlasHeight: Int get() = layout.atlasHeight
    override val baseCellSize: Int = layout.cellSize
    override val textScaleStep: Float = 0.25f
    override val samplingMode: UiFontSamplingMode = UiFontSamplingMode.ColorRgba
    override val fallbackChar: Char = '?'
    override val atlasChannels: Int = 4
    override val encodedAtlasBase64: String = ""
    override val uvBoundsPx: IntArray get() = layout.uvBoundsPx
    override val quadMetricsEm: FloatArray get() = layout.quadMetricsEm
    override val inkMetricsEm: FloatArray get() = layout.inkMetricsEm
    override val advancesEm: FloatArray get() = layout.advancesEm

    override fun atlasBytes(): ByteArray = atlas

    companion object {
        fun load(
            bytes: ByteArray,
            name: String,
            emojis: String,
            cellSize: Int = 16,
        ): ColorUiFontData {
            val font = OpenTypeFont(bytes, name)
            val glyphs = parseEmojiGlyphs(font, emojis)
            require(glyphs.isNotEmpty()) { "$name has none of the requested emojis" }
            val layout = ColorGlyphLayout(font, glyphs, cellSize)
            val atlas = layout.bakeAtlas(font, glyphs)
            val glyphOrder = glyphs.joinToString("") { CodePoints.asString(it.codePoint) }
            return ColorUiFontData(name, glyphOrder, layout, atlas)
        }

        private fun parseEmojiGlyphs(font: OpenTypeFont, emojis: String): List<ColorEmojiGlyph> {
            val glyphs = ArrayList<ColorEmojiGlyph>()
            val seen = HashSet<Int>()
            var idx = 0
            while (idx < emojis.length) {
                val cp = CodePoints.at(emojis, idx)
                val len = CodePoints.charCount(cp)
                var vs = 0
                if (idx + len < emojis.length) {
                    val next = CodePoints.at(emojis, idx + len)
                    if (next == VS_TEXT || next == VS_EMOJI) {
                        vs = next
                    }
                }
                var glyphId = if (vs != 0) font.glyphFor(cp, vs) else 0
                if (glyphId == 0) glyphId = font.glyphFor(cp)
                if (glyphId > 0 && seen.add(cp)) {
                    glyphs += ColorEmojiGlyph(cp, glyphId, font)
                }
                idx += len + if (vs != 0) CodePoints.charCount(vs) else 0
            }
            return glyphs
        }

        private const val VS_TEXT = 0xFE0E
        private const val VS_EMOJI = 0xFE0F
    }
}

internal class ColorEmojiGlyph(
    val codePoint: Int,
    val glyphId: Int,
    font: OpenTypeFont,
) {
    val advance: Int = font.advance(glyphId)
    val colrLayers: List<ColrLayer>? = font.colr?.layers(glyphId)
    val sbixPng: ByteArray? = font.sbix?.pngBytes(glyphId)
    val cbdtPng: ByteArray? = font.cbdt?.pngBytes(glyphId)
    val bounds: DoubleArray? = computeBounds(font)

    private fun computeBounds(font: OpenTypeFont): DoubleArray? {
        val layers = colrLayers
        if (layers != null && layers.isNotEmpty()) {
            var minX = Double.POSITIVE_INFINITY
            var minY = Double.POSITIVE_INFINITY
            var maxX = Double.NEGATIVE_INFINITY
            var maxY = Double.NEGATIVE_INFINITY
            for (layer in layers) {
                val b = outlineBounds(font.outline(layer.glyphId)) ?: continue
                minX = min(minX, b[0])
                minY = min(minY, b[1])
                maxX = max(maxX, b[2])
                maxY = max(maxY, b[3])
            }
            if (minX <= maxX && minY <= maxY) {
                return doubleArrayOf(minX, minY, maxX, maxY)
            }
        }
        val upe = font.unitsPerEm.toDouble()
        return doubleArrayOf(0.0, 0.0, upe, upe)
    }
}

internal class ColorGlyphLayout(
    private val font: OpenTypeFont,
    glyphs: List<ColorEmojiGlyph>,
    val cellSize: Int,
) {
    private val unitsPerEm = font.unitsPerEm.toDouble()
    private val texelsPerEm = cellSize * 2
    val lineHeightEm: Float = (font.ascender - font.descender) / font.unitsPerEm.toFloat()
    val cellWidth: Int = texelsPerEm + PADDING * 2
    val cellHeight: Int = texelsPerEm + PADDING * 2
    private val columns: Int = max(1, ceil(sqrt(glyphs.size.toDouble())).toInt())
    val atlasWidth: Int = cellWidth * columns
    val atlasHeight: Int = cellHeight * ((glyphs.size + columns - 1) / columns)
    val uvBoundsPx = IntArray(glyphs.size * 4)
    val quadMetricsEm = FloatArray(glyphs.size * 4)
    val inkMetricsEm = FloatArray(glyphs.size * 4)
    val advancesEm = FloatArray(glyphs.size) { glyphs[it].advance / unitsPerEm.toFloat() }

    init {
        glyphs.forEachIndexed { i, glyph ->
            val cx = i % columns * cellWidth
            val cy = i / columns * cellHeight
            val left = cx + PADDING
            val top = cy + PADDING
            val right = left + texelsPerEm
            val bottom = top + texelsPerEm
            val at = i * 4
            intArrayOf(left, top, right, bottom).copyInto(uvBoundsPx, at)
            val scale = texelsPerEm.toFloat()
            val descentOffset = (font.descender / unitsPerEm).toFloat()
            // Em quad placed relative to baseline
            floatArrayOf(0f, -lineHeightEm - descentOffset, 1f, 1f).copyInto(quadMetricsEm, at)
            floatArrayOf(0f, -lineHeightEm - descentOffset, 1f, 1f).copyInto(inkMetricsEm, at)
        }
    }

    fun bakeAtlas(font: OpenTypeFont, glyphs: List<ColorEmojiGlyph>): ByteArray {
        val atlas = ByteArray(atlasWidth * atlasHeight * 4)
        val scale = texelsPerEm / unitsPerEm
        glyphs.forEachIndexed { i, glyph ->
            val cellLeft = uvBoundsPx[i * 4]
            val cellTop = uvBoundsPx[i * 4 + 1]
            val cellRight = uvBoundsPx[i * 4 + 2]
            val cellBottom = uvBoundsPx[i * 4 + 3]
            val penX = cellLeft.toDouble()
            val baseline = cellBottom.toDouble() + font.descender * scale

            when {
                glyph.colrLayers != null -> {
                    bakeColrGlyph(atlas, font, glyph.colrLayers, penX, baseline, scale, cellLeft, cellTop, cellRight, cellBottom)
                }
                glyph.sbixPng != null -> {
                    bakePngGlyph(atlas, glyph.sbixPng, cellLeft, cellTop, cellRight - cellLeft, cellBottom - cellTop)
                }
                glyph.cbdtPng != null -> {
                    bakePngGlyph(atlas, glyph.cbdtPng, cellLeft, cellTop, cellRight - cellLeft, cellBottom - cellTop)
                }
            }
        }
        return atlas
    }

    @Suppress("LongParameterList")
    private fun bakeColrGlyph(
        atlas: ByteArray,
        font: OpenTypeFont,
        layers: List<ColrLayer>,
        penX: Double,
        baseline: Double,
        scale: Double,
        cLeft: Int,
        cTop: Int,
        cRight: Int,
        cBottom: Int,
    ) {
        val cpal = font.cpal
        for (layer in layers) {
            renderColrLayer(atlas, font, layer, cpal, penX, baseline, scale, cLeft, cTop, cRight, cBottom)
        }
    }

    @Suppress("LongParameterList", "NestedBlockDepth")
    private fun renderColrLayer(
        atlas: ByteArray,
        font: OpenTypeFont,
        layer: ColrLayer,
        cpal: CpalTable?,
        penX: Double,
        baseline: Double,
        scale: Double,
        cLeft: Int,
        cTop: Int,
        cRight: Int,
        cBottom: Int,
    ) {
        val outline = font.outline(layer.glyphId)
        if (outline.isEmpty()) return
        val bounds = outlineBounds(outline) ?: return
        val color = cpal?.color(layer.paletteIndex) ?: Color.White
        val curves = outline.map { seg ->
            val p = seg.points
            FieldCurve(DoubleArray(8) { if (it % 2 == 0) penX + p[it] * scale else baseline - p[it] * scale }, seg.isLine)
        }
        val minPx = max(cLeft, floor(penX + bounds[0] * scale).toInt())
        val maxPx = min(cRight, ceil(penX + bounds[2] * scale).toInt())
        val minPy = max(cTop, floor(baseline - bounds[3] * scale).toInt())
        val maxPy = min(cBottom, ceil(baseline - bounds[1] * scale).toInt())

        for (py in minPy until maxPy) {
            for (px in minPx until maxPx) {
                var inside = 0
                for (sy in 0..1) {
                    for (sx in 0..1) {
                        val sampleX = px + 0.25 + sx * 0.5
                        val sampleY = py + 0.25 + sy * 0.5
                        if (curves.sumOf { it.crossings(sampleX, sampleY) } != 0) inside++
                    }
                }
                if (inside > 0) {
                    val coverage = inside / 4f
                    val offset = (py * atlasWidth + px) * 4
                    compositeRgba(atlas, offset, color.r, color.g, color.b, color.a * coverage)
                }
            }
        }
    }

    @Suppress("LongParameterList")
    private fun bakePngGlyph(
        atlas: ByteArray,
        pngBytes: ByteArray,
        destX: Int,
        destY: Int,
        destW: Int,
        destH: Int,
    ) {
        val image = runCatching { PngDecoder.decode(pngBytes) }.getOrNull() ?: return
        val srcW = image.width
        val srcH = image.height
        val src = image.rgba
        for (dy in 0 until destH) {
            val py = destY + dy
            val v = (dy + 0.5f) / destH * srcH - 0.5f
            val sy0 = v.toInt().coerceIn(0, srcH - 1)
            val sy1 = (sy0 + 1).coerceIn(0, srcH - 1)
            val ty = (v - sy0).coerceIn(0f, 1f)
            for (dx in 0 until destW) {
                val px = destX + dx
                val u = (dx + 0.5f) / destW * srcW - 0.5f
                val sx0 = u.toInt().coerceIn(0, srcW - 1)
                val sx1 = (sx0 + 1).coerceIn(0, srcW - 1)
                val tx = (u - sx0).coerceIn(0f, 1f)

                fun sampleCh(ch: Int): Float {
                    val c00 = (src[(sy0 * srcW + sx0) * 4 + ch].toInt() and 0xFF) / 255f
                    val c10 = (src[(sy0 * srcW + sx1) * 4 + ch].toInt() and 0xFF) / 255f
                    val c01 = (src[(sy1 * srcW + sx0) * 4 + ch].toInt() and 0xFF) / 255f
                    val c11 = (src[(sy1 * srcW + sx1) * 4 + ch].toInt() and 0xFF) / 255f
                    return (c00 * (1f - tx) + c10 * tx) * (1f - ty) + (c01 * (1f - tx) + c11 * tx) * ty
                }

                val offset = (py * atlasWidth + px) * 4
                compositeRgba(atlas, offset, sampleCh(0), sampleCh(1), sampleCh(2), sampleCh(3))
            }
        }
    }

    @Suppress("LongParameterList")
    private fun compositeRgba(
        atlas: ByteArray,
        offset: Int,
        srcR: Float,
        srcG: Float,
        srcB: Float,
        srcA: Float,
    ) {
        if (srcA <= 0f) return
        val dstR = (atlas[offset].toInt() and 0xFF) / 255f
        val dstG = (atlas[offset + 1].toInt() and 0xFF) / 255f
        val dstB = (atlas[offset + 2].toInt() and 0xFF) / 255f
        val dstA = (atlas[offset + 3].toInt() and 0xFF) / 255f

        val outA = srcA + dstA * (1f - srcA)
        if (outA > 0f) {
            val outR = (srcR * srcA + dstR * dstA * (1f - srcA)) / outA
            val outG = (srcG * srcA + dstG * dstA * (1f - srcA)) / outA
            val outB = (srcB * srcA + dstB * dstA * (1f - srcA)) / outA
            atlas[offset] = (outR * 255f).toInt().coerceIn(0, 255).toByte()
            atlas[offset + 1] = (outG * 255f).toInt().coerceIn(0, 255).toByte()
            atlas[offset + 2] = (outB * 255f).toInt().coerceIn(0, 255).toByte()
            atlas[offset + 3] = (outA * 255f).toInt().coerceIn(0, 255).toByte()
        }
    }

    private companion object {
        const val PADDING = 2
    }
}
