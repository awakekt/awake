/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import kotlin.test.Test
import kotlin.test.assertEquals

class GpuResourcePoolTest {
    @Test
    fun clearDropsRetainedEntriesAfterTeardown() {
        var created = 0
        val pool = GpuResourcePool { ++created }

        assertEquals(3, pool[2])
        assertEquals(3, pool.size)

        val destroyed = mutableListOf<Int>()
        pool.forEach { destroyed += it }
        pool.clear()

        assertEquals(listOf(1, 2, 3), destroyed)
        assertEquals(0, pool.size)
    }
}
