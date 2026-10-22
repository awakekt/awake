/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.text.font

import kotlin.math.abs

/**
 * Decoded RGBA image pixels and dimensions from a PNG stream.
 */
internal data class DecodedPng(
    val width: Int,
    val height: Int,
    val rgba: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DecodedPng) return false
        return width == other.width && height == other.height && rgba.contentEquals(other.rgba)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + rgba.contentHashCode()
        return result
    }
}

/**
 * Pure Kotlin multiplatform PNG decoder (supporting RFC 2083 and RFC 1951 Deflate).
 */
internal object PngDecoder {
    private val SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    fun decode(bytes: ByteArray): DecodedPng {
        require(bytes.size >= 8 && (0 until 8).all { bytes[it] == SIGNATURE[it] }) {
            "Not a valid PNG image (signature mismatch)"
        }
        val parser = PngChunkParser(bytes)
        return parser.parse()
    }
}

private class PngChunkParser(private val bytes: ByteArray) {
    private var offset = 8
    private var width = 0
    private var height = 0
    private var bitDepth = 0
    private var colorType = 0
    private val idatChunks = ArrayList<ByteArray>()
    private var palette: ByteArray? = null
    private var transparency: ByteArray? = null

    fun parse(): DecodedPng {
        while (offset + 8 <= bytes.size) {
            val length = readU32(offset)
            val type = bytes.decodeToString(offset + 4, offset + 8)
            val dataOffset = offset + 8
            offset = dataOffset + length + 4
            when (type) {
                "IHDR" -> parseIhdr(dataOffset)
                "PLTE" -> palette = bytes.copyOfRange(dataOffset, dataOffset + length)
                "tRNS" -> transparency = bytes.copyOfRange(dataOffset, dataOffset + length)
                "IDAT" -> idatChunks += bytes.copyOfRange(dataOffset, dataOffset + length)
                "IEND" -> break
            }
        }
        val compressed = mergeIdat()
        val rawScanlines = DeflateInflater.inflateZlib(compressed)
        val rgba = unfilterScanlines(rawScanlines)
        return DecodedPng(width, height, rgba)
    }

    private fun parseIhdr(dataOffset: Int) {
        width = readU32(dataOffset)
        height = readU32(dataOffset + 4)
        bitDepth = bytes[dataOffset + 8].toInt() and 0xFF
        colorType = bytes[dataOffset + 9].toInt() and 0xFF
        require(bitDepth == 8) { "Unsupported PNG bit depth: $bitDepth" }
    }

    private fun readU32(at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)

    private fun mergeIdat(): ByteArray {
        val totalSize = idatChunks.sumOf { it.size }
        val merged = ByteArray(totalSize)
        var pos = 0
        for (chunk in idatChunks) {
            chunk.copyInto(merged, pos)
            pos += chunk.size
        }
        return merged
    }

    private fun unfilterScanlines(raw: ByteArray): ByteArray {
        val bpp = bytesPerPixel(colorType)
        val stride = 1 + width * bpp
        val unfilt = ByteArray(width * height * bpp)
        var prevOffset = -1
        for (y in 0 until height) {
            val filterType = raw[y * stride].toInt() and 0xFF
            val rowStart = y * stride + 1
            val outStart = y * width * bpp
            for (x in 0 until width * bpp) {
                val filt = raw[rowStart + x].toInt() and 0xFF
                val a = if (x >= bpp) unfilt[outStart + x - bpp].toInt() and 0xFF else 0
                val b = if (prevOffset >= 0) unfilt[prevOffset + x].toInt() and 0xFF else 0
                val c = if (prevOffset >= 0 && x >= bpp) unfilt[prevOffset + x - bpp].toInt() and 0xFF else 0
                val value = applyFilter(filterType, filt, a, b, c)
                unfilt[outStart + x] = value.toByte()
            }
            prevOffset = outStart
        }
        return convertToRgba(unfilt, bpp)
    }

    private fun bytesPerPixel(type: Int): Int = when (type) {
        0, 3 -> 1
        2 -> 3
        4 -> 2
        6 -> 4
        else -> 4
    }

