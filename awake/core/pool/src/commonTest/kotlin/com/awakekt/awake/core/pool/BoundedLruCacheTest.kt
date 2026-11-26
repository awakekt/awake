/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.pool

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BoundedLruCacheTest {
    @Test
    fun evictsLeastRecentlyUsedEntriesAndRespectsDynamicCeilingAndPins() {
        var limit = 2
        val evicted = mutableListOf<Pair<String, Int>>()
        val cache = BoundedLruCache<String, Int>(maxEntries = { limit })

        cache.put("a", 1) { k, v -> evicted += k to v }
        cache.put("b", 2) { k, v -> evicted += k to v }
        assertTrue("a" in cache)
        assertEquals(1, cache.get("a"))

        cache.put("c", 3) { k, v -> evicted += k to v }
        assertEquals(listOf("b" to 2), evicted)
        assertFalse("b" in cache)
        assertNull(cache.get("b"))
        assertEquals(2, cache.size)

        limit = 1
        cache.evictOverLimit(pinned = setOf("a")) { k, v -> evicted += k to v }
        assertEquals(listOf("b" to 2, "c" to 3), evicted)
        assertEquals(1, cache.get("a"))
        assertNull(cache.get("c"))
    }

    @Test
    fun trimAllNotifiesOldestFirstAndLeavesCacheEmpty() {
        val cache = BoundedLruCache<String, Int>(maxEntries = 4)
        cache.put("first", 1)
        cache.put("second", 2)
        val removed = mutableListOf<Pair<String, Int>>()

        cache.trimAll { k, v -> removed += k to v }

        assertEquals(listOf("first" to 1, "second" to 2), removed)
        assertTrue(cache.isEmpty())
    }

    @Test
    fun removeAndClearDropEntriesWithoutCallbacks() {
        val cache = BoundedLruCache<String, Int>(maxEntries = 4)
        cache.put("k1", 10)
        cache.put("k2", 20)

        assertEquals(10, cache.remove("k1"))
        assertNull(cache.remove("k1"))
        assertEquals(1, cache.size)

        cache.clear()
        assertTrue(cache.isEmpty())
    }
}
