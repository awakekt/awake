/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals

class IteratingSystemTest {
    private data class Position(var x: Float)
    private data class Velocity(var vx: Float)
    private data object Sleeping : EcsTag
    private data object Marker : EcsTag

    @Test
    fun iteratingSystemRunsBeginProcessEndAndSkipsExcludedEntities() {
        val world = World()
        val a = world.create()
        val b = world.create()
        world.add(a, Position(1f))
        world.add(a, Marker)
        world.add(b, Position(2f))
        world.add(b, Marker)
        world.add(b, Sleeping)

        val events = mutableListOf<String>()
        val system = object : IteratingSystem({
            all(Position::class, Marker::class)
            exclude(Sleeping::class)
        }) {
            override fun begin(world: World, delta: Float) {
                events += "begin:${family(world).size}"
            }

            override fun process(world: World, delta: Float, entity: Entity) {
                events += "process:${entity.id}"
            }

            override fun end(world: World, delta: Float) {
                events += "end"
            }
        }

        system.update(world, 0.016f)
        assertEquals(listOf("begin:1", "process:${a.id}", "end"), events)

        events.clear()
        world.remove<Sleeping>(b)
        system.update(world, 0.016f)
        assertEquals(listOf("begin:2", "process:${a.id}", "process:${b.id}", "end"), events)
    }

    @Test
    fun family1SystemIteratesPackedComponentsAndExcludesIncrementally() {
        val world = World()
        val active = world.create()
        val asleep = world.create()
        world.add(active, Position(0f))
        world.add(asleep, Position(0f))
        world.add(asleep, Sleeping)

        val system = object : Family1System<Position>(Position::class, Sleeping::class) {
            override fun process(world: World, delta: Float, entity: Entity, component: Position) {
                component.x += delta
            }
        }

        system.update(world, 2f)
        assertEquals(2f, world.get<Position>(active)?.x)
        assertEquals(0f, world.get<Position>(asleep)?.x)

        world.remove<Sleeping>(asleep)
        system.update(world, 3f)
        assertEquals(5f, world.get<Position>(active)?.x)
        assertEquals(3f, world.get<Position>(asleep)?.x)
    }

    @Test
    fun family2SystemIntegratesPackedColumnsWithoutWorldLookups() {
        val world = World()
        val active = world.create()
        val asleep = world.create()
        world.add(active, Position(10f))
        world.add(active, Velocity(4f))
        world.add(asleep, Position(100f))
        world.add(asleep, Velocity(50f))
        world.add(asleep, Sleeping)

        val system = object : Family2System<Position, Velocity>(
            Position::class,
            Velocity::class,
            Sleeping::class,
        ) {
            override fun process(world: World, delta: Float, entity: Entity, first: Position, second: Velocity) {
                first.x += second.vx * delta
            }
        }

        system.update(world, 0.5f)
        assertEquals(12f, world.get<Position>(active)?.x)
        assertEquals(100f, world.get<Position>(asleep)?.x)

        world.remove<Sleeping>(asleep)
        world.add(active, Sleeping)
        system.update(world, 1f)
        assertEquals(12f, world.get<Position>(active)?.x)
        assertEquals(150f, world.get<Position>(asleep)?.x)
    }

    @Test
    fun systemsRebindAutomaticallyAfterWorldClearAndAcrossWorlds() {
        val world1 = World()
        val e1 = world1.create()
        world1.add(e1, Position(1f))
        world1.add(e1, Velocity(2f))

        val specSystem = object : IteratingSystem({ all(Position::class) }) {
            var count = 0
            override fun process(world: World, delta: Float, entity: Entity) {
                count += 1
            }
        }
        val oneSystem = object : Family1System<Position>(Position::class) {
            var sum = 0f
            override fun process(world: World, delta: Float, entity: Entity, component: Position) {
                sum += component.x
            }
        }
        val twoSystem = object : Family2System<Position, Velocity>(Position::class, Velocity::class) {
            var sum = 0f
            override fun process(world: World, delta: Float, entity: Entity, first: Position, second: Velocity) {
                sum += first.x + second.vx
            }
        }

        specSystem.update(world1, 0f)
        oneSystem.update(world1, 0f)
        twoSystem.update(world1, 0f)
        assertEquals(1, specSystem.count)
        assertEquals(1f, oneSystem.sum)
        assertEquals(3f, twoSystem.sum)

        // World.clear increments generation; systems must rebind to the new family caches.
        world1.clear()
        val e2 = world1.create()
        world1.add(e2, Velocity(5f))
        world1.add(e2, Position(10f))
        specSystem.update(world1, 0f)
        oneSystem.update(world1, 0f)
        twoSystem.update(world1, 0f)
        assertEquals(2, specSystem.count)
        assertEquals(11f, oneSystem.sum)
        assertEquals(18f, twoSystem.sum)

        // Switching to a second World instance must also rebind cleanly.
        val world2 = World()
        val e3 = world2.create()
        world2.add(e3, Position(20f))
        world2.add(e3, Velocity(7f))
        specSystem.update(world2, 0f)
        oneSystem.update(world2, 0f)
        twoSystem.update(world2, 0f)
        assertEquals(3, specSystem.count)
        assertEquals(31f, oneSystem.sum)
        assertEquals(45f, twoSystem.sum)
    }
}
