/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Base64
import java.util.zip.Deflater
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Effect-field texels per em: coarse, since an outline or a shadow is smooth where the glyph's own edge is not. Not
 * the 24 #1061 first named: `WeightedUiFont` stacks its seven faces in one texture, and at 24 their atlases and fields
 * together pass the 8192 rows every backend supports.
 */
internal const val EFFECT_TEXELS_PER_EM = 16

/**
 * The effect field's whole range in its own texels, `-pxrange` to msdfgen: it reaches half of this, 8 texels or
 * half an em, either side of the outline. A 4 dp outline on 12 px text needs a third of an em, and a shadow its
 * offset and blur on top of that.
 */
internal const val EFFECT_RANGE_TEXELS = 16

private const val EFFECT_REACH_TEXELS = EFFECT_RANGE_TEXELS / 2

/** Texels between two effect tiles, so a bilinear sample at a tile's clamped edge never reads its neighbour. */
private const val EFFECT_TILE_MARGIN = 1

/**
 * Every glyph's effect field: a true signed distance per glyph at [EFFECT_TEXELS_PER_EM] texels per em, reaching
 * [EFFECT_RANGE_TEXELS] / 2 texels past its ink, packed in shelves like the atlas and kept beside it. The glyph
 * shader reads its outline and its shadow from here, which the atlas's own 2-texel range cannot hold (#1061).
 *
 * @property width The packed field's width in texels.
 * @property height Its height in texels.
 * @property uvBoundsPx Each glyph's tile rect (left, top, right, bottom) in field texels, in glyph order.
 * @property quadMetricsEm The rect each tile covers, in em from the pen origin and line top, in glyph order.
 * @property encodedBase64 The field's bytes, one a texel, row-major, through [encodeEffectField].
 */
internal class EffectField(
    val width: Int,
    val height: Int,
    val uvBoundsPx: IntArray,
    val quadMetricsEm: FloatArray,
    val encodedBase64: String,
)

/**
 * Bakes [glyphs]' effect field from [fontFile] with msdfgen's `sdf` mode, packed [atlasWidth] wide to sit under the
 * atlas; [metrics] are their outline-exact ink boxes.
 */
internal fun generateEffectField(
    fontFile: File,
    glyphs: List<Char>,
    metrics: List<GlyphMetrics>,
    ascentPx: Float,
    atlasWidth: Int,
): EffectField {
    val rects = metrics.map(::effectRect)
    val packing = ShelfPacking.packToWidth(
        atlasWidth,
        IntArray(rects.size) { rects[it][2] - rects[it][0] + 2 * EFFECT_TILE_MARGIN },
        IntArray(rects.size) { rects[it][3] - rects[it][1] + 2 * EFFECT_TILE_MARGIN },
    )
    val field = ByteArray(packing.width * packing.height)
    val uvBounds = IntArray(glyphs.size * 4)
    val quads = FloatArray(glyphs.size * 4)
    glyphs.forEachIndexed { index, char ->
        val rect = rects[index]
        val left = rect[0]
        val top = rect[1]
        val right = rect[2]
        val bottom = rect[3]
        val x = packing.xs[index] + EFFECT_TILE_MARGIN
        val y = packing.ys[index] + EFFECT_TILE_MARGIN
        intArrayOf(x, y, x + right - left, y + bottom - top).copyInto(uvBounds, index * 4)
        if (metrics[index].isBlank) return@forEachIndexed
        val scale = EFFECT_TEXELS_PER_EM.toFloat()
        floatArrayOf(left / scale, top / scale, (right - left) / scale, (bottom - top) / scale).copyInto(quads, index * 4)
        val tile = renderGlyphTile(
            fontFile = fontFile,
            char = char,
            mode = "sdf",
            pxRange = EFFECT_RANGE_TEXELS,
            texelsPerEm = EFFECT_TEXELS_PER_EM,
            tileWidthPx = right - left,
            tileHeightPx = bottom - top,
            penInTileX = -left,
            lineTopInTileY = -top,
            ascentPx = ascentPx,
        ) ?: return@forEachIndexed
        // The raster's own sample, not getRGB: msdfgen writes an `sdf` as a greyscale PNG, and getRGB converts grey
        // from linear to sRGB, which bends the distance ramp.
        val raster = tile.raster
        for (row in 0 until minOf(tile.height, bottom - top)) {
            for (column in 0 until minOf(tile.width, right - left)) {
                field[(y + row) * packing.width + x + column] = raster.getSample(column, row, 0).toByte()
            }
        }
    }
    return EffectField(packing.width, packing.height, uvBounds, quads, encodeEffectField(field, packing.width))
}

/**
 * A glyph's effect tile (left, top, right, bottom) in whole field texels from its pen origin and line top: its ink
 * box and the field's reach around it, snapped outward. A blank glyph gets a one-texel tile it never draws.
 */
private fun effectRect(metrics: GlyphMetrics): IntArray {
    if (metrics.isBlank) return intArrayOf(0, 0, 1, 1)
    val scale = EFFECT_TEXELS_PER_EM
    val left = metrics.offsetXEm * scale
    val top = metrics.offsetYEm * scale
    return intArrayOf(
        floor(left - EFFECT_REACH_TEXELS).toInt(),
        floor(top - EFFECT_REACH_TEXELS).toInt(),
        ceil(left + metrics.widthEm * scale + EFFECT_REACH_TEXELS).toInt(),
        ceil(top + metrics.heightEm * scale + EFFECT_REACH_TEXELS).toInt(),
    )
}

/**
 * [field] as `PackedUiFont` reads it back: each row's texels as differences from the texel to their left (PNG's
 * `Sub` filter, which turns a distance ramp into a run of one value), zlib-deflated, then base64. A field this
 * smooth deflates to a fraction of its texels, where the raw bytes would double each generated face.
 */
internal fun encodeEffectField(field: ByteArray, width: Int): String {
    val filtered = ByteArray(field.size)
    for (index in field.indices) {
        val left = if (index % width == 0) 0 else field[index - 1].toInt()
        filtered[index] = (field[index] - left).toByte()
    }
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    deflater.setInput(filtered)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(DEFLATE_CHUNK)
    while (!deflater.finished()) out.write(buffer, 0, deflater.deflate(buffer))
    deflater.end()
    return Base64.getEncoder().encodeToString(out.toByteArray())
}

private const val DEFLATE_CHUNK = 1 shl 16
