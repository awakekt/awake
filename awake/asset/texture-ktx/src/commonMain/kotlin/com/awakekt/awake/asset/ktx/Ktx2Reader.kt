/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.ktx

/**
 * Reads a KTX2 file (Khronos KTX File Format Specification 2.0) into a [Ktx2Texture].
 *
 * It reads the header, the level index, the data format descriptor's colour model and transfer
 * function, and the key/value data, and checks every offset and length against the bytes present.
 * It decodes no image data and undoes no supercompression: that is a transcoder's job.
 */
object Ktx2Reader {
    /** The 12 bytes every KTX2 file starts with: `«KTX 20»\r\n\x1A\n`. */
    private val IDENTIFIER = byteArrayOf(
        0xAB.toByte(), 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, 0xBB.toByte(), 0x0D, 0x0A, 0x1A, 0x0A,
    )

    /** Whether [bytes] start like a KTX2 file, to tell one from a PNG without reading it. */
    fun isKtx2(bytes: ByteArray): Boolean =
        bytes.size >= IDENTIFIER.size && IDENTIFIER.indices.all { bytes[it] == IDENTIFIER[it] }

    /**
     * Reads [bytes] as a KTX2 file.
     *
     * @throws Ktx2FormatException when the bytes aren't a well-formed KTX2 file.
     */
    fun read(bytes: ByteArray): Ktx2Texture {
        ensure(bytes.size >= HEADER_BYTES && isKtx2(bytes), "identifier") { "not a KTX2 file" }
        val input = LittleEndian(bytes)
        val width = input.int(PIXEL_WIDTH, "header.pixelWidth")
        val faceCount = input.int(FACE_COUNT, "header.faceCount")
        val declaredLevels = input.int(LEVEL_COUNT, "header.levelCount")
        ensure(width > 0, "header.pixelWidth") { "must be at least 1" }
        ensure(faceCount == 1 || faceCount == CUBE_FACES, "header.faceCount") { "must be 1 or 6, was $faceCount" }
        ensure(declaredLevels <= MAX_LEVELS, "header.levelCount") { "must be 0 to $MAX_LEVELS: $declaredLevels" }
        val scheme = input.int(SUPERCOMPRESSION, "header.supercompressionScheme")
        val supercompression = Ktx2Supercompression.of(scheme)
            ?: throw Ktx2FormatException("header.supercompressionScheme", "unknown scheme $scheme")
        val vkFormat = input.int(VK_FORMAT, "header.vkFormat")
        return Ktx2Texture(
            vkFormat = vkFormat,
            width = width,
            // A 1D texture declares a height of 0; it's read as one row.
            height = input.int(PIXEL_HEIGHT, "header.pixelHeight").coerceAtLeast(1),
            depth = input.int(PIXEL_DEPTH, "header.pixelDepth"),
            layerCount = input.int(LAYER_COUNT, "header.layerCount"),
            faceCount = faceCount,
            encoding = readEncoding(input, vkFormat, supercompression),
            supercompression = supercompression,
            srgb = input.dfdByte(DFD_TRANSFER) == KHR_DF_TRANSFER_SRGB,
            // 0 levels means one, the base, with the mips left for the loader to generate.
            levels = List(declaredLevels.coerceAtLeast(1)) { readLevel(input, it) },
            keyValues = readKeyValues(input),
            supercompressionGlobalData = input.slice("sgd", input.u64(SGD_OFFSET), input.u64(SGD_LENGTH)),
        )
    }

    private fun readEncoding(input: LittleEndian, vkFormat: Int, supercompression: Ktx2Supercompression): Ktx2Encoding {
        val encoding = when (input.dfdByte(DFD_COLOR_MODEL)) {
            KHR_DF_MODEL_ETC1S -> Ktx2Encoding.Etc1s
            KHR_DF_MODEL_UASTC -> Ktx2Encoding.Uastc
            else -> Ktx2Encoding.Raw
        }
        val basisLz = supercompression == Ktx2Supercompression.BasisLz
        ensure(encoding != Ktx2Encoding.Etc1s || basisLz, "dfd") { "ETC1S must be BasisLZ-supercompressed, was $supercompression" }
        ensure(encoding == Ktx2Encoding.Etc1s || !basisLz, "header.supercompressionScheme") { "BasisLZ is for ETC1S only" }
        ensure(encoding != Ktx2Encoding.Raw || vkFormat != 0, "header.vkFormat") {
            "a texture that isn't Basis Universal needs a format"
        }
        return encoding
    }

    private fun readLevel(input: LittleEndian, level: Int): Ktx2Level {
        val entry = LEVEL_INDEX + level * LEVEL_ENTRY_BYTES
        val data = input.slice("levels[$level]", input.u64(entry), input.u64(entry + U64))
        return Ktx2Level(data, input.u64(entry + 2 * U64))
    }

