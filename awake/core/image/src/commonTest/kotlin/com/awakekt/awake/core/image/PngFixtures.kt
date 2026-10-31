/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.image

/**
 * Generated PNG fixtures for the decode goldens, encoded here so every target decodes the same
 * file bytes and knows the exact pixels in them: no binary fixture to keep in sync.
 *
 * Images are RGBA8, non-interlaced, in stored (uncompressed) deflate blocks, which every decoder reads.
 */
internal object PngFixtures {
    /** One fixture: its name, size, and the straight-alpha `0xAARRGGBB` colour at each top-down pixel. */
    class Image(val name: String, val width: Int, val height: Int, val opaque: Boolean, val colourAt: (x: Int, y: Int) -> Int) {
        val png: ByteArray by lazy { encode(this) }

        /** What `createBitmap` returns: [colourAt] with the bottom row first. */
        fun expectedBottomUpArgb(): IntArray = IntArray(width * height) { colourAt(it % width, height - 1 - it / width) }
    }

    val opaque256 = Image("opaque 256x256", 256, 256, opaque = true, colourAt = ::opaqueColour)
    val alpha256 = Image("alpha 256x256", 256, 256, opaque = false, colourAt = ::alphaColour)
    val oddWidth = Image("opaque 257x129", 257, 129, opaque = true, colourAt = ::opaqueColour)
    val alphaOddWidth = Image("alpha 255x3", 255, 3, opaque = false, colourAt = ::alphaColour)
    val opaque1024 = Image("opaque 1024x1024", 1024, 1024, opaque = true, colourAt = ::opaqueColour)
    val alpha1024 = Image("alpha 1024x1024", 1024, 1024, opaque = false, colourAt = ::alphaColour)

    val all = listOf(opaque256, alpha256, oddWidth, alphaOddWidth, opaque1024, alpha1024)

    // Every channel differs from its neighbours and from the other channels, so a swapped channel,
    // a mirrored row or column, or a missing flip changes the value.
    private fun opaqueColour(x: Int, y: Int): Int = argb(0xFF, x * 7 + y, y * 3 + (x shr 2), (x xor y) * 5 + 11)

    // Alpha sweeps 0..255 along x, so straight-alpha handling is tested at every alpha value.
    private fun alphaColour(x: Int, y: Int): Int = argb(x * 5 + y, y * 11 + x, x * 3 + 40, (x + y) * 13)

    private fun argb(a: Int, r: Int, g: Int, b: Int): Int = ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

    private fun encode(image: Image): ByteArray {
        val rowBytes = 1 + image.width * 4
        val raw = ByteArray(rowBytes * image.height)
        for (y in 0 until image.height) {
            var at = y * rowBytes + 1 // Filter byte 0: none.
            for (x in 0 until image.width) {
                val c = image.colourAt(x, y)
                raw[at++] = (c shr 16).toByte()
                raw[at++] = (c shr 8).toByte()
                raw[at++] = c.toByte()
                raw[at++] = (c ushr 24).toByte()
            }
        }
        val out = ByteSink()
        out.bytes(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
        val header = ByteSink().apply { int(image.width); int(image.height); bytes(byteArrayOf(8, 6, 0, 0, 0)) }
        out.chunk("IHDR", header.toByteArray())
        out.chunk("IDAT", zlibStored(raw))
        out.chunk("IEND", ByteArray(0))
        return out.toByteArray()
    }

    private fun zlibStored(data: ByteArray): ByteArray {
        val out = ByteSink()
        out.bytes(byteArrayOf(0x78, 0x01))
        var at = 0
        do {
            val length = minOf(MAX_STORED_BLOCK, data.size - at)
            val last = at + length == data.size
            out.bytes(byteArrayOf(if (last) 1 else 0, length.toByte(), (length shr 8).toByte(), length.inv().toByte(), (length.inv() shr 8).toByte()))
            out.bytes(data.copyOfRange(at, at + length))
            at += length
        } while (!last)
        var a = 1
        var b = 0
        for (byte in data) {
            a = (a + (byte.toInt() and 0xFF)) % ADLER_MOD
            b = (b + a) % ADLER_MOD
        }
        out.int((b shl 16) or a)
        return out.toByteArray()
    }

    private fun ByteSink.chunk(type: String, data: ByteArray) {
        val typed = type.encodeToByteArray() + data
        int(data.size)
        bytes(typed)
        int(crc32(typed))
    }

    private val crcTable = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) CRC_POLYNOMIAL xor (c ushr 1) else c ushr 1 }
        c
    }

    private fun crc32(data: ByteArray): Int {
        var c = -1
        for (byte in data) c = crcTable[(c xor byte.toInt()) and 0xFF] xor (c ushr 8)
        return c.inv()
    }

    private class ByteSink {
        private var buffer = ByteArray(INITIAL_CAPACITY)
        private var size = 0

        fun bytes(data: ByteArray) {
            if (size + data.size > buffer.size) buffer = buffer.copyOf(maxOf(buffer.size * 2, size + data.size))
            data.copyInto(buffer, size)
            size += data.size
        }

        fun int(value: Int) = bytes(byteArrayOf((value ushr 24).toByte(), (value ushr 16).toByte(), (value ushr 8).toByte(), value.toByte()))

        fun toByteArray(): ByteArray = buffer.copyOf(size)
    }

    private const val MAX_STORED_BLOCK = 65535
    private const val ADLER_MOD = 65521
    private const val CRC_POLYNOMIAL = 0xEDB88320.toInt()
    private const val INITIAL_CAPACITY = 1024
}
