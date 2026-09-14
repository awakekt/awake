/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("TooManyFunctions")

package com.awakekt.awake.core.io

/**
 * Bounds-checked little-endian reader for binary asset codecs.
 *
 * The reader never clamps malformed input: attempting to read beyond the buffer throws an
 * [IndexOutOfBoundsException], allowing each codec to turn malformed input into its own typed
 * failure or nullable result.
 */
class BinaryReader(
    private val bytes: ByteArray,
    startOffset: Int = 0,
) {
    init {
        require(startOffset in 0..bytes.size) { "startOffset must be within the byte buffer." }
    }

    var position: Int = startOffset
        private set

    val remaining: Int get() = bytes.size - position

    fun readByte(): Int {
        requireRemaining(1)
        return bytes[position++].toInt()
    }

    fun readUnsignedByte(): Int = readByte() and 0xFF

    fun readShortLe(): Int {
        requireRemaining(2)
        val value = (bytes[position].toInt() and 0xFF) or
            (bytes[position + 1].toInt() shl 8)
        position += 2
        return value
    }

    fun readUnsignedShortLe(): Int = readShortLe() and 0xFFFF

    fun readIntLe(): Int {
        requireRemaining(4)
        val value = (bytes[position].toInt() and 0xFF) or
            ((bytes[position + 1].toInt() and 0xFF) shl 8) or
            ((bytes[position + 2].toInt() and 0xFF) shl 16) or
            ((bytes[position + 3].toInt() and 0xFF) shl 24)
        position += 4
        return value
    }

    fun readUnsignedIntLe(): Long = readIntLe().toLong() and 0xFFFF_FFFFL

    fun readFloatLe(): Float = Float.fromBits(readIntLe())

    fun readBytes(length: Int): ByteArray {
        require(length >= 0) { "length must not be negative." }
        requireRemaining(length)
        return bytes.copyOfRange(position, position + length).also { position += length }
    }

    fun skip(length: Int) {
        require(length >= 0) { "length must not be negative." }
        requireRemaining(length)
        position += length
    }

    fun requireRemaining(length: Int) {
        require(length >= 0) { "length must not be negative." }
        if (length > remaining) {
            throw IndexOutOfBoundsException(
                "Need $length bytes at offset $position, but only $remaining remain.",
            )
        }
    }

    fun readShortLeAt(offset: Int): Int = BinaryReader(bytes, offset).readShortLe()

    fun readIntLeAt(offset: Int): Int = BinaryReader(bytes, offset).readIntLe()

    fun readUnsignedShortLeAt(offset: Int): Int = BinaryReader(bytes, offset).readUnsignedShortLe()

    fun readUnsignedIntLeAt(offset: Int): Long = BinaryReader(bytes, offset).readUnsignedIntLe()

    fun readFloatLeAt(offset: Int): Float = BinaryReader(bytes, offset).readFloatLe()
}
