/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Verifies [EntityList] behavior across bounds checks, removals, bulk copies, and randomized fuzzing. */
class EntityListTest {
    @Test
    fun emptyListReportsEmptyAndRejectsOutOfBounds() {
        val list = EntityList()
        assertEquals(0, list.size)
        assertTrue(list.isEmpty)
        assertFalse(list.isNotEmpty)
        assertEquals(-1, list.lastIndex)
        assertTrue(list.indices.isEmpty())
        assertFalse(Entity.of(0, 0) in list)
        assertEquals(-1, list.indexOf(Entity.of(0, 0)))
        assertFalse(list.any { true })
        assertTrue(list.all { false })
        assertEquals("[]", list.toString())

        assertFailsWith<IllegalArgumentException> { EntityList(-1) }
        assertFailsWith<IndexOutOfBoundsException> { list[0] }
        assertFailsWith<IndexOutOfBoundsException> { list[0] = Entity.of(0, 0) }
        assertFailsWith<IndexOutOfBoundsException> { list.removeAt(0) }
        assertFailsWith<IndexOutOfBoundsException> { list.swapRemoveAt(0) }
        assertFailsWith<NoSuchElementException> { list.first() }
        assertFailsWith<NoSuchElementException> { list.last() }
        assertFailsWith<NoSuchElementException> { list.removeLast() }
    }

    @Test
    fun addGetSetDistinguishGenerationsAndGrowCleanly() {
        val list = EntityList(initialCapacity = 2)
        val gen0 = Entity.of(7, 0)
        val gen1 = Entity.of(7, 1)
        val other = Entity.of(8, 0)

        list.add(gen0)
        list.add(gen1)
        list.add(other)

        assertEquals(3, list.size)
        assertEquals(gen0, list.first())
        assertEquals(other, list.last())
        assertEquals(0, list.indexOf(gen0))
        assertEquals(1, list.indexOf(gen1))
        assertEquals(2, list.indexOf(other))
        assertTrue(gen0 in list)
        assertTrue(gen1 in list)
        assertFalse(Entity.of(7, 2) in list)

        val replaced = list.set(1, Entity.of(99, 3))
        assertEquals(gen1, replaced)
        assertEquals(Entity.of(99, 3), list[1])
        assertFalse(gen1 in list)
    }

    @Test
    fun removeAtPreservesOrderWhileSwapRemoveIsConstantTime() {
        val e0 = Entity.of(0, 0)
        val e1 = Entity.of(1, 0)
        val e2 = Entity.of(2, 0)
        val e3 = Entity.of(3, 0)

        val ordered = EntityList()
        ordered.addAll(listOf(e0, e1, e2, e3))
        assertEquals(e1, ordered.removeAt(1))
        assertEquals(listOf(e0, e2, e3), ordered.toList())
        assertTrue(ordered.remove(e0))
        assertFalse(ordered.remove(e0))
        assertEquals(listOf(e2, e3), ordered.toList())
        assertEquals(e3, ordered.removeLast())
        assertEquals(listOf(e2), ordered.toList())

        val swapped = EntityList()
        swapped.addAll(listOf(e0, e1, e2, e3))
        assertEquals(e1, swapped.swapRemoveAt(1))
        assertEquals(listOf(e0, e3, e2), swapped.toList())
        assertTrue(swapped.swapRemove(e0))
        assertEquals(listOf(e2, e3), swapped.toList())
        assertFalse(swapped.swapRemove(e0))
        assertEquals(e3, swapped.swapRemoveAt(1))
        assertEquals(listOf(e2), swapped.toList())
    }

    @Test
    fun addAllFiltersAndEqualityWorkWithoutLoss() {
        val a = EntityList()
        val b = EntityList()
        val set = EntitySet()
        for (id in 0 until 6) {
            val entity = Entity.of(id, id % 3)
            a.add(entity)
            b.add(entity)
            if (id >= 3) set.add(entity)
        }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertContentEquals(LongArray(6) { Entity.of(it, it % 3).packed }, a.toLongArray())

        assertTrue(a.removeAll { it.id % 2 == 0 })
        assertFalse(a.removeAll { it.id % 2 == 0 })
        assertEquals(listOf(Entity.of(1, 1), Entity.of(3, 0), Entity.of(5, 2)), a.toList())
        assertNotEquals(a, b)

        assertTrue(b.retainAll { it.id % 2 == 1 })
        assertFalse(b.retainAll { it.id % 2 == 1 })
        assertEquals(a, b)

        val visited = mutableListOf<Pair<Int, Entity>>()
        a.forEachIndexed { index, entity -> visited.add(index to entity) }
        assertEquals(
            listOf(0 to Entity.of(1, 1), 1 to Entity.of(3, 0), 2 to Entity.of(5, 2)),
            visited,
        )

        val combined = EntityList()
        combined.addAll(a)
        combined.addAll(set)
        assertEquals(6, combined.size)
        assertEquals(set.toSet(), combined.toList().subList(3, 6).toSet())

        combined.clear()
        assertTrue(combined.isEmpty)
        combined.trimToSize()
        combined.add(Entity.of(42, 1))
        assertEquals(listOf(Entity.of(42, 1)), combined.toList())
    }

    @Test
    fun matchesArrayListAcrossRandomizedOperations() {
        val random = Random(SEED)
        val actual = EntityList()
        val expected = ArrayList<Entity>()

        repeat(STEPS) { step ->
            when (random.nextInt(10)) {
                in 0..4 -> {
                    val entity = Entity.of(random.nextInt(MAX_ID), random.nextInt(MAX_GEN))
                    actual.add(entity)
                    expected.add(entity)
                }
                5 -> if (expected.isNotEmpty()) {
                    val index = random.nextInt(expected.size)
                    val entity = Entity.of(random.nextInt(MAX_ID), random.nextInt(MAX_GEN))
                    assertEquals(expected.set(index, entity), actual.set(index, entity))
                }
                6 -> if (expected.isNotEmpty()) {
                    val index = random.nextInt(expected.size)
                    assertEquals(expected.removeAt(index), actual.removeAt(index))
                }
                7 -> {
                    val entity = Entity.of(random.nextInt(MAX_ID), random.nextInt(MAX_GEN))
                    assertEquals(expected.remove(entity), actual.remove(entity))
                }
                8 -> if (expected.isNotEmpty()) {
                    assertEquals(expected.removeAt(expected.lastIndex), actual.removeLast())
                }
                else -> if (step % CLEAR_EVERY == 0) {
                    actual.clear()
                    expected.clear()
                } else {
                    val mod = random.nextInt(2, 5)
                    assertEquals(expected.removeAll { it.id % mod == 0 }, actual.removeAll { it.id % mod == 0 })
                }
            }
            assertEquals(expected.size, actual.size)
            if (step % VERIFY_EVERY == 0) {
                assertEquals(expected, actual.toList())
            }
        }
        assertEquals(expected, actual.toList())
    }

    private companion object {
        const val SEED = 1780
        const val STEPS = 10_000
        const val MAX_ID = 200
        const val MAX_GEN = 4
        const val CLEAR_EVERY = 337
        const val VERIFY_EVERY = 97
    }
}
