/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/** The paged sparse index answers as a plain map would, through sets, moves, removes and clears. */
class EntityIndexMapTest {
    @Test
    fun itAnswersAsAMapThroughEveryKindOfChange() {
        val random = Random(SEED)
        val map = EntityIndexMap()
        val expected = HashMap<Int, Int>()
        repeat(STEPS) { step ->
            val id = random.nextInt(MAX_ID)
            when (random.nextInt(10)) {
                in 0..5 -> random.nextInt(MAX_ID).let { map.set(id, it); expected[id] = it }
                in 6..8 -> { map.remove(id); expected.remove(id) }
                else -> if (step % CLEAR_EVERY == 0) { map.clear(); expected.clear() }
            }
        }
        for (id in -1..MAX_ID + EntityIndexMap.PAGE_SIZE) {
            assertEquals(expected[id] ?: EntityIndexMap.ABSENT, map.get(id), "id $id")
        }
    }

    @Test
    fun anIdPastEveryPageAndANegativeIdAreAbsent() {
        val map = EntityIndexMap()
        map.set(3, 7)
        assertEquals(7, map.get(3))
        assertEquals(EntityIndexMap.ABSENT, map.get(-5))
        assertEquals(EntityIndexMap.ABSENT, map.get(Int.MAX_VALUE))
        map.remove(Int.MAX_VALUE)
        map.remove(-1)
        assertEquals(7, map.get(3))
    }

    private companion object {
        const val SEED = 1753
        const val STEPS = 40_000
        const val MAX_ID = 20_000
        const val CLEAR_EVERY = 997
    }
}
