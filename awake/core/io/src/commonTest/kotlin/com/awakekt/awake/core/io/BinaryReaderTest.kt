/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BinaryReaderTest {
    @Test
    fun readsLittleEndianValuesAndAdvances() {
        val reader = BinaryReader(byteArrayOf(0x34, 0x12, 0x78, 0x56, 0x34, 0x12, 0x7F))

        assertEquals(0x1234, reader.readShortLe())
        assertEquals(0x12345678, reader.readIntLe())
        assertEquals(0x7F, reader.readUnsignedByte())
        assertEquals(0, reader.remaining)
    }

    @Test
    fun rejectsOutOfBoundsReadsInsteadOfClamping() {
        val reader = BinaryReader(byteArrayOf(1, 2, 3))
        assertFailsWith<IndexOutOfBoundsException> { reader.readIntLe() }
        assertEquals(0, reader.position)
        assertFailsWith<IndexOutOfBoundsException> { reader.readBytes(4) }
    }

    @Test
    fun readsSlicesWithoutSharingMutableStorage() {
        val reader = BinaryReader(byteArrayOf(1, 2, 3))
        val slice = reader.readBytes(2)
        assertContentEquals(byteArrayOf(1, 2), slice)
        assertEquals(1, reader.remaining)
    }
}
