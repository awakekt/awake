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
 *
 * @param bytes The raw byte buffer to read from.
 * @param startOffset The initial byte position within [bytes], defaulting to 0.
 */
class BinaryReader(
    private val bytes: ByteArray,
    startOffset: Int = 0,
) {
    init {
        require(startOffset in 0..bytes.size) { "startOffset must be within the byte buffer." }
    }

    /** Current read position in bytes. */
    var position: Int = startOffset
        private set

    /** Number of bytes remaining to be read. */
    val remaining: Int get() = bytes.size - position

    /**
     * Reads a signed 8-bit byte at the current position.
     *
     * @return The signed byte value as an integer.
     */
    fun readByte(): Int {
        requireRemaining(1)
        return bytes[position++].toInt()
    }

    /**
     * Reads an unsigned 8-bit byte at the current position.
     *
     * @return The unsigned byte value in the range 0..255.
     */
    fun readUnsignedByte(): Int = readByte() and 0xFF

    /**
     * Reads a 16-bit signed integer in little-endian byte order.
     *
     * @return The 16-bit signed integer.
     */
    fun readShortLe(): Int {
        requireRemaining(2)
        val value = (bytes[position].toInt() and 0xFF) or
            (bytes[position + 1].toInt() shl 8)
        position += 2
        return value
    }

    /**
     * Reads a 16-bit unsigned integer in little-endian byte order.
     *
     * @return The unsigned 16-bit value in the range 0..65535.
     */
    fun readUnsignedShortLe(): Int = readShortLe() and 0xFFFF

    /**
     * Reads a 32-bit signed integer in little-endian byte order.
     *
     * @return The 32-bit signed integer.
     */
    fun readIntLe(): Int {
        requireRemaining(4)
        val value = (bytes[position].toInt() and 0xFF) or
            ((bytes[position + 1].toInt() and 0xFF) shl 8) or
            ((bytes[position + 2].toInt() and 0xFF) shl 16) or
            ((bytes[position + 3].toInt() and 0xFF) shl 24)
        position += 4
        return value
    }

    /**
     * Reads a 32-bit unsigned integer in little-endian byte order.
     *
     * @return The unsigned 32-bit value as a 64-bit Long.
     */
    fun readUnsignedIntLe(): Long = readIntLe().toLong() and 0xFFFF_FFFFL

    /**
     * Reads a 32-bit IEEE 754 floating-point number in little-endian byte order.
     *
     * @return The decoded 32-bit float value.
     */
    fun readFloatLe(): Float = Float.fromBits(readIntLe())

    /**
     * Reads a slice of [length] bytes advancing the current position.
     *
     * @param length Number of bytes to read.
     * @return A newly allocated byte array containing the read slice.
     */
    fun readBytes(length: Int): ByteArray {
        require(length >= 0) { "length must not be negative." }
        requireRemaining(length)
        return bytes.copyOfRange(position, position + length).also { position += length }
    }

    /**
     * Advances the current read position forward by [length] bytes.
     *
     * @param length Number of bytes to skip.
     */
    fun skip(length: Int) {
        require(length >= 0) { "length must not be negative." }
        requireRemaining(length)
        position += length
    }

    /**
     * Asserts that at least [length] bytes remain in the buffer.
     *
     * @param length The required number of remaining bytes.
     * @throws IndexOutOfBoundsException If fewer than [length] bytes remain.
     */
    fun requireRemaining(length: Int) {
        require(length >= 0) { "length must not be negative." }
        if (length > remaining) {
            throw IndexOutOfBoundsException(
                "Need $length bytes at offset $position, but only $remaining remain.",
            )
        }
    }

    /**
     * Reads a 16-bit signed integer in little-endian byte order at the specified [offset].
     *
     * @param offset Absolute byte offset from which to read.
     * @return The 16-bit signed integer.
     */
    fun readShortLeAt(offset: Int): Int = BinaryReader(bytes, offset).readShortLe()

    /**
     * Reads a 32-bit signed integer in little-endian byte order at the specified [offset].
     *
     * @param offset Absolute byte offset from which to read.
     * @return The 32-bit signed integer.
     */
    fun readIntLeAt(offset: Int): Int = BinaryReader(bytes, offset).readIntLe()

    /**
     * Reads a 16-bit unsigned integer in little-endian byte order at the specified [offset].
     *
     * @param offset Absolute byte offset from which to read.
     * @return The unsigned 16-bit integer.
     */
    fun readUnsignedShortLeAt(offset: Int): Int = BinaryReader(bytes, offset).readUnsignedShortLe()

    /**
     * Reads a 32-bit unsigned integer in little-endian byte order at the specified [offset].
     *
     * @param offset Absolute byte offset from which to read.
     * @return The unsigned 32-bit integer as a Long.
     */
    fun readUnsignedIntLeAt(offset: Int): Long = BinaryReader(bytes, offset).readUnsignedIntLe()

    /**
     * Reads a 32-bit IEEE 754 floating-point number in little-endian byte order at the specified [offset].
     *
     * @param offset Absolute byte offset from which to read.
     * @return The 32-bit float value.
     */
    fun readFloatLeAt(offset: Int): Float = BinaryReader(bytes, offset).readFloatLe()
}
