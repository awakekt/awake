/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project

import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertNull

/** [IndexedDbContentCache] against the real browser's IndexedDB, each test under its own namespace. */
class IndexedDbContentCacheTest {
    @Test
    fun missingKeyReturnsNull() = runTest {
        assertNull(cache().get("absent"))
    }

    @Test
    fun putThenGetRoundTrips() = runTest {
        val cache = cache()
        val bytes = ByteArray(37) { (it * 11 - 5).toByte() }

        cache.put("a-sha256", bytes)

        assertContentEquals(bytes, cache.get("a-sha256"))
    }

    @Test
    fun everyTailLengthAndSignBitRoundTrips() = runTest {
        val cache = cache()
        // 0..11 covers every remainder the 4-byte word packing leaves as a tail, in both directions.
        for (length in 0..11) {
            val bytes = ByteArray(length) { (it * 37 - 128).toByte() }

            cache.put("length-$length", bytes)

            assertContentEquals(bytes, cache.get("length-$length"), "length $length")
        }
    }

    @Test
    fun emptyContentRoundTrips() = runTest {
        val cache = cache()

        cache.put("empty", ByteArray(0))

        assertContentEquals(ByteArray(0), cache.get("empty"))
    }

    @Test
    fun puttingAgainReplacesThePreviousValue() = runTest {
        val cache = cache()
        cache.put("key", byteArrayOf(1, 2, 3))

        cache.put("key", byteArrayOf(9, 9))

        assertContentEquals(byteArrayOf(9, 9), cache.get("key"))
    }

    @Test
    fun differentNamespacesDoNotCollide() = runTest {
        val suffix = Random.nextInt()
        val a = IndexedDbContentCache("test-$suffix-a")
        val b = IndexedDbContentCache("test-$suffix-b")
        a.put("same-sha256", byteArrayOf(1))
        b.put("same-sha256", byteArrayOf(2))

        assertContentEquals(byteArrayOf(1), a.get("same-sha256"))
        assertContentEquals(byteArrayOf(2), b.get("same-sha256"))
    }

    private fun cache() = IndexedDbContentCache("test-${Random.nextInt()}")
}
