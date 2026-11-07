/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame

/** A lookup finds only the exact live handle, with no separate liveness check (#1428). */
class StaleHandleLookupTest {
    private class Health(val value: Int)

    private val health = componentType<Health>()

    @Test
    fun aDestroyedHandleFindsNothingInEveryGetForm() {
        val world = World()
        val entity = world.create()
        world.add(entity, Health(10))

        world.destroy(entity)

        assertNull(world.get<Health>(entity))
        assertNull(world.get(entity, Health::class))
        assertNull(world.get(entity, health))
    }

    @Test
    fun aStaleHandleDoesNotReadTheEntityThatReusedItsId() {
        val world = World()
        val stale = world.create()
        world.add(stale, Health(1))
        world.destroy(stale)

        val reused = world.create()
        world.add(reused, Health(2))

        assertEquals(stale.id, reused.id, "the id was recycled")
        assertNull(world.get<Health>(stale))
        assertNull(world.get(stale, health))
        assertEquals(2, world.get(reused, health)!!.value)
    }

    @Test
    fun aComponentLeftInTheRawStoreDoesNotAnswerForARecycledId() {
        val world = World()
        val leaked = world.create()
        val store = world.store(Health::class)
        val component = Health(1)
        store.add(leaked, component)
        world.destroy(leaked)

        val reused = world.create()

        assertEquals(leaked.id, reused.id, "the id was recycled")
        assertNull(world.get<Health>(reused), "the raw entry belongs to the old generation")
        assertFalse(store.contains(reused))
        assertSame(component, store.get(leaked), "the raw store still holds what it was given")
    }

    @Test
    fun aStoreMatchesOnlyTheExactHandle() {
        val store = ComponentStore(Health::class)
        val entity = Entity.of(3, 0)
        store.add(entity, Health(5))

        assertNull(store.get(Entity.of(3, 1)), "another generation of the same id")
        assertNull(store.remove(Entity.of(3, 1)))
        assertEquals(5, store.get(entity)!!.value)
    }
}
