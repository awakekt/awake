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

    fun isPng(bytes: ByteArray): Boolean {
        if (bytes.size < SIGNATURE.size) return false
        var i = 0
        while (i < SIGNATURE.size && bytes[i] == SIGNATURE[i]) i++
        return i == SIGNATURE.size
    }

    fun decode(bytes: ByteArray): DecodedPng {
        require(isPng(bytes)) { "Not a valid PNG image (signature mismatch)" }
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
    private var singleIdatOffset = -1
    private var singleIdatLength = 0
    private var idatChunks: ArrayList<ByteArray>? = null
    private var palette: ByteArray? = null
    private var transparency: ByteArray? = null

    fun parse(): DecodedPng {
        while (offset + 8 <= bytes.size) {
            val length = readU32(offset)
            val dataOffset = offset + 8
            val typeTag = readU32(offset + 4)
            offset = dataOffset + length + 4
            when (typeTag) {
                CHUNK_IHDR -> parseIhdr(dataOffset)
                CHUNK_PLTE -> palette = bytes.copyOfRange(dataOffset, dataOffset + length)
                CHUNK_TRNS -> transparency = bytes.copyOfRange(dataOffset, dataOffset + length)
                CHUNK_IDAT -> recordIdat(dataOffset, length)
                CHUNK_IEND -> break
            }
        }
        require(width > 0 && height > 0) { "PNG missing valid IHDR chunk" }
        val bpp = bytesPerPixel(colorType)
        val expectedScanlines = height * (1 + width * bpp)
        val rawScanlines = inflateIdat(expectedScanlines)
        val rgba = unfilterScanlines(rawScanlines, bpp)
        return DecodedPng(width, height, rgba)
    }

    private fun recordIdat(dataOffset: Int, length: Int) {
        val multi = idatChunks
        if (multi != null) {
            multi += bytes.copyOfRange(dataOffset, dataOffset + length)
        } else if (singleIdatOffset < 0) {
            singleIdatOffset = dataOffset
            singleIdatLength = length
        } else {
            val list = ArrayList<ByteArray>(4)
            list += bytes.copyOfRange(singleIdatOffset, singleIdatOffset + singleIdatLength)
            list += bytes.copyOfRange(dataOffset, dataOffset + length)
            idatChunks = list
        }
    }

    private fun inflateIdat(expectedScanlines: Int): ByteArray {
        val multi = idatChunks
        if (multi == null) {
            require(singleIdatOffset >= 0) { "PNG missing IDAT chunk" }
            return DeflateInflater.inflateZlib(bytes, singleIdatOffset, singleIdatLength, expectedScanlines)
        }
        val totalSize = multi.sumOf { it.size }
        val merged = ByteArray(totalSize)
        var pos = 0
        for (i in multi.indices) {
            val chunk = multi[i]
            chunk.copyInto(merged, pos)
            pos += chunk.size
        }
        return DeflateInflater.inflateZlib(merged, 0, merged.size, expectedScanlines)
    }

    private fun parseIhdr(dataOffset: Int) {
        width = readU32(dataOffset)
        height = readU32(dataOffset + 4)
        bitDepth = bytes[dataOffset + 8].toInt() and 0xFF
        colorType = bytes[dataOffset + 9].toInt() and 0xFF
        val interlace = bytes[dataOffset + 12].toInt() and 0xFF
        require(bitDepth == 8) { "Unsupported PNG bit depth: $bitDepth" }
        require(interlace == 0) { "Unsupported PNG interlace method: $interlace" }
    }

    private fun readU32(at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)

    private fun unfilterScanlines(raw: ByteArray, bpp: Int): ByteArray {
        val rowBytes = width * bpp
        val stride = 1 + rowBytes
        require(raw.size >= height * stride) {
            "Inflated PNG scanlines truncated: ${raw.size} < ${height * stride}"
        }
        val unfilt = ByteArray(width * height * bpp)
        var prevOffset = -1
        for (y in 0 until height) {
            val filterType = raw[y * stride].toInt() and 0xFF
            val rowStart = y * stride + 1
            val outStart = y * rowBytes
            unfilterPngRow(filterType, raw, rowStart, unfilt, outStart, prevOffset, rowBytes, bpp)
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

    private fun convertToRgba(unfilt: ByteArray, bpp: Int): ByteArray {
        if (colorType == 6) return unfilt
        val rgba = ByteArray(width * height * 4)
        var srcIdx = 0
        var dstIdx = 0
        val pal = palette
        val trns = transparency
        for (i in 0 until width * height) {
            when (colorType) {
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

    private companion object {
        const val CHUNK_IHDR = 0x49484452
        const val CHUNK_PLTE = 0x504C5445
        const val CHUNK_TRNS = 0x74524E53
        const val CHUNK_IDAT = 0x49444154
        const val CHUNK_IEND = 0x49454E44
    }
}

@Suppress("LongParameterList")
private fun unfilterPngRow(
    filterType: Int,
    raw: ByteArray,
    rowStart: Int,
    out: ByteArray,
    outStart: Int,
    prevOffset: Int,
    rowBytes: Int,
    bpp: Int,
) {
    when (filterType) {
        0 -> raw.copyInto(out, outStart, rowStart, rowStart + rowBytes)
        1 -> unfilterSubRow(raw, rowStart, out, outStart, rowBytes, bpp)
        2 -> unfilterUpRow(raw, rowStart, out, outStart, prevOffset, rowBytes)
        3 -> unfilterAverageRow(raw, rowStart, out, outStart, prevOffset, rowBytes, bpp)
        4 -> unfilterPaethRow(raw, rowStart, out, outStart, prevOffset, rowBytes, bpp)
        else -> throw IllegalArgumentException("Unsupported PNG filter type: $filterType")
    }
}

@Suppress("LongParameterList")
private fun unfilterSubRow(raw: ByteArray, rowStart: Int, out: ByteArray, outStart: Int, rowBytes: Int, bpp: Int) {
    for (x in 0 until bpp) {
        out[outStart + x] = raw[rowStart + x]
    }
    for (x in bpp until rowBytes) {
        val filt = raw[rowStart + x].toInt() and 0xFF
        val a = out[outStart + x - bpp].toInt() and 0xFF
        out[outStart + x] = (filt + a).toByte()
    }
}

@Suppress("LongParameterList")
private fun unfilterUpRow(raw: ByteArray, rowStart: Int, out: ByteArray, outStart: Int, prevOffset: Int, rowBytes: Int) {
    if (prevOffset < 0) {
        raw.copyInto(out, outStart, rowStart, rowStart + rowBytes)
        return
    }
    for (x in 0 until rowBytes) {
        val filt = raw[rowStart + x].toInt() and 0xFF
        val b = out[prevOffset + x].toInt() and 0xFF
        out[outStart + x] = (filt + b).toByte()
    }
}

@Suppress("LongParameterList")
private fun unfilterAverageRow(
    raw: ByteArray,
    rowStart: Int,
    out: ByteArray,
    outStart: Int,
    prevOffset: Int,
    rowBytes: Int,
    bpp: Int,
) {
    for (x in 0 until rowBytes) {
        val filt = raw[rowStart + x].toInt() and 0xFF
        val a = if (x >= bpp) out[outStart + x - bpp].toInt() and 0xFF else 0
        val b = if (prevOffset >= 0) out[prevOffset + x].toInt() and 0xFF else 0
        out[outStart + x] = (filt + ((a + b) ushr 1)).toByte()
    }
}

@Suppress("LongParameterList")
private fun unfilterPaethRow(
    raw: ByteArray,
    rowStart: Int,
    out: ByteArray,
    outStart: Int,
    prevOffset: Int,
    rowBytes: Int,
    bpp: Int,
) {
    for (x in 0 until rowBytes) {
        val filt = raw[rowStart + x].toInt() and 0xFF
        val a = if (x >= bpp) out[outStart + x - bpp].toInt() and 0xFF else 0
        val b = if (prevOffset >= 0) out[prevOffset + x].toInt() and 0xFF else 0
        val c = if (prevOffset >= 0 && x >= bpp) out[prevOffset + x - bpp].toInt() and 0xFF else 0
        out[outStart + x] = (filt + paethPredictor(a, b, c)).toByte()
    }
}

private fun paethPredictor(a: Int, b: Int, c: Int): Int {
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

/**
 * Pure Kotlin multiplatform RFC 1951 Deflate inflater with zlib header support.
 */
internal object DeflateInflater {
    fun inflateZlib(
        bytes: ByteArray,
        offset: Int = 0,
        length: Int = bytes.size - offset,
        expectedSize: Int = 0,
    ): ByteArray {
        require(length >= 6 && offset >= 0 && offset + length <= bytes.size) { "Zlib stream too short" }
        val cmf = bytes[offset].toInt() and 0xFF
        val flg = bytes[offset + 1].toInt() and 0xFF
        require((cmf and 0x0F) == 8 && ((cmf shl 8) or flg) % 31 == 0) { "Invalid zlib header" }
        require((flg and 0x20) == 0) { "Zlib preset dictionary is not supported" }
        return inflateDeflate(bytes, offset + 2, offset + length, expectedSize)
    }

    private fun inflateDeflate(bytes: ByteArray, offset: Int, endOffset: Int, expectedSize: Int): ByteArray {
        val stream = BitStream(bytes, offset, endOffset)
        val output = ByteSink(if (expectedSize > 0) expectedSize else DEFAULT_SINK_CAPACITY)
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

    private fun inflateUncompressed(stream: BitStream, output: ByteSink) {
        stream.alignToByte()
        val len = stream.readU16()
        val nlen = stream.readU16()
        require((len xor 0xFFFF) and 0xFFFF == nlen) { "Corrupt uncompressed block length" }
        output.writeFromStream(stream, len)
    }

    private fun inflateFixedHuffman(stream: BitStream, output: ByteSink) {
        decodeSymbols(stream, FixedHuffmanTree.literalTree, FixedHuffmanTree.distanceTree, output)
    }

    private fun inflateDynamicHuffman(stream: BitStream, output: ByteSink) {
        val hlit = stream.readBits(5) + 257
        val hdist = stream.readBits(5) + 1
        val hclen = stream.readBits(4) + 4
        val clCodeLengths = IntArray(19)
        for (i in 0 until hclen) {
            clCodeLengths[CODE_LENGTH_ORDER[i]] = stream.readBits(3)
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
        output: ByteSink,
    ) {
        while (true) {
            val symbol = litTree.decodeSymbol(stream)
            if (symbol == 256) break
            if (symbol < 256) {
                output.writeByte(symbol.toByte())
            } else {
                val lenIdx = symbol - 257
                val extraLenBits = LENGTH_EXTRA[lenIdx]
                val length = LENGTH_BASE[lenIdx] + if (extraLenBits > 0) stream.readBits(extraLenBits) else 0
                val distSymbol = distTree.decodeSymbol(stream)
                val extraDistBits = DIST_EXTRA[distSymbol]
                val distance = DIST_BASE[distSymbol] + if (extraDistBits > 0) stream.readBits(extraDistBits) else 0
                output.copyBackReference(distance, length)
            }
        }
    }

    private const val DEFAULT_SINK_CAPACITY = 4096

    private val CODE_LENGTH_ORDER = intArrayOf(16, 17, 18, 0, 8, 7, 9, 6, 10, 5, 11, 4, 12, 3, 13, 2, 14, 1, 15)

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

/**
 * Primitive growing byte buffer for deflate output, avoiding per-byte boxing.
 */
private class ByteSink(initialCapacity: Int) {
    private var buf = ByteArray(maxOf(initialCapacity, 64))
    var size = 0
        private set

    fun writeByte(value: Byte) {
        ensureCapacity(size + 1)
        buf[size++] = value
    }

    fun writeFromStream(stream: BitStream, count: Int) {
        ensureCapacity(size + count)
        stream.readBytes(buf, size, count)
        size += count
    }

    fun copyBackReference(distance: Int, length: Int) {
        val start = size - distance
        require(start >= 0) { "Invalid deflate back-reference distance: $distance at size $size" }
        ensureCapacity(size + length)
        if (distance >= length) {
            buf.copyInto(buf, size, start, start + length)
            size += length
        } else {
            var src = start
            var dst = size
            val end = size + length
            while (dst < end) {
                buf[dst++] = buf[src++]
            }
            size = end
        }
    }

    fun toByteArray(): ByteArray = if (size == buf.size) buf else buf.copyOf(size)

    private fun ensureCapacity(required: Int) {
        if (required <= buf.size) return
        var newSize = buf.size shl 1
        if (newSize < required) newSize = required
        buf = buf.copyOf(newSize)
    }
}

private class BitStream(
    private val bytes: ByteArray,
    var offset: Int = 0,
    private val endOffset: Int = bytes.size,
) {
    private var bitBuffer = 0
    private var bitCount = 0

    fun ensureBits(count: Int) {
        while (bitCount < count && offset < endOffset) {
            bitBuffer = bitBuffer or ((bytes[offset++].toInt() and 0xFF) shl bitCount)
            bitCount += 8
        }
    }

    fun peekBits(mask: Int): Int = bitBuffer and mask

    fun consumeBits(count: Int) {
        require(bitCount >= count) { "Unexpected end of bit stream" }
        bitBuffer = bitBuffer ushr count
        bitCount -= count
    }

    fun readBits(count: Int): Int {
        while (bitCount < count) {
            require(offset < endOffset) { "Unexpected end of bit stream" }
            bitBuffer = bitBuffer or ((bytes[offset++].toInt() and 0xFF) shl bitCount)
            bitCount += 8
        }
        val value = bitBuffer and ((1 shl count) - 1)
        bitBuffer = bitBuffer ushr count
        bitCount -= count
        return value
    }

    fun alignToByte() {
        val discardBits = bitCount and 7
        bitCount -= discardBits
        val wholeBytes = bitCount ushr 3
        offset -= wholeBytes
        bitBuffer = 0
        bitCount = 0
    }

    fun readU16(): Int {
        alignToByte()
        require(offset + 2 <= endOffset) { "Unexpected end of bit stream" }
        val low = bytes[offset++].toInt() and 0xFF
        val high = bytes[offset++].toInt() and 0xFF
        return (high shl 8) or low
    }

    fun readBytes(dest: ByteArray, destOffset: Int, count: Int) {
        alignToByte()
        require(offset + count <= endOffset) { "Unexpected end of bit stream" }
        bytes.copyInto(dest, destOffset, offset, offset + count)
        offset += count
    }
}

private class HuffmanTree(
    private val fastTable: IntArray,
    private val left: IntArray,
    private val right: IntArray,
    private val value: IntArray,
) {
    fun decodeSymbol(stream: BitStream): Int {
        stream.ensureBits(FAST_BITS)
        val entry = fastTable[stream.peekBits(FAST_MASK)]
        if (entry != 0) {
            stream.consumeBits(entry and FAST_LEN_MASK)
            return entry ushr FAST_LEN_SHIFT
        }
        var node = 0
        while (value[node] < 0) {
            val bit = stream.readBits(1)
            node = if (bit == 0) left[node] else right[node]
            require(node >= 0) { "Corrupt Huffman code in stream" }
        }
        return value[node]
    }

    companion object {
        private const val FAST_BITS = 9
        private const val FAST_SIZE = 1 shl FAST_BITS
        private const val FAST_MASK = FAST_SIZE - 1
        private const val FAST_LEN_SHIFT = 4
        private const val FAST_LEN_MASK = (1 shl FAST_LEN_SHIFT) - 1

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

            val fastTable = IntArray(FAST_SIZE)
            val maxNodes = lengths.size * 4 + 2
            val left = IntArray(maxNodes) { -1 }
            val right = IntArray(maxNodes) { -1 }
            val value = IntArray(maxNodes) { -1 }
            var nextNode = 1

            for (symbol in lengths.indices) {
                val len = lengths[symbol]
                if (len <= 0) continue
                val c = nextCode[len]++
                if (len <= FAST_BITS) {
                    val rev = reverseBits(c, len)
                    val packed = (symbol shl FAST_LEN_SHIFT) or len
                    val step = 1 shl len
                    var idx = rev
                    while (idx < FAST_SIZE) {
                        fastTable[idx] = packed
                        idx += step
                    }
                } else {
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
            }
            return HuffmanTree(fastTable, left, right, value)
        }

        private fun reverseBits(code: Int, length: Int): Int {
            var src = code
            var rev = 0
            for (i in 0 until length) {
                rev = (rev shl 1) or (src and 1)
                src = src ushr 1
            }
            return rev
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
