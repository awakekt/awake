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

/**
 * The JVM side of [LookupCalls]: run by the test task with escape analysis off, so an allocation
 * the JIT would hide still counts. Each per-entity call allocates nothing once warm.
 */
class LookupAllocationTest {
    @Test
    fun aComponentCallAllocatesNothingOnceWarm() {
        val lookups = LookupCalls()
        val costs = lookups.calls.map { (name, pass) ->
            repeat(WARMUP) { pass() }
            name to readAllocation(PASSES.toLong() * LookupCalls.ENTITIES) { repeat(PASSES) { pass() } }
        }
        println("PERF jvm bytes per call: " + costs.joinToString { (name, reading) -> "$name=${reading.perUnit}" } + " (${lookups.sink})")
        costs.forEach { (name, reading) -> assertAllocatesNothing(reading, name) }
    }

    private companion object {
        const val WARMUP = 2_000
        const val PASSES = 500
    }
}
