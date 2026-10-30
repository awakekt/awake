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
import kotlin.test.assertIs

/** The browser's default cache, against the real IndexedDB. */
class PlatformContentCacheWasmTest {
    @Test
    fun theDefaultIsIndexedDbAndAFreshInstanceFindsWhatAnEarlierOneKept() = runTest {
        val namespace = "test-default-${Random.nextInt(0, Int.MAX_VALUE)}"
        val bytes = ByteArray(9) { it.toByte() }

        assertIs<IndexedDbContentCache>(platformContentCache(namespace)).put("ab12", bytes)

        // A new instance, as the next visit to the page makes.
        assertContentEquals(bytes, platformContentCache(namespace)?.get("ab12"))
    }
}
