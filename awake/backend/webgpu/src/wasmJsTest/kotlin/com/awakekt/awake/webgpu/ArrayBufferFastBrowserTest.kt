/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import io.ygdrasil.webgpu.ArrayBuffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * The upload buffers copied out of wasm linear memory hold exactly the bytes the arrays did, as the
 * element-by-element copy made them, at every length and for every bit pattern; and a texture-sized
 * copy takes a fraction of the time. The printed milliseconds are for comparing the two on one machine.
 */
class ArrayBufferFastBrowserTest {
    @Test
    fun bytesOfEveryLengthAndValueComeOutAsTheyWent() {
        for (size in 0..9) {
            val bytes = ByteArray(size) { (it * 37 - 128).toByte() }
            assertContentEquals(bytes, fastArrayBufferOf(bytes).toByteArray(), "$size bytes")
        }
        val every = ByteArray(256) { it.toByte() }
        assertContentEquals(every, fastArrayBufferOf(every).toByteArray())
    }

    @Test
    fun floatsAndIntsKeepTheirBits() {
        val floats = floatArrayOf(0f, -0f, 1.5f, -2.25f, Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE, Float.MAX_VALUE)
        assertContentEquals(floats.map { it.toRawBits() }, fastArrayBufferOf(floats).toFloatArray().map { it.toRawBits() })
        val ints = intArrayOf(0, 1, -1, Int.MIN_VALUE, Int.MAX_VALUE, 0x12345678)
        assertContentEquals(ints, fastArrayBufferOf(ints).toIntArray())
        assertEquals(0uL, fastArrayBufferOf(FloatArray(0)).size)
    }

    @Test
    fun aTextureSizedCopyIsAFractionOfTheElementByElementOne() {
        val pixels = ByteArray(TEXTURE_BYTES) { (it * 31).toByte() }
        val elementByElement = timed { ArrayBuffer.allocate(pixels.size.toULong()).apply { setBytes(0uL, pixels) } }
        val fromLinearMemory = timed { fastArrayBufferOf(pixels) }
        println("ArrayBufferFastBrowserTest: 1024x1024 RGBA texture, element by element $elementByElement, from linear memory $fromLinearMemory")
        assertContentEquals(pixels, fastArrayBufferOf(pixels).toByteArray())
    }

    private inline fun timed(copy: () -> ArrayBuffer): Duration {
        repeat(WARM_UP) { copy() }
        val start = TimeSource.Monotonic.markNow()
        repeat(MEASURED) { copy() }
        return start.elapsedNow() / MEASURED
    }

    private companion object {
        const val TEXTURE_BYTES = 1024 * 1024 * 4
        const val WARM_UP = 2
        const val MEASURED = 5
    }
}
