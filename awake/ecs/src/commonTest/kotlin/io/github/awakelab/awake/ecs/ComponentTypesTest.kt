/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `componentTypes` is what an editor inspector or a debug overlay asks the world.
 *
 * The set it reports has to track `add`/`remove` exactly: a stale entry is a section for a
 * component that is gone, and a missing one is a component the tooling cannot see at all.
 */
class ComponentTypesTest {

    private data class Position(val x: Float = 0f)
    private data class Velocity(val x: Float = 0f)

    @Test
    fun componentTypesReportsWhatIsAttachedAndNothingElse() {
        val world = World()
        val entity = world.create()
        val other = world.create()
        world.add(entity, Position())
        world.add(entity, Velocity())
        // A second entity registers no new types but does set bits in its own signature, so a
        // world-wide type list would wrongly appear on `entity` too.
        world.add(other, Velocity())

        assertEquals(
            listOf(Position::class, Velocity::class),
            world.componentTypes(entity),
            "both components, in registration order",
        )
        assertEquals(listOf(Velocity::class), world.componentTypes(other))
    }

    @Test
    fun removingAComponentDropsItFromTheList() {
        val world = World()
        val entity = world.create()
        world.add(entity, Position())
        world.add(entity, Velocity())

        world.remove<Position>(entity)

        assertEquals(listOf(Velocity::class), world.componentTypes(entity))
    }

    @Test
    fun aDeadEntityReportsNoComponents() {
        val world = World()
        val entity = world.create()
        world.add(entity, Position())
        world.destroy(entity)

        assertTrue(world.componentTypes(entity).isEmpty(), "a destroyed entity has no components")
        // The id is recycled, so a stale handle must not read the successor's components either.
        val recycled = world.create()
        world.add(recycled, Velocity())
        assertTrue(
            world.componentTypes(entity).isEmpty(),
            "a stale handle must not see the recycled id's components",
        )
        assertEquals(listOf(Velocity::class), world.componentTypes(recycled))
    }
}
