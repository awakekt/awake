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

/** Measures the complete warmed typed-query path, including family resolution. */
class TypedQueryAllocationTest {
    private class Position(var x: Int = 0)
    private class Velocity(val x: Int = 1)
    private val positionType = Position::class
    private val velocityType = Velocity::class

    @Test
    fun oneComponentQueryAllocatesNothing() = measure("queryEach<A>") { world ->
        world.queryEach(positionType) { _, position -> position.x++ }
    }

    @Test
    fun twoComponentQueryAllocatesNothing() = measure("queryEach<A, B>") { world ->
        world.queryEach(positionType, velocityType) { _, position, velocity -> position.x += velocity.x }
    }

    @Test
    fun reifiedOneComponentQueryAllocatesNothing() = measure("queryEach<A> reified") { world ->
        world.queryEach<Position> { _, position -> position.x++ }
    }

    @Test
    fun reifiedTwoComponentQueryAllocatesNothing() = measure("queryEach<A, B> reified") { world ->
        world.queryEach<Position, Velocity> { _, position, velocity -> position.x += velocity.x }
    }

    @Test
    fun firstComponentLookupAllocatesNothing() = measure("firstOrNull<A>", visitsPerFrame = 1) { world ->
        world.firstOrNull<Position>()!!.x++
    }

    private inline fun measure(name: String, visitsPerFrame: Int = ENTITY_COUNT, frame: (World) -> Unit) {
        val world = World()
        repeat(ENTITY_COUNT) {
            val entity = world.create()
            world.add(entity, Position())
            world.add(entity, Velocity())
        }
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        repeat(WARMUP_FRAMES) { frame(world) }
        // An occasional JVM initialization allocation must not turn this into a flaky CI
        // threshold. A per-call regression allocates in every window, including the median.
        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val before = bean.currentThreadAllocatedBytes
            repeat(MEASURED_FRAMES) { frame(world) }
            windows[window] = bean.currentThreadAllocatedBytes - before
        }
        println("$name: ${windows.joinToString()} bytes per $MEASURED_FRAMES frames")
        windows.sort()
        assertEquals(0L, windows[MEASURED_WINDOWS / 2], "$name median allocated bytes across $MEASURED_FRAMES frames")
        assertEquals((WARMUP_FRAMES + MEASURED_WINDOWS * MEASURED_FRAMES) * visitsPerFrame, total(world))
    }

    private fun total(world: World): Int {
        var sum = 0
        world.queryEach<Position> { _, position -> sum += position.x }
        return sum
    }

    private companion object {
        const val ENTITY_COUNT = 32
        const val WARMUP_FRAMES = 50_000
        const val MEASURED_FRAMES = 10_000
        const val MEASURED_WINDOWS = 5
    }
}
