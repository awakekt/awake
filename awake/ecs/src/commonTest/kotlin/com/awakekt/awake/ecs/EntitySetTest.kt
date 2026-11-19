/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Verifies [EntitySet] membership, generation isolation, backward-shift deletion, and randomized fuzzing. */
class EntitySetTest {
    @Test
    fun emptySetReportsEmptyAndRejectsInvalidCapacityOrIndex() {
        val set = EntitySet()
        assertEquals(0, set.size)
        assertTrue(set.isEmpty)
        assertFalse(set.isNotEmpty)
        assertFalse(Entity.of(0, 0) in set)
        assertFalse(set.remove(Entity.of(0, 0)))
        assertFalse(set.any { true })
        assertTrue(set.all { false })
        assertEquals("[]", set.toString())

        set.clear()
        var visits = 0
        set.forEach { visits++ }
        assertEquals(0, visits)

        assertFailsWith<IllegalArgumentException> { EntitySet(-1) }
        assertFailsWith<IndexOutOfBoundsException> { set.entityAt(0) }
    }

    @Test
    fun sameIdWithDifferentGenerationsAndExtremePackedValuesCoexist() {
        val set = EntitySet(initialCapacity = 4)
        val handles = listOf(
            Entity.of(5, 0),
            Entity.of(5, 1),
            Entity.of(5, 2),
            Entity(0L),
            Entity(-1L),
            Entity(Long.MAX_VALUE),
            Entity(Long.MIN_VALUE),
            Entity.of(Int.MAX_VALUE, Int.MAX_VALUE),
        )
        for (handle in handles) {
            assertTrue(set.add(handle), "expected first add to succeed for $handle")
            assertFalse(set.add(handle), "expected duplicate add to return false for $handle")
        }
        assertEquals(handles.size, set.size)
        for (handle in handles) {
            assertTrue(handle in set, "expected $handle in set")
        }

        assertTrue(set.remove(Entity.of(5, 1)))
        assertFalse(Entity.of(5, 1) in set)
        assertTrue(Entity.of(5, 0) in set)
        assertTrue(Entity.of(5, 2) in set)
        assertEquals(handles.size - 1, set.size)
    }

    @Test
    fun sustainedChurnNeverLosesEntriesOrDegradesLookup() {
        val set = EntitySet()
        val window = 32
        for (step in 0 until 10_000) {
            val inserted = Entity.of(step, step % 7)
            assertTrue(set.add(inserted))
            if (step >= window) {
                val evicted = Entity.of(step - window, (step - window) % 7)
                assertTrue(set.remove(evicted), "failed to evict $evicted at step $step")
                assertFalse(evicted in set)
            }
            assertEquals(minOf(step + 1, window), set.size)
            assertTrue(inserted in set)
        }
        val expectedStart = 10_000 - window
        for (id in expectedStart until 10_000) {
            assertTrue(Entity.of(id, id % 7) in set)
        }
    }

    @Test
    fun bulkOperationsFilteringEqualityAndClearWorkCorrectly() {
        val a = EntitySet()
        val b = EntitySet()
        val list = EntityList()
        for (id in 0 until 10) {
            list.add(Entity.of(id, 1))
        }
        assertTrue(a.addAll(list))
        assertFalse(a.addAll(list))
        for (id in 9 downTo 0) {
            b.add(Entity.of(id, 1))
        }
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertEquals(a.toSet(), b.toSet())
        assertEquals(10, a.toLongArray().size)

        assertTrue(a.removeAll { it.id % 2 == 0 })
        assertFalse(a.removeAll { it.id % 2 == 0 })
        assertTrue(b.retainAll { it.id % 2 == 1 })
        assertFalse(b.retainAll { it.id % 2 == 1 })
        assertEquals(a, b)
        assertEquals(5, a.size)

        val copied = EntitySet()
        assertTrue(copied.addAll(a))
        assertEquals(a, copied)

        val visited = HashSet<Entity>()
        copied.forEach { visited.add(it) }
        assertEquals(a.toSet(), visited)
        for (index in 0 until copied.size) {
            assertTrue(copied.entityAt(index) in visited)
        }

        copied.clear()
        assertTrue(copied.isEmpty)
        assertNotEquals(a, copied)
        assertTrue(copied.addAll(listOf(Entity.of(99, 0))))
        assertEquals(setOf(Entity.of(99, 0)), copied.toSet())
    }

    @Test
    fun matchesHashSetAcrossRandomizedOperations() {
        val random = Random(SEED)
        val actual = EntitySet()
        val expected = HashSet<Entity>()

        repeat(STEPS) { step ->
            val entity = Entity.of(random.nextInt(MAX_ID), random.nextInt(MAX_GEN))
            when (random.nextInt(10)) {
                in 0..4 -> assertEquals(expected.add(entity), actual.add(entity), "add $entity at step $step")
                in 5..8 -> assertEquals(expected.remove(entity), actual.remove(entity), "remove $entity at step $step")
                else -> if (step % CLEAR_EVERY == 0) {
                    actual.clear()
                    expected.clear()
                } else {
                    val mod = random.nextInt(2, 6)
                    assertEquals(
                        expected.removeAll { it.id % mod == 0 },
                        actual.removeAll { it.id % mod == 0 },
                        "removeAll at step $step",
                    )
                }
            }
            assertEquals(expected.size, actual.size)
            assertEquals(entity in expected, entity in actual)
            if (step % VERIFY_EVERY == 0) {
                assertEquals(expected, actual.toSet(), "mismatch at step $step")
                var count = 0
                actual.forEach {
                    assertTrue(it in expected, "unexpected $it in forEach at step $step")
                    count++
                }
                assertEquals(expected.size, count)
            }
        }
        assertEquals(expected, actual.toSet())
    }

    private companion object {
        const val SEED = 178002
        const val STEPS = 40_000
        const val MAX_ID = 1_500
        const val MAX_GEN = 4
        const val CLEAR_EVERY = 1_237
        const val VERIFY_EVERY = 311
    }
}
