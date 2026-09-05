/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.core.host

/**
 * Reads a 32-bit signed integer in little-endian byte order starting at [offset].
 */
fun ByteArray.readLittleEndianInt(offset: Int): Int =
    (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

/**
 * Reads a 16-bit signed short in little-endian byte order starting at [offset].
 */
fun ByteArray.readLittleEndianShort(offset: Int): Short {
    val unsigned = (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8)
    return unsigned.toShort()
}
