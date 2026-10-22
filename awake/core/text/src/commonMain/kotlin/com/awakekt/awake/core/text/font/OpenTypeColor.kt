/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import com.awakekt.awake.core.color.Color

/**
 * A single colored layer in an OpenType COLR glyph record.
 *
 * @property glyphId Glyph ID of the layer outline in the 'glyf' or 'CFF ' table.
 * @property paletteIndex Index into the CPAL color palette, or 0xFFFF for foreground color.
 */
internal data class ColrLayer(
    val glyphId: Int,
    val paletteIndex: Int,
)

/**
 * Parses the OpenType CPAL (Color Palette) table (v0 and v1).
 */
internal class CpalTable(private val table: FontTable) {
    private val numPalettes = table.u16(4)
    private val colorRecordsArrayOffset = table.u32(8)
    private val colorRecordIndices = IntArray(numPalettes) { table.u16(12 + it * 2) }

    @Suppress("ReturnCount")
    fun color(paletteIndex: Int, palette: Int = 0): Color {
        if (paletteIndex == 0xFFFF || palette !in 0 until numPalettes) return Color.White
        val baseIndex = colorRecordIndices[palette]
        val offset = colorRecordsArrayOffset + (baseIndex + paletteIndex) * 4
        if (offset + 4 > table.length) return Color.White
        val b = table.u8(offset)
        val g = table.u8(offset + 1)
        val r = table.u8(offset + 2)
        val a = table.u8(offset + 3)
        return Color(r / 255f, g / 255f, b / 255f, a / 255f)
    }
}

/**
 * Parses the OpenType COLR (Color) table (v0 base glyph records and layer records).
 */
internal class ColrTable(private val table: FontTable) {
    private val numBaseGlyphRecords = table.u16(2)
    private val baseGlyphRecordsOffset = table.u32(4)
    private val layerRecordsOffset = table.u32(8)
    private val numLayerRecords = table.u16(12)

    @Suppress("ReturnCount")
    fun layers(glyphId: Int): List<ColrLayer>? {
        var low = 0
        var high = numBaseGlyphRecords - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val offset = baseGlyphRecordsOffset + mid * 6
            val id = table.u16(offset)
            when {
                id < glyphId -> low = mid + 1
                id > glyphId -> high = mid - 1
                else -> {
                    val firstLayer = table.u16(offset + 2)
                    val count = table.u16(offset + 4)
                    if (firstLayer + count > numLayerRecords) return null
                    return List(count) { i ->
                        val layerOffset = layerRecordsOffset + (firstLayer + i) * 4
                        ColrLayer(
                            glyphId = table.u16(layerOffset),
                            paletteIndex = table.u16(layerOffset + 2),
                        )
                    }
                }
            }
        }
        return null
    }
}

/**
 * Parses the OpenType sbix (Standard Bitmap Graphics) table used by Apple Color Emoji.
 */
internal class SbixTable(private val table: FontTable, private val glyphCount: Int) {
    private val numStrikes = table.u32(4)
    private val strikeOffsets = IntArray(numStrikes) { table.u32(8 + it * 4) }

    @Suppress("ReturnCount")
    fun pngBytes(glyphId: Int): ByteArray? {
        if (numStrikes == 0 || glyphId !in 0 until glyphCount) return null
        // Pick the last (usually largest/highest-resolution) strike
        val strikeOffset = strikeOffsets[numStrikes - 1]
        val glyphDataOffsets = strikeOffset + 4
        val offset = table.u32(glyphDataOffsets + glyphId * 4)
        val nextOffset = table.u32(glyphDataOffsets + (glyphId + 1) * 4)
        val length = nextOffset - offset
        if (length <= 8) return null
        val dataOffset = strikeOffset + offset
        val graphicType = table.u32(dataOffset + 4)
        // 0x706E6720 is ASCII 'png '
        if (graphicType != PNG_TAG) return null
        return table.bytes(dataOffset + 8, length - 8)
    }

    private companion object {
        const val PNG_TAG = 0x706E6720
    }
}

/**
 * Parses the OpenType CBLC and CBDT tables used by Google Noto Color Emoji bitmap flavor.
 */
internal class CbdtCblcTable(private val cblc: FontTable, private val cbdt: FontTable) {
    @Suppress("ReturnCount")
    fun pngBytes(glyphId: Int): ByteArray? {
        val numSizes = cblc.u32(4)
        if (numSizes <= 0) return null
        // Look through size tables for one containing glyphId
        for (i in 0 until numSizes) {
            val sizeOffset = 8 + i * 48
            val startGlyph = cblc.u16(sizeOffset + 40)
            val endGlyph = cblc.u16(sizeOffset + 42)
            if (glyphId in startGlyph..endGlyph) {
                return readGlyphFromStrike(sizeOffset, glyphId)
            }
        }
        return null
    }

