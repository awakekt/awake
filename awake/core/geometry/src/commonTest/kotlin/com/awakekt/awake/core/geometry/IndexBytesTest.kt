/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class IndexBytesTest {

    /** Byte ORDER is the whole point, so the expectation is spelled out rather than derived by
     * the same shifts the implementation uses -- which would agree with any bug it had. */
    @Test
    fun eachIndexBecomesFourLittleEndianBytes() {
        assertContentEquals(
            byteArrayOf(1, 0, 0, 0, 0, 1, 0, 0),
            intArrayOf(1, 256).toByteArrayLE(),
        )
    }

    /** A value with all four bytes distinct: catches a transposed or repeated shift that
     * symmetric inputs like 0 or 1 would hide. */
    @Test
    fun allFourBytesLandInAscendingSignificance() {
        assertContentEquals(
            byteArrayOf(0x78, 0x56, 0x34, 0x12),
            intArrayOf(0x12345678).toByteArrayLE(),
        )
    }

    /** Indices are unsigned on the GPU; Kotlin's Int is not. The top bit must survive as 0xFF
     * rather than sign-extending or throwing. */
    @Test
    fun theHighBitSurvivesAsAnUnsignedByte() {
        assertContentEquals(
            byteArrayOf(-1, -1, -1, -1),
            intArrayOf(-1).toByteArrayLE(),
        )
    }

    @Test
    fun emptyIndicesProduceNoBytes() {
        assertEquals(0, intArrayOf().toByteArrayLE().size)
    }
}
