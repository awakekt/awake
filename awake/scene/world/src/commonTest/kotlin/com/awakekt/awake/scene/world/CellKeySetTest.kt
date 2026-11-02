/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CellKeySetTest {
    @Test
    fun addContainsAndRemove() {
        val set = CellKeySet()

        assertTrue(set.add(3, -4))
        assertFalse(set.add(3, -4), "a second add of the same cell changes nothing")
        assertTrue(set.contains(3, -4))
        assertFalse(set.contains(-4, 3), "x and z are not interchangeable")
        assertEquals(1, set.size)

        assertTrue(set.remove(3, -4))
        assertFalse(set.remove(3, -4))
        assertFalse(set.contains(3, -4))
        assertEquals(0, set.size)
    }

    @Test
    fun theCellThatPacksToTheEmptyMarkerStillWorks() {
        val set = CellKeySet()

        assertTrue(set.add(Int.MIN_VALUE, 0))
        assertTrue(set.contains(Int.MIN_VALUE, 0))
        assertFalse(set.contains(0, 0))
        assertTrue(set.remove(Int.MIN_VALUE, 0))
        assertFalse(set.contains(Int.MIN_VALUE, 0))
    }

    @Test
    fun itMatchesAHashSetThroughGrowthAndRemovals() {
        val set = CellKeySet()
        val reference = HashSet<Pair<Int, Int>>()
        val random = Random(1252)

        repeat(20_000) {
            val x = random.nextInt(-40, 40)
            val z = random.nextInt(-40, 40)
            if (random.nextInt(3) == 0) {
                assertEquals(reference.remove(x to z), set.remove(x, z))
            } else {
                assertEquals(reference.add(x to z), set.add(x, z))
            }
        }

        assertEquals(reference.size, set.size)
        for (x in -40 until 40) {
            for (z in -40 until 40) assertEquals(x to z in reference, set.contains(x, z), "cell $x, $z")
        }
    }

    @Test
    fun clearEmptiesIt() {
        val set = CellKeySet()
        for (x in 0 until 100) set.add(x, x)

        set.clear()

        assertEquals(0, set.size)
        assertFalse(set.contains(5, 5))
    }
}
