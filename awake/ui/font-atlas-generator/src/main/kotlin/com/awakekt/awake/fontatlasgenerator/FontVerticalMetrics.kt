/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import java.io.File
import java.nio.ByteBuffer

/**
 * A font's vertical metrics as the font file states them: `head.unitsPerEm` and the `hhea` table's
 * ascender, descender and line gap.
 *
 * The generator reads these in place of `java.awt.Font.getLineMetrics`, which answers from whatever
 * the platform's font scaler reports. For the Roboto files in `awake:core:text` that is `hhea` on
 * Linux (ascender 1900 of 2048) but the `OS/2` table's `winAscent`/`winDescent` on Windows (1946 and
 * 512), so the same command produced a taller line, a taller atlas cell and different glyph offsets
 * depending on the machine. Reading the table directly gives every machine the Linux answer the
 * committed atlas was built from.
 */
internal class FontVerticalMetrics(
    val unitsPerEm: Int,
    /** Distance from the baseline to the top of a line, in font units; positive. */
    val ascender: Int,
    /** Distance from the baseline to the bottom of a line, in font units; negative, as the file stores it. */
    val descender: Int,
    val lineGap: Int,
) {
    init {
        require(unitsPerEm > 0) { "unitsPerEm must be positive, got $unitsPerEm" }
    }

    /** The ascent at [size] (pixels per em), as a positive distance above the baseline. */
    fun ascent(size: Float): Float = size * ascender / unitsPerEm

    /** The descent at [size] (pixels per em), as a positive distance below the baseline. */
    fun descent(size: Float): Float = size * -descender / unitsPerEm

    companion object {
        /** Reads the metrics of the TrueType or OpenType font in [file]. */
        fun read(file: File): FontVerticalMetrics = parse(file.readBytes(), file.name)

        /**
         * Reads the metrics from the bytes of a TrueType or OpenType font; [name] is only for messages.
         *
         * @throws IllegalArgumentException when the bytes are not an sfnt font, or lack `head` or `hhea`.
         */
        fun parse(bytes: ByteArray, name: String = "font"): FontVerticalMetrics {
            require(bytes.size >= HEADER_SIZE) { "$name is too short to be a font" }
            val buffer = ByteBuffer.wrap(bytes)
            require(buffer.getInt(0) in SFNT_VERSIONS) { "$name is not a TrueType or OpenType font" }
            val head = tableOffset(buffer, "head", name)
            val hhea = tableOffset(buffer, "hhea", name)
            require(head + HEAD_UNITS_PER_EM + 2 <= bytes.size && hhea + HHEA_LINE_GAP + 2 <= bytes.size) {
                "$name has a truncated head or hhea table"
            }
            return FontVerticalMetrics(
                unitsPerEm = buffer.getShort(head + HEAD_UNITS_PER_EM).toInt() and 0xFFFF,
                ascender = buffer.getShort(hhea + HHEA_ASCENDER).toInt(),
                descender = buffer.getShort(hhea + HHEA_DESCENDER).toInt(),
                lineGap = buffer.getShort(hhea + HHEA_LINE_GAP).toInt(),
            )
        }

        private fun tableOffset(buffer: ByteBuffer, tag: String, name: String): Int {
            val wanted = tag.fold(0) { acc, c -> acc shl 8 or c.code }
            val count = buffer.getShort(TABLE_COUNT).toInt() and 0xFFFF
            for (index in 0 until count) {
                val entry = HEADER_SIZE + index * TABLE_RECORD_SIZE
                require(entry + TABLE_RECORD_SIZE <= buffer.limit()) { "$name has a truncated table directory" }
                if (buffer.getInt(entry) == wanted) return buffer.getInt(entry + TABLE_RECORD_OFFSET)
            }
            throw IllegalArgumentException("$name has no '$tag' table")
        }

        private const val TABLE_COUNT = 4
        private const val HEADER_SIZE = 12
        private const val TABLE_RECORD_SIZE = 16
        private const val TABLE_RECORD_OFFSET = 8
        private const val HEAD_UNITS_PER_EM = 18
        private const val HHEA_ASCENDER = 4
        private const val HHEA_DESCENDER = 6
        private const val HHEA_LINE_GAP = 8

        /** TrueType (`0x00010000`, `true`) and CFF OpenType (`OTTO`): every one of them has `head` and `hhea`. */
        private val SFNT_VERSIONS = setOf(0x00010000, 0x74727565, 0x4F54544F)
    }
}
