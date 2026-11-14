/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
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
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        var sum = 0L
        fun pass() = entities.forEach { sum += world.get<Health>(it)!!.value + (world.get<Speed>(it)?.value?.toLong() ?: 0L) }
        repeat(WARMUP) { pass() }
        val start = threads.currentThreadAllocatedBytes
        repeat(PASSES) { pass() }
        val perGet = (threads.currentThreadAllocatedBytes - start).toDouble() / (PASSES * COUNT * 2)
        println("PERF reified World.get bytes per get=$perGet ($sum)")
        assertEquals(0L, (threads.currentThreadAllocatedBytes - start) / (PASSES * COUNT * 2), "a get allocates")
    }

    private companion object {
        const val COUNT = 1_000
        const val WARMUP = 2_000
        const val PASSES = 2_000
    }
}
