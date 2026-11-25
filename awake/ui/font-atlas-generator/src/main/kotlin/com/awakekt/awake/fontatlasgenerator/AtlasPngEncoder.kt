/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.fontatlasgenerator

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.zip.CRC32
import java.util.zip.Deflater

private const val PNG_RGBA_CHANNELS = 4
private const val PNG_BIT_DEPTH = 8
private const val PNG_COLOR_TYPE_RGBA = 6
private const val PNG_FILTER_NONE: Byte = 0
private const val PNG_DEFLATE_CHUNK = 1 shl 16

private val PNG_SIGNATURE = byteArrayOf(
    0x89.toByte(),
    0x50,
    0x4E,
    0x47,
    0x0D,
    0x0A,
    0x1A,
    0x0A,
)

/**
 * Encodes tightly-packed RGBA8 [rgba] ([width] by [height]) as a deterministic lossless PNG (8-bit RGBA,
 * no ancillary chunks, `None` scanline filter, zlib level 9).
 *
 * Uses `None` scanline filtering because MTSDF atlases are mostly saturated flat `0x00`/`0xFF` bands with
 * abrupt channel transitions at edges, where `None` compresses smaller than `Sub`/`Up`/`Average`/`Paeth`
 * while also decoding via bulk row copies in `PngDecoder`. Uses [Deflater.BEST_COMPRESSION] directly so
 * the output is deterministic across JDK builds (matching [encodeEffectField]).
 */
internal fun encodeAtlasPng(rgba: ByteArray, width: Int, height: Int): ByteArray {
    require(width > 0 && height > 0) { "Atlas dimensions must be positive: ${width}x$height" }
    require(rgba.size == width * height * PNG_RGBA_CHANNELS) {
        "Expected ${width * height * PNG_RGBA_CHANNELS} RGBA bytes for ${width}x$height, got ${rgba.size}"
    }

    val rowBytes = width * PNG_RGBA_CHANNELS
    val scanlines = ByteArray(height * (1 + rowBytes))
    for (y in 0 until height) {
        val dst = y * (1 + rowBytes)
        scanlines[dst] = PNG_FILTER_NONE
        rgba.copyInto(scanlines, dst + 1, y * rowBytes, (y + 1) * rowBytes)
    }

    val compressed = deflateZlibBest(scanlines)

    val out = ByteArrayOutputStream(PNG_SIGNATURE.size + 57 + compressed.size)
    out.write(PNG_SIGNATURE)

    val ihdr = ByteArrayOutputStream(13)
    DataOutputStream(ihdr).use { data ->
        data.writeInt(width)
        data.writeInt(height)
        data.writeByte(PNG_BIT_DEPTH)
        data.writeByte(PNG_COLOR_TYPE_RGBA)
        data.writeByte(0) // compression method: deflate
        data.writeByte(0) // filter method: adaptive
        data.writeByte(0) // interlace method: none
    }
    writeChunk(out, "IHDR", ihdr.toByteArray())
    writeChunk(out, "IDAT", compressed)
    writeChunk(out, "IEND", ByteArray(0))
    return out.toByteArray()
}

/**
 * Encodes RGBA8 [rgba] ([width] by [height]) as a lossless PNG and formats it as standard base64.
 */
internal fun encodeAtlasPngBase64(rgba: ByteArray, width: Int, height: Int): String =
    Base64.getEncoder().encodeToString(encodeAtlasPng(rgba, width, height))

private fun deflateZlibBest(input: ByteArray): ByteArray {
    val deflater = Deflater(Deflater.BEST_COMPRESSION)
    deflater.setInput(input)
    deflater.finish()
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(PNG_DEFLATE_CHUNK)
    while (!deflater.finished()) {
        out.write(buffer, 0, deflater.deflate(buffer))
    }
    deflater.end()
    return out.toByteArray()
}

private fun writeChunk(out: ByteArrayOutputStream, type: String, payload: ByteArray) {
    val typeBytes = type.toByteArray(StandardCharsets.US_ASCII)
    val crc = CRC32().apply {
        update(typeBytes)
        update(payload)
    }
    val data = DataOutputStream(out)
    data.writeInt(payload.size)
    data.write(typeBytes)
    data.write(payload)
    data.writeInt(crc.value.toInt())
}
