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

/** A sorted set of ids fills in any order, sorts without duplicates, searches, copies and diffs. */
class SortedLongSetTest {
    @Test
    fun sortingOrdersTheIdsAndDropsDuplicates() {
        val set = SortedLongSet()
        listOf(42L, -7L, 42L, Long.MAX_VALUE, 0L, -7L).forEach(set::addUnsorted)
        assertFalse(set.isSorted)
        assertFailsWith<IllegalStateException> { 42L in set }

        set.sort()

        assertTrue(set.isSorted)
        assertEquals(listOf(-7L, 0L, 42L, Long.MAX_VALUE), List(set.size) { set[it] })
        assertTrue(42L in set)
        assertFalse(41L in set)
    }

    @Test
    fun idsAddedInOrderStaySortedWithoutASort() {
        val set = SortedLongSet()
        listOf(1L, 2L, 5L).forEach(set::addUnsorted)
        assertTrue(set.isSorted)
        set.addUnsorted(5L)
        assertFalse(set.isSorted, "a repeated id needs the sort that drops it")
    }

    @Test
    fun clearKeepsWorkingAndCopyFromReplacesTheContent() {
        val first = SortedLongSet().apply { listOf(3L, 1L, 2L).forEach(::addUnsorted) }
        first.sort()
        val second = SortedLongSet().apply { addUnsorted(99L) }

        second.copyFrom(first)
        first.clear()

        assertEquals(listOf(1L, 2L, 3L), List(second.size) { second[it] })
        assertTrue(first.isEmpty)
        assertTrue(first.isSorted)
        second.copyFrom(second)
        assertEquals(3, second.size)
    }

    @Test
    fun theDiffReportsWhatEnteredAndWhatLeft() {
        val previous = sorted(1L, 3L, 5L, 7L)
        val current = sorted(2L, 3L, 7L, 9L)
        val entered = mutableListOf<Long>()
        val left = mutableListOf<Long>()

        SortedLongSet.diff(previous, current, { entered += it }, { left += it })

        assertEquals(listOf(2L, 9L), entered)
        assertEquals(listOf(1L, 5L), left)
        assertFailsWith<IllegalStateException> {
            SortedLongSet.diff(previous, SortedLongSet().apply { addUnsorted(2L); addUnsorted(1L) }, {}, {})
        }
    }

    @Test
    fun itAgreesWithAReferenceSetUnderRandomUse() {
        val random = Random(1557)
        val previous = SortedLongSet()
        val current = SortedLongSet()
        var reference = emptySet<Long>()
        repeat(500) {
            current.clear()
            val next = mutableSetOf<Long>()
            repeat(random.nextInt(0, 200)) {
                val id = random.nextLong(-1_000L, 1_000L)
                current.addUnsorted(id)
                next += id
            }
            current.sort()
            assertEquals(next.sorted(), List(current.size) { current[it] })
            repeat(20) {
                val probe = random.nextLong(-1_000L, 1_000L)
                assertEquals(probe in next, probe in current)
            }
            val entered = mutableListOf<Long>()
            val left = mutableListOf<Long>()
            SortedLongSet.diff(previous, current, { entered += it }, { left += it })
            assertEquals((next - reference).sorted(), entered)
            assertEquals((reference - next).sorted(), left)
            previous.copyFrom(current)
            reference = next
        }
    }

    @Test
    fun aNegativeCapacityIsRefused() {
        assertFailsWith<IllegalArgumentException> { SortedLongSet(initialCapacity = -1) }
    }

    private fun sorted(vararg ids: Long) = SortedLongSet().apply {
        ids.forEach(::addUnsorted)
        sort()
    }
}
