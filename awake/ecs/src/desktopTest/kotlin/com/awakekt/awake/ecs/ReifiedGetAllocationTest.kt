/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals

/** A reified `World.get`, the per-entity lookup systems use, allocates nothing. */
class ReifiedGetAllocationTest {
    private class Health(var value: Int)

    private class Speed(var value: Float)

    @Test
    fun aReifiedGetAllocatesNothing() {
        val world = World()
        val entities = List(COUNT) { world.create().also { e -> world.add(e, Health(it)); world.add(e, Speed(1f)) } }
        var sum = 0L
        fun pass() = entities.forEach { sum += world.get<Health>(it)!!.value + (world.get<Speed>(it)?.value?.toLong() ?: 0L) }
        repeat(WARMUP) { pass() }
        val reading = readAllocation(PASSES.toLong() * COUNT * 2) { repeat(PASSES) { pass() } }
        println("PERF reified World.get: $reading ($sum)")
        assertAllocatesNothing(reading, "a reified get")
    }

    private companion object {
        const val COUNT = 1_000
        const val WARMUP = 2_000
        const val PASSES = 2_000
    }
}
