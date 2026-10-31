/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.meshlod

/** A GLB container's JSON chunk and its BIN chunk, null when it has none. */
internal class GlbFile(val json: String, val bin: ByteArray?) {

    /** This container as GLB bytes, each chunk padded to four bytes as the format requires. */
    fun encode(): ByteArray {
        val jsonBytes = json.encodeToByteArray().padded(SPACE)
        val binBytes = bin?.padded(0)
        val length = HEADER + CHUNK_HEADER + jsonBytes.size + (binBytes?.let { CHUNK_HEADER + it.size } ?: 0)
        val out = ByteArray(length)
        var offset = 0
        fun int(value: Int) {
            for (i in 0 until Int.SIZE_BYTES) out[offset + i] = (value ushr (i * Byte.SIZE_BITS)).toByte()
            offset += Int.SIZE_BYTES
        }
        fun chunk(type: Int, bytes: ByteArray) {
            int(bytes.size)
            int(type)
            bytes.copyInto(out, offset)
            offset += bytes.size
        }
        int(MAGIC)
        int(VERSION)
        int(length)
        chunk(JSON_CHUNK, jsonBytes)
        binBytes?.let { chunk(BIN_CHUNK, it) }
        return out
    }

    companion object {
        private const val MAGIC = 0x46546C67
        private const val VERSION = 2
        private const val HEADER = 12
        private const val CHUNK_HEADER = 8
        private const val JSON_CHUNK = 0x4E4F534A
        private const val BIN_CHUNK = 0x004E4942
        private const val SPACE: Byte = 0x20

        fun isGlb(bytes: ByteArray): Boolean = bytes.size >= HEADER && bytes.intAt(0) == MAGIC

        /** Reads a GLB [bytes]; the importer has already validated it, so this only splits the chunks. */
        fun decode(bytes: ByteArray): GlbFile {
            require(isGlb(bytes)) { "not a GLB file" }
            var json: String? = null
            var bin: ByteArray? = null
            var offset = HEADER
            val end = minOf(bytes.intAt(Int.SIZE_BYTES * 2), bytes.size)
            while (offset + CHUNK_HEADER <= end) {
                val length = bytes.intAt(offset)
                val type = bytes.intAt(offset + Int.SIZE_BYTES)
                val start = offset + CHUNK_HEADER
                require(length >= 0 && start + length <= end) { "GLB chunk at byte $offset runs past the file" }
                when (type) {
                    JSON_CHUNK -> json = bytes.decodeToString(start, start + length)
                    BIN_CHUNK -> bin = bytes.copyOfRange(start, start + length)
                }
                offset = start + length
            }
            return GlbFile(requireNotNull(json) { "GLB file has no JSON chunk" }, bin)
        }

        private fun ByteArray.intAt(offset: Int): Int {
            var value = 0
            for (i in 0 until Int.SIZE_BYTES) value = value or ((this[offset + i].toInt() and BYTE_MASK) shl (i * Byte.SIZE_BITS))
            return value
        }

        private fun ByteArray.padded(fill: Byte): ByteArray {
            val padding = (ALIGNMENT - size % ALIGNMENT) % ALIGNMENT
            return if (padding == 0) this else copyOf(size + padding).also { it.fill(fill, size, size + padding) }
        }

        private const val BYTE_MASK = 0xFF
        private const val ALIGNMENT = 4
    }
}
