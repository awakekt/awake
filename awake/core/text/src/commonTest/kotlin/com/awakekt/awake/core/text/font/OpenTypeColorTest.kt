/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenTypeColorTest {

    @Test
    fun cpalTableExtractsBgraColors() {
        // Construct a synthetic CPAL table:
        // version: 0 (u16)
        // numPaletteEntries: 2 (u16)
        // numPalettes: 1 (u16)
        // numColorRecords: 2 (u16)
        // colorRecordsArrayOffset: 14 (u32)
        // colorRecordIndices: [0] (u16)
        // Color 0: Blue=0, Green=255, Red=128, Alpha=255
        // Color 1: Blue=255, Green=0, Red=0, Alpha=200
        val bytes = ByteArray(22)
        bytes[0] = 0; bytes[1] = 0 // version 0
        bytes[2] = 0; bytes[3] = 2 // numPaletteEntries = 2
        bytes[4] = 0; bytes[5] = 1 // numPalettes = 1
        bytes[6] = 0; bytes[7] = 2 // numColorRecords = 2
        bytes[8] = 0; bytes[9] = 0; bytes[10] = 0; bytes[11] = 14 // colorRecordsArrayOffset = 14
        bytes[12] = 0; bytes[13] = 0 // colorRecordIndices[0] = 0
        // Record 0 (offset 14): B=0, G=255, R=128, A=255
        bytes[14] = 0; bytes[15] = 255.toByte(); bytes[16] = 128.toByte(); bytes[17] = 255.toByte()
        // Record 1 (offset 18): B=255, G=0, R=0, A=200
        bytes[18] = 255.toByte(); bytes[19] = 0; bytes[20] = 0; bytes[21] = 200.toByte()

        val table = FontTable(bytes, 0, bytes.size, "CPAL")
        val cpal = CpalTable(table)

        val col0 = cpal.color(0)
        assertEquals(128 / 255f, col0.r, 1e-3f)
        assertEquals(1f, col0.g, 1e-3f)
        assertEquals(0f, col0.b, 1e-3f)
        assertEquals(1f, col0.a, 1e-3f)

        val col1 = cpal.color(1)
        assertEquals(0f, col1.r, 1e-3f)
        assertEquals(0f, col1.g, 1e-3f)
        assertEquals(1f, col1.b, 1e-3f)
        assertEquals(200 / 255f, col1.a, 1e-3f)
    }

    @Test
    fun colrTableFindsLayersViaBinarySearch() {
        // Construct a synthetic COLRv0 table:
        // version: 0 (u16)
        // numBaseGlyphRecords: 2 (u16)
        // baseGlyphRecordsOffset: 14 (u32)
        // layerRecordsOffset: 26 (u32)
        // numLayerRecords: 3 (u16)
        // BaseGlyphRecord 0 (glyph 10): firstLayer=0, count=2
        // BaseGlyphRecord 1 (glyph 20): firstLayer=2, count=1
        // LayerRecord 0: glyph 101, paletteIndex 1
        // LayerRecord 1: glyph 102, paletteIndex 2
        // LayerRecord 2: glyph 201, paletteIndex 0
        val bytes = ByteArray(38)
        bytes[0] = 0; bytes[1] = 0 // version 0
        bytes[2] = 0; bytes[3] = 2 // numBaseGlyphRecords = 2
        bytes[4] = 0; bytes[5] = 0; bytes[6] = 0; bytes[7] = 14 // baseGlyphRecordsOffset = 14
        bytes[8] = 0; bytes[9] = 0; bytes[10] = 0; bytes[11] = 26 // layerRecordsOffset = 26
        bytes[12] = 0; bytes[13] = 3 // numLayerRecords = 3

        // Base 0 at offset 14: glyph 10, firstLayer 0, count 2
        bytes[14] = 0; bytes[15] = 10; bytes[16] = 0; bytes[17] = 0; bytes[18] = 0; bytes[19] = 2
        // Base 1 at offset 20: glyph 20, firstLayer 2, count 1
        bytes[20] = 0; bytes[21] = 20; bytes[22] = 0; bytes[23] = 2; bytes[24] = 0; bytes[25] = 1

        // Layer 0 at offset 26: glyph 101, paletteIndex 1
        bytes[26] = 0; bytes[27] = 101; bytes[28] = 0; bytes[29] = 1
        // Layer 1 at offset 30: glyph 102, paletteIndex 2
        bytes[30] = 0; bytes[31] = 102; bytes[32] = 0; bytes[33] = 2
        // Layer 2 at offset 34: glyph 201, paletteIndex 0
        bytes[34] = 0; bytes[35] = 201.toByte(); bytes[36] = 0; bytes[37] = 0

        val table = FontTable(bytes, 0, bytes.size, "COLR")
        val colr = ColrTable(table)

        assertNull(colr.layers(5))
        assertNull(colr.layers(15))
        assertNull(colr.layers(25))

        val layers10 = colr.layers(10)
        assertNotNull(layers10)
        assertEquals(2, layers10.size)
        assertEquals(ColrLayer(101, 1), layers10[0])
        assertEquals(ColrLayer(102, 2), layers10[1])

        val layers20 = colr.layers(20)
        assertNotNull(layers20)
        assertEquals(1, layers20.size)
        assertEquals(ColrLayer(201, 0), layers20[0])
    }

    @Test
    fun variationSelectorResolvesNonDefaultGlyph() {
        // Cmap format 14 subtable synthetic test
        // 1 subtable at offset 12
        val cmapBytes = ByteArray(42)
        cmapBytes[0] = 0; cmapBytes[1] = 0 // version 0
        cmapBytes[2] = 0; cmapBytes[3] = 1 // 1 subtable
        cmapBytes[4] = 0; cmapBytes[5] = 0 // platform 0
        cmapBytes[6] = 0; cmapBytes[7] = 5 // encoding 5
        cmapBytes[8] = 0; cmapBytes[9] = 0; cmapBytes[10] = 0; cmapBytes[11] = 12 // offset 12

        // Format 14 at offset 12:
        // format: 14 (u16)
        // length: 30 (u32)
        // numVarSelectorRecords: 1 (u32)
        // Record 0: varSelector = 0xFE0F (u24), defaultUVSOffset = 0, nonDefaultUVSOffset = 21 (from 12 = 33)
        val f14 = 12
        cmapBytes[f14] = 0; cmapBytes[f14 + 1] = 14
        cmapBytes[f14 + 2] = 0; cmapBytes[f14 + 3] = 0; cmapBytes[f14 + 4] = 0; cmapBytes[f14 + 5] = 30
        cmapBytes[f14 + 6] = 0; cmapBytes[f14 + 7] = 0; cmapBytes[f14 + 8] = 0; cmapBytes[f14 + 9] = 1 // 1 record
        // rec 0 at f14 + 10 = 22:
        // varSelector = 0x00FE0F
        cmapBytes[22] = 0; cmapBytes[23] = 0xFE.toByte(); cmapBytes[24] = 0x0F
        // defaultUVSOffset = 0
        cmapBytes[25] = 0; cmapBytes[26] = 0; cmapBytes[27] = 0; cmapBytes[28] = 0
        // nonDefaultUVSOffset = 21 (from 12 -> offset 33)
        cmapBytes[29] = 0; cmapBytes[30] = 0; cmapBytes[31] = 0; cmapBytes[32] = 21

        // NonDefault UVS Table at offset 33:
        // numUVSMappings: 1 (u32)
        cmapBytes[33] = 0; cmapBytes[34] = 0; cmapBytes[35] = 0; cmapBytes[36] = 1
        // Mapping 0 at 37: unicodeValue = 0x002764 (u24), glyphID = 77 (u16)
        cmapBytes[37] = 0; cmapBytes[38] = 0x27; cmapBytes[39] = 0x64
        cmapBytes[40] = 0; cmapBytes[41] = 77

        val table = FontTable(cmapBytes, 0, cmapBytes.size, "cmap")
        val uvs = UnicodeVariationSequences(table)
        assertTrue(uvs.isAvailable)
        assertEquals(77, uvs.findGlyph(0x2764, 0xFE0F))
        assertNull(uvs.findGlyph(0x2764, 0xFE0E))
        assertNull(uvs.findGlyph(0x1234, 0xFE0F))
    }
}
