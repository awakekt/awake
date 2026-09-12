/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorldExtensionsTest {

    private data class Score(var points: Int)
    private data class Health(val current: Int)
    private data object ActiveTag

    @Test
    fun reifiedAddGetRemoveAndHasWorkCorrectly() {
        val world = World()
        val entity = world.create()

        assertFalse(world.has<Score>(entity))
        assertNull(world.get<Score>(entity))

        world.add(entity, Score(100))
        assertTrue(world.has<Score>(entity))
        assertEquals(100, world.get<Score>(entity)?.points)

        val removed = world.remove<Score>(entity)
        assertEquals(100, removed?.points)
        assertFalse(world.has<Score>(entity))
    }

    @Test
    fun requireReturnsExistingOrThrows() {
        val world = World()
        val entity = world.create()

        assertFailsWith<IllegalStateException> {
            world.require<Health>(entity)
        }

        world.add(entity, Health(50))
        assertEquals(50, world.require<Health>(entity).current)
    }

    @Test
    fun getOrPutRetrievesOrInserts() {
        val world = World()
        val entity = world.create()

        val created = world.getOrPut(entity) { Health(100) }
        assertEquals(100, created.current)

        val existing = world.getOrPut(entity) { Health(200) }
        assertEquals(100, existing.current)
    }

    @Test
    fun updateTransformsImmutableComponent() {
        val world = World()
        val entity = world.create()

        val updatedMissing = world.update<Health>(entity) { it.copy(current = it.current + 10) }
        assertNull(updatedMissing)

        world.add(entity, Health(40))
        val updated = world.update<Health>(entity) { it.copy(current = it.current + 10) }
        assertNotNull(updated)
        assertEquals(50, updated.current)
        assertEquals(50, world.get<Health>(entity)?.current)
    }

    @Test
    fun mutateAppliesInPlaceMutation() {
        val world = World()
        val entity = world.create()

        val mutatedMissing = world.mutate<Score>(entity) { it.points += 25 }
        assertNull(mutatedMissing)

        world.add(entity, Score(10))
        val mutated = world.mutate<Score>(entity) { it.points += 25 }
        assertNotNull(mutated)
        assertEquals(35, mutated.points)
        assertEquals(35, world.get<Score>(entity)?.points)
    }

    @Test
    fun singleOrNullAndSingleEntityOrNull() {
        val world = World()
        assertNull(world.singleOrNull<Health>())
        assertNull(world.singleEntityOrNull<Health>())

        val e1 = world.create()
        world.add(e1, Health(80))

        assertEquals(80, world.singleOrNull<Health>()?.current)
        assertEquals(e1, world.singleEntityOrNull<Health>())

        val e2 = world.create()
        world.add(e2, Health(90))

        // Multiple entities with Health should resolve to null (like Iterable.singleOrNull)
        assertNull(world.singleOrNull<Health>())
        assertNull(world.singleEntityOrNull<Health>())
    }

    @Test
    fun hasAllAndHasAnyCheckCombinations() {
        val world = World()
        val entity = world.create()

        world.add(entity, Health(100))
        assertFalse(world.hasAll<Health, ActiveTag>(entity))
        assertTrue(world.hasAny<Health, ActiveTag>(entity))

        world.add(entity, ActiveTag)
        assertTrue(world.hasAll<Health, ActiveTag>(entity))
        assertTrue(world.hasAny<Health, ActiveTag>(entity))
    }

    @Test
    fun worldCreateDslBuildsEntity() {
        val world = World()
        val entity = world.create {
            add(Health(75))
            add(ActiveTag)
        }

        assertTrue(world.isAlive(entity))
        assertEquals(75, world.get<Health>(entity)?.current)
        assertTrue(world.has<ActiveTag>(entity))
    }

    @Test
    fun entityReceiverExtensionsWorkFluidly() {
        val world = World()
        val entity = world.create()

        entity.add(world, Health(60))
        assertTrue(entity.has<Health>(world))
        assertEquals(60, entity.get<Health>(world)?.current)
        assertEquals(60, entity.require<Health>(world).current)

        val removed = entity.remove<Health>(world)
        assertEquals(60, removed?.current)
        assertFalse(entity.has<Health>(world))

        assertTrue(entity.destroy(world))
        assertFalse(world.isAlive(entity))
    }
}
