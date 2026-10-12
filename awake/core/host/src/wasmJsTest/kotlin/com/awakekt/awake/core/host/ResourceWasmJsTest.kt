/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.host

import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.set
import kotlin.test.Test
import kotlin.test.assertContentEquals

/** [ArrayBuffer.copyToByteArray]'s word-at-a-time unpacking reassembles the exact source bytes. */
class ResourceWasmJsTest {
    @Test
    fun roundTripsEveryTailLength() {
        // 0..11 covers every remainder (0..3) the 4-byte word view can leave as a tail, across
        // more than one word.
        for (length in 0..11) {
            val expected = ByteArray(length) { (it * 7 - 3).toByte() }
            assertContentEquals(expected, bufferOf(expected).copyToByteArray(), "length $length")
        }
    }

    @Test
    fun preservesNegativeAndHighBitBytes() {
        val expected = byteArrayOf(0, 1, -1, 127, -128, 42, -42, 100)
        assertContentEquals(expected, bufferOf(expected).copyToByteArray())
    }

    private fun bufferOf(bytes: ByteArray): ArrayBuffer {
        val array = Int8Array(bytes.size)
        for (i in bytes.indices) array[i] = bytes[i]
        return array.buffer
    }
}
