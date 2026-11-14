/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.pool

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Ids are handed out smallest first, reused once released, bounded by maxIds, and visited in order. */
class ReusableIdSetTest {
    @Test
    fun theSmallestFreeIdIsHandedOutAndReusedAfterRelease() {
        val ids = ReusableIdSet()

        assertEquals(listOf(0, 1, 2, 3), List(4) { ids.allocate() })
        assertTrue(ids.release(1))
        assertTrue(ids.release(2))
        assertFalse(ids.release(2), "an id already free")
        assertFalse(ids.release(-1))
        assertFalse(ids.release(1_000))

        assertEquals(1, ids.allocate())
        assertEquals(2, ids.allocate())
        assertEquals(4, ids.allocate())
        assertEquals(5, ids.size)
    }

    @Test
    fun itGrowsPastItsInitialCapacityAndStopsAtMaxIds() {
        val ids = ReusableIdSet(initialCapacity = 1, maxIds = 130)

        assertEquals((0 until 130).toList(), List(130) { ids.allocate() })
        assertTrue(ids.isFull)
        assertEquals(ReusableIdSet.NONE, ids.tryAllocate())
        assertFailsWith<IllegalStateException> { ids.allocate() }

        ids.release(129)
        assertEquals(129, ids.allocate())
    }

    @Test
    fun addMarksAnIdFromElsewhereAndAllocationSkipsIt() {
        val ids = ReusableIdSet(maxIds = 300)

        assertTrue(ids.add(0))
        assertTrue(ids.add(200))
        assertFalse(ids.add(200))
        assertFailsWith<IllegalArgumentException> { ids.add(300) }
        assertFailsWith<IllegalArgumentException> { ids.add(-1) }

        assertEquals(1, ids.allocate())
        assertTrue(200 in ids)
        assertFalse(199 in ids)
    }

    @Test
    fun forEachVisitsTheIdsInUseInAscendingOrder() {
        val ids = ReusableIdSet()
        listOf(130, 3, 64, 63, 0).forEach { ids.add(it) }

        val seen = mutableListOf<Int>()
        ids.forEach { seen += it }

        assertEquals(listOf(0, 3, 63, 64, 130), seen)
        ids.clear()
        assertTrue(ids.isEmpty)
        assertEquals(0, ids.allocate(), "a cleared set starts again from 0")
    }

    @Test
    fun itAgreesWithAReferenceSetUnderRandomUse() {
        val ids = ReusableIdSet(initialCapacity = 8, maxIds = 500)
        val reference = mutableSetOf<Int>()
        val random = Random(1557)
        repeat(20_000) {
            if (random.nextInt(3) == 0 && reference.isNotEmpty()) {
                val id = reference.elementAt(random.nextInt(reference.size))
                assertTrue(ids.release(id))
                reference.remove(id)
            } else {
                val expected = (0 until 500).firstOrNull { it !in reference } ?: ReusableIdSet.NONE
                assertEquals(expected, ids.tryAllocate())
                if (expected >= 0) reference.add(expected)
            }
            assertEquals(reference.size, ids.size)
        }
        val seen = mutableListOf<Int>()
        ids.forEach { seen += it }
        assertEquals(reference.sorted(), seen)
    }

    @Test
    fun badLimitsAreRefused() {
        assertFailsWith<IllegalArgumentException> { ReusableIdSet(initialCapacity = 0) }
        assertFailsWith<IllegalArgumentException> { ReusableIdSet(maxIds = 0) }
    }
}