    private fun readKeyValues(input: LittleEndian): Map<String, ByteArray> {
        val start = input.u32(KVD_OFFSET.toLong())
        val end = start + input.u32(KVD_LENGTH.toLong())
        input.within(start, end - start, "kvd")
        val values = LinkedHashMap<String, ByteArray>()
        var at = start
        while (at + U32 <= end) {
            val length = input.u32(at)
            val entry = at + U32
            ensure(length in 1..(end - entry), "kvd") { "an entry runs past the key/value data" }
            val bytes = input.slice("kvd", entry, length)
            val nul = bytes.indexOf(0)
            ensure(nul > 0, "kvd") { "a key isn't NUL-terminated" }
            values[bytes.decodeToString(0, nul)] = bytes.copyOfRange(nul + 1, bytes.size)
            // Each entry is padded to a multiple of four bytes.
            at = entry + (length + PADDING - 1) / PADDING * PADDING
        }
        return values
    }

    /**
     * Little-endian reads over the whole file. Every field is read unsigned, as the format stores it,
     * and every offset and length is checked against the file's size without overflowing.
     */
    private class LittleEndian(val bytes: ByteArray) {
        /** Fails at [location] unless [length] bytes from [offset] lie inside the file. */
        fun within(offset: Long, length: Long, location: String) =
            ensure(offset in 0..bytes.size && length in 0..(bytes.size - offset), location) {
                "offset $offset and length $length run past the end of the file (${bytes.size} bytes)"
            }

        /** The unsigned 32-bit field at [at]. */
        fun u32(at: Long): Long {
            within(at, U32.toLong(), "offset $at")
            val i = at.toInt()
            return (
                (bytes[i].toLong() and BYTE) or ((bytes[i + 1].toLong() and BYTE) shl 8) or
                    ((bytes[i + 2].toLong() and BYTE) shl 16) or ((bytes[i + 3].toLong() and BYTE) shl 24)
                )
        }

        /** The unsigned 32-bit header field at [at], which must fit an `Int`. */
        fun int(at: Int, location: String): Int {
            val value = u32(at.toLong())
            ensure(value <= Int.MAX_VALUE, location) { "$value is out of range" }
            return value.toInt()
        }

        /** The unsigned 64-bit field at [at]; one past `Long.MAX_VALUE` reads negative and fails [within]. */
        fun u64(at: Int): Long = u32(at.toLong()) or (u32(at.toLong() + U32) shl 32)

        fun slice(location: String, offset: Long, length: Long): ByteArray {
            within(offset, length, location)
            return bytes.copyOfRange(offset.toInt(), (offset + length).toInt())
        }

        /** The byte [field] bytes into the data format descriptor. */
        fun dfdByte(field: Int): Int {
            val dfd = u32(DFD_OFFSET.toLong())
            ensure(u32(DFD_LENGTH.toLong()) >= DFD_BASIC_MIN, "dfd") { "too short for a basic descriptor block" }
            within(dfd + field, 1, "dfd")
            return bytes[(dfd + field).toInt()].toInt() and BYTE.toInt()
        }
    }

    private inline fun ensure(condition: Boolean, location: String, problem: () -> String) {
        if (!condition) throw Ktx2FormatException(location, problem())
    }

    // Header field offsets, from the specification's file structure.
    private const val VK_FORMAT = 12
    private const val PIXEL_WIDTH = 20
    private const val PIXEL_HEIGHT = 24
    private const val PIXEL_DEPTH = 28
    private const val LAYER_COUNT = 32
    private const val FACE_COUNT = 36
    private const val LEVEL_COUNT = 40
    private const val SUPERCOMPRESSION = 44
    private const val DFD_OFFSET = 48
    private const val DFD_LENGTH = 52
    private const val KVD_OFFSET = 56
    private const val KVD_LENGTH = 60
    private const val SGD_OFFSET = 64
    private const val SGD_LENGTH = 72
    private const val LEVEL_INDEX = 80
    private const val HEADER_BYTES = 80
    private const val LEVEL_ENTRY_BYTES = 24

    // The data format descriptor: a total size, then the basic block's header, then its colour fields.
    private const val DFD_COLOR_MODEL = 12
    private const val DFD_TRANSFER = 14
    private const val DFD_BASIC_MIN = 28
    private const val KHR_DF_MODEL_ETC1S = 163
    private const val KHR_DF_MODEL_UASTC = 166
    private const val KHR_DF_TRANSFER_SRGB = 2

    private const val CUBE_FACES = 6
    private const val MAX_LEVELS = 32
    private const val U32 = 4
    private const val U64 = 8
    private const val PADDING = 4
    private const val BYTE = 0xFFL
}