    private fun applyFilter(type: Int, x: Int, a: Int, b: Int, c: Int): Int = when (type) {
        0 -> x
        1 -> (x + a) and 0xFF
        2 -> (x + b) and 0xFF
        3 -> (x + (a + b) / 2) and 0xFF
        4 -> (x + paeth(a, b, c)) and 0xFF
        else -> x
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

    private fun convertToRgba(unfilt: ByteArray, bpp: Int): ByteArray {
        val rgba = ByteArray(width * height * 4)
        var srcIdx = 0
        var dstIdx = 0
        val pal = palette
        val trns = transparency
        for (i in 0 until width * height) {
            when (colorType) {
                6 -> {
                    rgba[dstIdx] = unfilt[srcIdx]
                    rgba[dstIdx + 1] = unfilt[srcIdx + 1]
                    rgba[dstIdx + 2] = unfilt[srcIdx + 2]
                    rgba[dstIdx + 3] = unfilt[srcIdx + 3]
                }
                2 -> {
                    rgba[dstIdx] = unfilt[srcIdx]
                    rgba[dstIdx + 1] = unfilt[srcIdx + 1]
                    rgba[dstIdx + 2] = unfilt[srcIdx + 2]
                    rgba[dstIdx + 3] = 0xFF.toByte()
                }
                3 -> {
                    val entry = unfilt[srcIdx].toInt() and 0xFF
                    val palIdx = entry * 3
                    if (pal != null && palIdx + 2 < pal.size) {
                        rgba[dstIdx] = pal[palIdx]
                        rgba[dstIdx + 1] = pal[palIdx + 1]
                        rgba[dstIdx + 2] = pal[palIdx + 2]
                    }
                    val alpha = if (trns != null && entry < trns.size) trns[entry] else 0xFF.toByte()
                    rgba[dstIdx + 3] = alpha
                }
                else -> {
                    rgba[dstIdx] = unfilt[srcIdx]
                    rgba[dstIdx + 1] = unfilt[srcIdx]
                    rgba[dstIdx + 2] = unfilt[srcIdx]
                    rgba[dstIdx + 3] = if (colorType == 4) unfilt[srcIdx + 1] else 0xFF.toByte()
                }
            }
            srcIdx += bpp
            dstIdx += 4
        }
        return rgba
    }
}

/**
 * Pure Kotlin multiplatform RFC 1951 Deflate inflater with zlib header support.
 */
internal object DeflateInflater {
    fun inflateZlib(bytes: ByteArray): ByteArray {
        require(bytes.size >= 6) { "Zlib stream too short" }
        return inflateDeflate(bytes, offset = 2)
    }

    private fun inflateDeflate(bytes: ByteArray, offset: Int): ByteArray {
        val stream = BitStream(bytes, offset)
        val output = ArrayList<Byte>()
        var isFinal = false
        while (!isFinal) {
            isFinal = stream.readBits(1) != 0
            when (val btype = stream.readBits(2)) {
                0 -> inflateUncompressed(stream, output)
                1 -> inflateFixedHuffman(stream, output)
                2 -> inflateDynamicHuffman(stream, output)
                else -> throw IllegalArgumentException("Invalid deflate block type: $btype")
            }
        }
        return output.toByteArray()
    }

    private fun inflateUncompressed(stream: BitStream, output: ArrayList<Byte>) {
        stream.alignToByte()
        val len = stream.readU16()
        val nlen = stream.readU16()
        require((len xor 0xFFFF) and 0xFFFF == nlen) { "Corrupt uncompressed block length" }
        val buf = ByteArray(len)
        stream.readBytes(buf, 0, len)
        for (b in buf) output.add(b)
    }

    private fun inflateFixedHuffman(stream: BitStream, output: ArrayList<Byte>) {
        val litTree = FixedHuffmanTree.literalTree
        val distTree = FixedHuffmanTree.distanceTree
        decodeSymbols(stream, litTree, distTree, output)
    }

    private fun inflateDynamicHuffman(stream: BitStream, output: ArrayList<Byte>) {
        val hlit = stream.readBits(5) + 257
        val hdist = stream.readBits(5) + 1
        val hclen = stream.readBits(4) + 4
        val codeLengthOrder = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)
        val clCodeLengths = IntArray(19)
        for (i in 0 until hclen) {
            clCodeLengths[codeLengthOrder[i]] = stream.readBits(3)
        }
        val clTree = HuffmanTree.fromLengths(clCodeLengths)
        val allLengths = IntArray(hlit + hdist)
        var idx = 0
        while (idx < hlit + hdist) {
            when (val symbol = clTree.decodeSymbol(stream)) {
                in 0..15 -> allLengths[idx++] = symbol
                16 -> {
                    val repeat = stream.readBits(2) + 3
                    val prev = allLengths[idx - 1]
                    repeat(repeat) { allLengths[idx++] = prev }
                }
                17 -> {
                    val repeat = stream.readBits(3) + 3
                    repeat(repeat) { allLengths[idx++] = 0 }
                }
                18 -> {
                    val repeat = stream.readBits(7) + 11
                    repeat(repeat) { allLengths[idx++] = 0 }
                }
            }
        }
        val litTree = HuffmanTree.fromLengths(allLengths.copyOfRange(0, hlit))
        val distTree = HuffmanTree.fromLengths(allLengths.copyOfRange(hlit, hlit + hdist))
        decodeSymbols(stream, litTree, distTree, output)
    }

