/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScratchPoolTest {

    private class SampleItem(var value: Int = 0)

    @Test
    fun obtainsNewInstancesWhenEmpty() {
        var createCount = 0
        val pool = ScratchPool {
            createCount++
            SampleItem(createCount)
        }

        assertEquals(0, pool.count)
        assertEquals(0, pool.capacity)

        val item1 = pool.obtain()
        assertEquals(1, pool.count)
        assertEquals(1, pool.capacity)
        assertEquals(1, item1.value)

        val item2 = pool.obtain()
        assertEquals(2, pool.count)
        assertEquals(2, pool.capacity)
        assertEquals(2, item2.value)
    }

    @Test
    fun reusesInstancesAcrossResetWithoutAllocating() {
        var createCount = 0
        val pool = ScratchPool {
            createCount++
            SampleItem(createCount)
        }

        val first1 = pool.obtain()
        val first2 = pool.obtain()
        assertEquals(2, createCount)
        assertEquals(2, pool.count)

        pool.reset()
        assertEquals(0, pool.count)
        assertEquals(2, pool.capacity)

        val second1 = pool.obtain()
        val second2 = pool.obtain()
        assertEquals(2, pool.count)
        assertEquals(2, createCount, "No new instances should be created after reset")
        assertSame(first1, second1)
        assertSame(first2, second2)
    }

    @Test
    fun configureLambdaExecutesOnObtain() {
        val pool = ScratchPool { SampleItem(0) }
        val item = pool.obtain { it.value = 42 }
        assertEquals(42, item.value)
    }

    @Test
    fun getByIndexGrowsCapacity() {
        val pool = ScratchPool { SampleItem(0) }
        val item = pool[5]
        assertEquals(6, pool.capacity)
        assertSame(item, pool[5])
    }

    @Test
    fun resetInstanceInvokedOnActiveItems() {
        val resetList = mutableListOf<Int>()
        val pool = ScratchPool(
            resetInstance = { resetList.add(it.value) },
        ) {
            SampleItem()
        }

        val item1 = pool.obtain()
        item1.value = 10
        val item2 = pool.obtain()
        item2.value = 20

        assertEquals(2, pool.count)
        assertTrue(resetList.isEmpty())

        pool.reset()
        assertEquals(listOf(10, 20), resetList)
        assertEquals(0, pool.count)
    }

    @Test
    fun forEachActiveVisitsOnlyActiveItems() {
        val pool = ScratchPool { SampleItem() }
        pool.obtain().value = 1
        pool.obtain().value = 2
        pool.obtain().value = 3
        pool.reset()

        pool.obtain().value = 10
        pool.obtain().value = 20

        val visited = mutableListOf<Int>()
        pool.forEachActive { visited.add(it.value) }

        assertEquals(listOf(10, 20), visited)
    }

    @Test
    fun clearEmptiesPool() {
        val pool = ScratchPool { SampleItem() }
        pool.obtain()
        pool.obtain()
        assertEquals(2, pool.capacity)

        pool.clear()
        assertEquals(0, pool.capacity)
        assertEquals(0, pool.count)
    }
}
