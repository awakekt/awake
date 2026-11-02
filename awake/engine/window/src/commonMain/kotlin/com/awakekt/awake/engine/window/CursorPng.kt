/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.CursorImage
import kotlin.io.encoding.Base64

/*
 * A browser cursor is a CSS `url(...)`, and the one source that needs no request is a `data:` URL
 * holding an encoded image, so a CursorImage's raw pixels are PNG-encoded here. The encoder is in
 * common code, not the wasmJs source set, so the bytes and the CSS string are tested on the JVM.
 *
 * The deflate stream uses stored (uncompressed) blocks: every decoder reads them, nothing here pulls
 * in a compressor, and a cursor is at most 128x128 pixels (a browser ignores a larger one), so the
 * data URL is a one-off of tens of kilobytes at most.
 */

private const val PNG_BIT_DEPTH = 8
private const val PNG_COLOR_TYPE_RGBA = 6
private const val STORED_BLOCK_MAX = 0xFFFF
private const val ADLER_MOD = 65_521
private const val CRC_POLY = 0xEDB88320.toInt()
private const val BYTE_MASK = 0xFF

private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A)

private val crcTable: IntArray by lazy {
    IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) CRC_POLY xor (c ushr 1) else c ushr 1 }
        c
    }
}

/** This image as an RGBA8, non-interlaced PNG file, top row first. */
internal fun CursorImage.toPng(): ByteArray {
    val rowBytes = 1 + width * CursorImage.PIXEL_STRIDE
    val raw = ByteArray(rowBytes * height) // Each row opens with filter byte 0: none.
    for (y in 0 until height) {
        val from = y * width * CursorImage.PIXEL_STRIDE
        rgba.copyInto(raw, destinationOffset = y * rowBytes + 1, startIndex = from, endIndex = from + width * CursorImage.PIXEL_STRIDE)
    }
    val header = ByteArray(IHDR_SIZE)
    header.putInt(0, width)
    header.putInt(4, height)
    header[8] = PNG_BIT_DEPTH.toByte()
    header[9] = PNG_COLOR_TYPE_RGBA.toByte()
    // Bytes 10..12, compression, filter and interlace method, stay 0.
    val idat = zlibStored(raw)
    val out = ByteArray(PNG_SIGNATURE.size + chunkSize(header.size) + chunkSize(idat.size) + chunkSize(0))
    PNG_SIGNATURE.copyInto(out)
    var at = PNG_SIGNATURE.size
    at = putChunk(out, at, "IHDR", header)
    at = putChunk(out, at, "IDAT", idat)
    putChunk(out, at, "IEND", ByteArray(0))
    return out
}

private const val IHDR_SIZE = 13
private const val CHUNK_OVERHEAD = 12 // Length, type and CRC around the data.

private fun chunkSize(dataSize: Int): Int = CHUNK_OVERHEAD + dataSize

private fun putChunk(out: ByteArray, at: Int, type: String, data: ByteArray): Int {
    out.putInt(at, data.size)
    for (i in 0 until 4) out[at + 4 + i] = type[i].code.toByte()
    data.copyInto(out, destinationOffset = at + 8)
    out.putInt(at + 8 + data.size, crc32(out, at + 4, 4 + data.size))
    return at + chunkSize(data.size)
}

private fun zlibStored(raw: ByteArray): ByteArray {
    val blocks = (raw.size + STORED_BLOCK_MAX - 1) / STORED_BLOCK_MAX
    val out = ByteArray(2 + raw.size + blocks * 5 + 4)
    out[0] = 0x78 // Deflate, 32K window.
    out[1] = 0x01 // No preset dictionary, fastest; makes the header a multiple of 31.
    var at = 2
    var done = 0
    while (done < raw.size) {
        val n = minOf(STORED_BLOCK_MAX, raw.size - done)
        out[at++] = if (done + n == raw.size) 1 else 0 // BFINAL, BTYPE 00 (stored).
        out[at++] = n.toByte()
        out[at++] = (n shr 8).toByte()
        out[at++] = n.inv().toByte()
        out[at++] = (n.inv() shr 8).toByte()
        raw.copyInto(out, destinationOffset = at, startIndex = done, endIndex = done + n)
        at += n
        done += n
    }
    out.putInt(at, adler32(raw))
    return out
}

private fun adler32(bytes: ByteArray): Int {
    var a = 1
    var b = 0
    for (byte in bytes) {
        a = (a + (byte.toInt() and BYTE_MASK)) % ADLER_MOD
        b = (b + a) % ADLER_MOD
    }
    return (b shl 16) or a
}

private fun crc32(bytes: ByteArray, from: Int, length: Int): Int {
    var c = -1
    for (i in from until from + length) c = crcTable[(c xor bytes[i].toInt()) and BYTE_MASK] xor (c ushr 8)
    return c.inv()
}

private fun ByteArray.putInt(at: Int, value: Int) {
    this[at] = (value ushr 24).toByte()
    this[at + 1] = (value ushr 16).toByte()
    this[at + 2] = (value ushr 8).toByte()
    this[at + 3] = value.toByte()
}

/**
 * This image as the value of a CSS `cursor` property: `url(data:image/png;base64,...) x y, auto`,
 * with the hotspot as the two offsets and `auto` as the fallback a browser needs if it cannot
 * decode the image.
 *
 * Encodes on every call; [CanvasCursor] calls it once per image and keeps the string.
 */
internal fun CursorImage.toCssCursor(): String =
    "url(data:image/png;base64,${Base64.Default.encode(toPng())}) $hotspotX $hotspotY, auto"
