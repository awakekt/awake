/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.blueprint

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Running a blueprint allocates nothing: an idle tick, a tick with a wait pending, and firing a chain
 * through a branch, math and variables.
 *
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class BlueprintAllocationProbe {
    private val nodes = BlueprintNodes.core()
    private val interpreter = BlueprintInterpreter()

    private val chain = BlueprintCompiler.compile(
        graph {
            node("start", "event.start")
            node("big", "math.greater", "a" to 5f, "b" to 2f)
            node("branch", "flow.branch")
            node("sum", "math.add", "a" to 1f, "b" to 2f)
            node("set", "var.set.float", "name" to "total")
            node("get", "var.get.float", "name" to "total")
            node("again", "var.set.float", "name" to "copy")
            wire("start.then", "branch.exec")
            wire("big.result", "branch.condition")
            wire("branch.true", "set.exec")
            wire("sum.sum", "set.value")
            wire("set.then", "again.exec")
            wire("get.value", "again.value")
        },
        nodes,
    )

    private val waiting = BlueprintCompiler.compile(
        graph {
            node("start", "event.start")
            node("wait", "flow.delay", "seconds" to 1_000_000f)
            wire("start.then", "wait.exec")
        },
        nodes,
    )

    @Test
    fun anIdleTickAllocatesNothing() {
        val instance = BlueprintInstance(chain).also(interpreter::start)
        assertEquals(0L, bytesPer { interpreter.tick(instance, TICK) })
    }

    @Test
    fun aTickWithAWaitPendingAllocatesNothing() {
        val instance = BlueprintInstance(waiting).also(interpreter::start)
        assertEquals(0L, bytesPer { interpreter.tick(instance, TICK) })
    }

    @Test
    fun firingAChainAllocatesNothing() {
        val instance = BlueprintInstance(chain)
        assertEquals(0L, bytesPer { interpreter.start(instance) })
        assertEquals(3f, instance.variable("copy"))
    }

    private fun bytesPer(block: () -> Unit): Long {
        repeat(WARMUP) { block() }
        val before = allocated()
        repeat(MEASURED) { block() }
        return (allocated() - before) / MEASURED
    }

    private fun allocated(): Long = (ManagementFactory.getThreadMXBean() as ThreadMXBean).currentThreadAllocatedBytes

    private companion object {
        const val TICK = 1f / 60f
        const val WARMUP = 20_000
        const val MEASURED = 10_000
    }
}
