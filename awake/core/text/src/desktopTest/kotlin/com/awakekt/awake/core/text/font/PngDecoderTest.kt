/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.CRC32
import java.util.zip.Deflater
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PngDecoderTest {

    @Test
    fun decodesStandardRgbaPng() {
        val width = 4
        val height = 4
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        // Red, Green, Blue, Semi-transparent Yellow
        img.setRGB(0, 0, 0xFFFF0000.toInt())
        img.setRGB(1, 0, 0xFF00FF00.toInt())
        img.setRGB(2, 0, 0xFF0000FF.toInt())
        img.setRGB(3, 0, 0x80FFFF00.toInt())

        val stream = ByteArrayOutputStream()
        ImageIO.write(img, "png", stream)
        val bytes = stream.toByteArray()

        val decoded = PngDecoder.decode(bytes)
        assertEquals(width, decoded.width)
        assertEquals(height, decoded.height)
        assertEquals(width * height * 4, decoded.rgba.size)

        // Pixel (0, 0): Red (255, 0, 0, 255)
        assertEquals(255, decoded.rgba[0].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[1].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[2].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[3].toInt() and 0xFF)

        // Pixel (1, 0): Green (0, 255, 0, 255)
        assertEquals(0, decoded.rgba[4].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[5].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[6].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[7].toInt() and 0xFF)

        // Pixel (2, 0): Blue (0, 0, 255, 255)
        assertEquals(0, decoded.rgba[8].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[9].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[10].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[11].toInt() and 0xFF)

        // Pixel (3, 0): Semi-transparent Yellow (255, 255, 0, 128)
        assertEquals(255, decoded.rgba[12].toInt() and 0xFF)
        assertEquals(255, decoded.rgba[13].toInt() and 0xFF)
        assertEquals(0, decoded.rgba[14].toInt() and 0xFF)
        assertEquals(128, decoded.rgba[15].toInt() and 0xFF)
    }

    @Test
    fun decodesAllFivePngFiltersMultiIdatAndStoredBlocksBitExact() {
        val width = 37
        val height = 29
        val rng = Random(42)
        val rgba = ByteArray(width * height * 4)
        for (i in rgba.indices) {
            rgba[i] = if (i % 24 < 12) (i / 4 % 256).toByte() else rng.nextInt(256).toByte()
        }

        for (filterType in 0..4) {
            val single = buildRgbaPng(rgba, width, height, filterType, Deflater.BEST_COMPRESSION, idatChunkSize = Int.MAX_VALUE)
            val decodedSingle = PngDecoder.decode(single)
            assertEquals(width, decodedSingle.width)
            assertEquals(height, decodedSingle.height)
            assertContentEquals(rgba, decodedSingle.rgba, "filter=$filterType single IDAT")
        }

        val multiIdat = buildRgbaPng(rgba, width, height, filterType = 4, level = Deflater.BEST_COMPRESSION, idatChunkSize = 257)
        assertContentEquals(rgba, PngDecoder.decode(multiIdat).rgba, "multi-IDAT PNG")

        val stored = buildRgbaPng(rgba, width, height, filterType = 0, level = Deflater.NO_COMPRESSION, idatChunkSize = 512)
        assertContentEquals(rgba, PngDecoder.decode(stored).rgba, "uncompressed deflate block PNG")
    }

    private fun buildRgbaPng(
        rgba: ByteArray,
        width: Int,
        height: Int,
        filterType: Int,
        level: Int,
        idatChunkSize: Int,
    ): ByteArray {
        val scanlines = filterRgbaScanlines(rgba, width, height, filterType)
        val idat = deflateZlib(scanlines, level)
        val png = ByteArrayOutputStream()
        png.write(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A))
        val ihdr = ByteArrayOutputStream(13)
        DataOutputStream(ihdr).use {
            it.writeInt(width)
            it.writeInt(height)
            it.writeByte(8)
            it.writeByte(6)
            it.writeByte(0)
            it.writeByte(0)
            it.writeByte(0)
        }
        writeChunk(png, "IHDR", ihdr.toByteArray())
        var pos = 0
        while (pos < idat.size) {
            val end = minOf(pos + idatChunkSize, idat.size)
            writeChunk(png, "IDAT", idat.copyOfRange(pos, end))
            pos = end
        }
        writeChunk(png, "IEND", ByteArray(0))
        return png.toByteArray()
    }

    private fun filterRgbaScanlines(rgba: ByteArray, width: Int, height: Int, filterType: Int): ByteArray {
        val bpp = 4
        val rowBytes = width * bpp
        val scanlines = ByteArray(height * (1 + rowBytes))
        for (y in 0 until height) {
            val rowOff = y * rowBytes
            val prevOff = (y - 1) * rowBytes
            val dst = y * (1 + rowBytes)
            scanlines[dst] = filterType.toByte()
            for (x in 0 until rowBytes) {
                val raw = rgba[rowOff + x].toInt() and 0xFF
                val a = if (x >= bpp) rgba[rowOff + x - bpp].toInt() and 0xFF else 0
                val b = if (y > 0) rgba[prevOff + x].toInt() and 0xFF else 0
                val c = if (y > 0 && x >= bpp) rgba[prevOff + x - bpp].toInt() and 0xFF else 0
                scanlines[dst + 1 + x] = ((raw - predictByte(filterType, a, b, c)) and 0xFF).toByte()
            }
        }
        return scanlines
    }

    private fun predictByte(filterType: Int, a: Int, b: Int, c: Int): Int = when (filterType) {
        0 -> 0
        1 -> a
        2 -> b
        3 -> (a + b) ushr 1
        4 -> paeth(a, b, c)
        else -> 0
    }

    private fun deflateZlib(input: ByteArray, level: Int): ByteArray {
        val deflater = Deflater(level)
        deflater.setInput(input)
        deflater.finish()
        val zlibOut = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        while (!deflater.finished()) zlibOut.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return zlibOut.toByteArray()
    }

    private fun paeth(a: Int, b: Int, c: Int): Int {
        val p = a + b - c
        val pa = abs(p - a)
        val pb = abs(p - b)
        val pc = abs(p - c)
        return if (pa <= pb && pa <= pc) {
            a
        } else if (pb <= pc) {
            b
        } else {
            c
        }
    }

    private fun writeChunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
        val crc = CRC32().apply {
            update(typeBytes)
            update(data)
        }
        val dos = DataOutputStream(out)
        dos.writeInt(data.size)
        dos.write(typeBytes)
        dos.write(data)
        dos.writeInt(crc.value.toInt())
    }
}
