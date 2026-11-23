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

/** Verifies zero per-frame allocations across 10,000 warmed ticks for iterating systems and excluded packed families. */
class IteratingSystemAllocationTest {
    private class Position(var x: Int = 0)
    private class Velocity(val vx: Int = 1)
    private data object Sleeping : EcsTag

    @Test
    fun iteratingSystemWithExcludeAllocatesNothing() {
        val system = object : IteratingSystem({
            all(Position::class)
            exclude(Sleeping::class)
        }) {
            override fun process(world: World, delta: Float, entity: Entity) {
                world.get<Position>(entity)!!.x++
            }
        }
        measure("IteratingSystem(exclude)") { world -> system.update(world, 0.016f) }
    }

    @Test
    fun family1SystemWithExcludeAllocatesNothing() {
        val system = object : Family1System<Position>(Position::class, Sleeping::class) {
            override fun process(world: World, delta: Float, entity: Entity, component: Position) {
                component.x++
            }
        }
        measure("Family1System(exclude)") { world -> system.update(world, 0.016f) }
    }

    @Test
    fun family2SystemWithExcludeAllocatesNothing() {
        val system = object : Family2System<Position, Velocity>(Position::class, Velocity::class, Sleeping::class) {
            override fun process(world: World, delta: Float, entity: Entity, first: Position, second: Velocity) {
                first.x += second.vx
            }
        }
        measure("Family2System(exclude)") { world -> system.update(world, 0.016f) }
    }

    @Test
    fun cachedExcludedFamily2ForEachAllocatesNothing() {
        val world = populatedWorld()
        val family = world.familyExcluding<Position, Velocity>(Sleeping::class)
        measureOnWorld("Family2Excluding.forEach", world) {
            family.forEach { _, pos, vel -> pos.x += vel.vx }
        }
    }

    private fun populatedWorld(): World {
        val world = World()
        repeat(ACTIVE_COUNT) {
            val entity = world.create()
            world.add(entity, Position())
            world.add(entity, Velocity())
        }
        repeat(SLEEPING_COUNT) {
            val entity = world.create()
            world.add(entity, Position())
            world.add(entity, Velocity())
            world.add(entity, Sleeping)
        }
        return world
    }

    private inline fun measure(name: String, frame: (World) -> Unit) {
        measureOnWorld(name, populatedWorld(), frame)
    }

    private inline fun measureOnWorld(name: String, world: World, frame: (World) -> Unit) {
        val bean = ManagementFactory.getThreadMXBean() as ThreadMXBean
        check(bean.isThreadAllocatedMemorySupported)
        bean.isThreadAllocatedMemoryEnabled = true
        repeat(WARMUP_FRAMES) { frame(world) }
        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val before = bean.currentThreadAllocatedBytes
            repeat(MEASURED_FRAMES) { frame(world) }
            windows[window] = bean.currentThreadAllocatedBytes - before
        }
        println("$name: ${windows.joinToString()} bytes per $MEASURED_FRAMES frames")
        windows.sort()
        assertEquals(0L, windows[MEASURED_WINDOWS / 2], "$name median allocated bytes across $MEASURED_FRAMES frames")
        var total = 0
        world.queryEach<Position> { _, pos -> total += pos.x }
        assertEquals((WARMUP_FRAMES + MEASURED_WINDOWS * MEASURED_FRAMES) * ACTIVE_COUNT, total)
    }

    private companion object {
        const val ACTIVE_COUNT = 16
        const val SLEEPING_COUNT = 64
        const val WARMUP_FRAMES = 50_000
        const val MEASURED_FRAMES = 10_000
        const val MEASURED_WINDOWS = 5
    }
}
