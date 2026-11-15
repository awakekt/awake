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

/**
 * The JVM side of [LookupCalls]: run by the test task with escape analysis off, so an allocation
 * the JIT would hide still counts. Each per-entity call allocates nothing once warm.
 */
class LookupAllocationTest {
    @Test
    fun aComponentCallAllocatesNothingOnceWarm() {
        val threads = ManagementFactory.getThreadMXBean() as ThreadMXBean
        val lookups = LookupCalls()
        val costs = lookups.calls.map { (name, pass) ->
            repeat(WARMUP) { pass() }
            val start = threads.currentThreadAllocatedBytes
            repeat(PASSES) { pass() }
            name to (threads.currentThreadAllocatedBytes - start) / (PASSES.toLong() * LookupCalls.ENTITIES)
        }
        println("PERF jvm bytes per call: " + costs.joinToString { (name, bytes) -> "$name=$bytes" } + " (${lookups.sink})")
        costs.forEach { (name, bytes) -> assertEquals(0L, bytes, "$name allocates") }
    }

    private companion object {
        const val WARMUP = 2_000
        const val PASSES = 500
    }
}