    private fun readGlyphFromStrike(sizeOffset: Int, glyphId: Int): ByteArray? {
        val indexSubTableArrayOffset = cblc.u32(sizeOffset)
        val numIndexSubTables = cblc.u32(sizeOffset + 8)
        for (j in 0 until numIndexSubTables) {
            val subArrayOffset = indexSubTableArrayOffset + j * 8
            val first = cblc.u16(subArrayOffset)
            val last = cblc.u16(subArrayOffset + 2)
            if (glyphId in first..last) {
                val subTableOffset = indexSubTableArrayOffset + cblc.u32(subArrayOffset + 4)
                return readSubTable(subTableOffset, glyphId - first)
            }
        }
        return null
    }

    @Suppress("ReturnCount")
    private fun readSubTable(subTableOffset: Int, indexInSubTable: Int): ByteArray? {
        val indexFormat = cblc.u16(subTableOffset)
        val imageFormat = cblc.u16(subTableOffset + 2)
        val imageDataOffset = cblc.u32(subTableOffset + 4)
        val offset = when (indexFormat) {
            1 -> cblc.u32(subTableOffset + 8 + indexInSubTable * 4)
            3 -> cblc.u16(subTableOffset + 8 + indexInSubTable * 2)
            else -> return null
        }
        val nextOffset = when (indexFormat) {
            1 -> cblc.u32(subTableOffset + 8 + (indexInSubTable + 1) * 4)
            3 -> cblc.u16(subTableOffset + 8 + (indexInSubTable + 1) * 2)
            else -> return null
        }
        val length = nextOffset - offset
        // Format 17 has 5 bytes SmallGlyphMetrics followed by PNG bytes
        if (imageFormat == 17 && length > 5) {
            return cbdt.bytes(imageDataOffset + offset + 5, length - 5)
        }
        return null
    }
}

/**
 * Parses OpenType cmap format 14 (Unicode Variation Sequences) for U+FE0E and U+FE0F.
 */
internal class UnicodeVariationSequences(cmap: FontTable) {
    private var subtableOffset = -1
    private val cmapTable = cmap

    init {
        val numSubtables = cmap.u16(2)
        for (i in 0 until numSubtables) {
            val offset = cmap.u32(4 + i * 8 + 4)
            if (cmap.u16(offset) == 14) {
                subtableOffset = offset
                break
            }
        }
    }

    val isAvailable: Boolean get() = subtableOffset >= 0

    @Suppress("ReturnCount", "NestedBlockDepth")
    fun findGlyph(codePoint: Int, variationSelector: Int): Int? {
        val base = subtableOffset
        if (base < 0) return null
        val numRecords = cmapTable.u32(base + 6)
        var low = 0
        var high = numRecords - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val rec = base + 10 + mid * 11
            val vs = cmapTable.u24(rec)
            when {
                vs < variationSelector -> low = mid + 1
                vs > variationSelector -> high = mid - 1
                else -> {
                    val nonDefOffset = cmapTable.u32(rec + 7)
                    if (nonDefOffset != 0) {
                        val glyph = searchNonDefault(base + nonDefOffset, codePoint)
                        if (glyph != null) return glyph
                    }
                    val defOffset = cmapTable.u32(rec + 3)
                    if (defOffset != 0 && inDefaultRanges(base + defOffset, codePoint)) {
                        return -1 // Signal that default mapping should be used
                    }
                    return null
                }
            }
        }
        return null
    }

    private fun searchNonDefault(tableOffset: Int, codePoint: Int): Int? {
        val count = cmapTable.u32(tableOffset)
        var low = 0
        var high = count - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val rec = tableOffset + 4 + mid * 5
            val cp = cmapTable.u24(rec)
            when {
                cp < codePoint -> low = mid + 1
                cp > codePoint -> high = mid - 1
                else -> return cmapTable.u16(rec + 3)
            }
        }
        return null
    }

    private fun inDefaultRanges(tableOffset: Int, codePoint: Int): Boolean {
        val count = cmapTable.u32(tableOffset)
        var low = 0
        var high = count - 1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val rec = tableOffset + 4 + mid * 4
            val start = cmapTable.u24(rec)
            val additional = cmapTable.u8(rec + 3)
            when {
                codePoint < start -> high = mid - 1
                codePoint > start + additional -> low = mid + 1
                else -> return true
            }
        }
        return false
    }
}