    private fun decodeSymbols(
        stream: BitStream,
        litTree: HuffmanTree,
        distTree: HuffmanTree,
        output: ArrayList<Byte>,
    ) {
        while (true) {
            val symbol = litTree.decodeSymbol(stream)
            if (symbol == 256) break
            if (symbol < 256) {
                output.add(symbol.toByte())
            } else {
                val lenIdx = symbol - 257
                val length = LENGTH_BASE[lenIdx] + stream.readBits(LENGTH_EXTRA[lenIdx])
                val distSymbol = distTree.decodeSymbol(stream)
                val distance = DIST_BASE[distSymbol] + stream.readBits(DIST_EXTRA[distSymbol])
                val start = output.size - distance
                for (i in 0 until length) {
                    output.add(output[start + i])
                }
            }
        }
    }

    private val LENGTH_BASE = intArrayOf(
        3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 15, 17, 19, 23, 27, 31, 35, 43, 51, 59, 67, 83, 99, 115, 131, 163, 195, 227, 258,
    )
    private val LENGTH_EXTRA = intArrayOf(
        0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 0,
    )
    private val DIST_BASE = intArrayOf(
        1, 2, 3, 4, 5, 7, 9, 13, 17, 25, 33, 49, 65, 97, 129, 193, 257, 385, 513, 769, 1025, 1537, 2049, 3073, 4097, 6145, 8193, 12289, 16385, 24577,
    )
    private val DIST_EXTRA = intArrayOf(
        0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, 8, 8, 9, 9, 10, 10, 11, 11, 12, 12, 13, 13,
    )
}

private class BitStream(private val bytes: ByteArray, var offset: Int = 0) {
    private var bitBuffer = 0
    private var bitCount = 0

    fun readBits(count: Int): Int {
        while (bitCount < count) {
            require(offset < bytes.size) { "Unexpected end of bit stream" }
            bitBuffer = bitBuffer or ((bytes[offset++].toInt() and 0xFF) shl bitCount)
            bitCount += 8
        }
        val value = bitBuffer and ((1 shl count) - 1)
        bitBuffer = bitBuffer ushr count
        bitCount -= count
        return value
    }

    fun alignToByte() {
        bitBuffer = 0
        bitCount = 0
    }

    fun readU16(): Int {
        alignToByte()
        val low = bytes[offset++].toInt() and 0xFF
        val high = bytes[offset++].toInt() and 0xFF
        return (high shl 8) or low
    }

    fun readBytes(dest: ByteArray, destOffset: Int, count: Int) {
        alignToByte()
        bytes.copyInto(dest, destOffset, offset, offset + count)
        offset += count
    }
}

private class HuffmanTree(private val left: IntArray, private val right: IntArray, private val value: IntArray) {
    fun decodeSymbol(stream: BitStream): Int {
        var node = 0
        while (value[node] < 0) {
            val bit = stream.readBits(1)
            node = if (bit == 0) left[node] else right[node]
            require(node >= 0) { "Corrupt Huffman code in stream" }
        }
        return value[node]
    }

    companion object {
        @Suppress("NestedBlockDepth")
        fun fromLengths(lengths: IntArray): HuffmanTree {
            val maxLen = lengths.maxOrNull() ?: 0
            val blCount = IntArray(maxLen + 1)
            for (len in lengths) if (len > 0) blCount[len]++
            var code = 0
            val nextCode = IntArray(maxLen + 1)
            for (bits in 1..maxLen) {
                code = (code + blCount[bits - 1]) shl 1
                nextCode[bits] = code
            }

            val maxNodes = lengths.size * 4 + 2
            val left = IntArray(maxNodes) { -1 }
            val right = IntArray(maxNodes) { -1 }
            val value = IntArray(maxNodes) { -1 }
            var nextNode = 1

            for (symbol in lengths.indices) {
                val len = lengths[symbol]
                if (len <= 0) continue
                val c = nextCode[len]++
                var node = 0
                for (bitIdx in len - 1 downTo 0) {
                    val bit = (c ushr bitIdx) and 1
                    if (bit == 0) {
                        if (left[node] < 0) left[node] = nextNode++
                        node = left[node]
                    } else {
                        if (right[node] < 0) right[node] = nextNode++
                        node = right[node]
                    }
                }
                value[node] = symbol
            }
            return HuffmanTree(left, right, value)
        }
    }
}

private object FixedHuffmanTree {
    val literalTree: HuffmanTree by lazy {
        val lengths = IntArray(288)
        for (i in 0..143) lengths[i] = 8
        for (i in 144..255) lengths[i] = 9
        for (i in 256..279) lengths[i] = 7
        for (i in 280..287) lengths[i] = 8
        HuffmanTree.fromLengths(lengths)
    }

    val distanceTree: HuffmanTree by lazy {
        HuffmanTree.fromLengths(IntArray(32) { 5 })
    }
}
