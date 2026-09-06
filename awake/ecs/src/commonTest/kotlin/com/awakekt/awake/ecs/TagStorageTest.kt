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
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class TagStorageTest {
    @Test
    fun tagUsesEntityOnlyStorageWithoutChangingWorldApi() {
        val world = World()
        val entity = world.create()
        val store = world.store(TestTag::class)

        assertEquals(ComponentStorageKind.Uninitialized, world.storageKind<TestTag>())
        assertNull(world.add(entity, TestTag))

        assertEquals(ComponentStorageKind.TagSparseSet, world.storageKind<TestTag>())
        assertFalse(store.hasPayloadStorage)
        assertSame(TestTag, world.get<TestTag>(entity))
        assertTrue(world.has<TestTag>(entity))
        assertSame(TestTag, world.add(entity, TestTag))
        assertSame(TestTag, world.remove<TestTag>(entity))
        assertFalse(world.has<TestTag>(entity))
    }

    @Test
    fun ordinaryComponentStillUsesPayloadStorage() {
        val world = World()
        val entity = world.create()
        val store = world.store(ValueComponent::class)

        world.add(entity, ValueComponent(7))

        assertEquals(ComponentStorageKind.SparseSet, world.storageKind<ValueComponent>())
        assertTrue(store.hasPayloadStorage)
        assertEquals(7, world.get<ValueComponent>(entity)?.value)
    }

    @Test
    fun familyBuiltBeforeFirstTagTracksAddRemoveAndDestroy() {
        val world = World()
        val first = world.create()
        val second = world.create()
        world.add(first, ValueComponent(1))
        world.add(second, ValueComponent(2))
        val family = world.family<ValueComponent, TestTag>()

        world.add(first, TestTag)
        world.add(second, TestTag)
        assertEquals(setOf(first, second), family.entities())

        world.remove<TestTag>(first)
        assertEquals(setOf(second), family.entities())

        world.destroy(second)
        assertEquals(emptySet(), family.entities())
    }

    @Test
    fun storageDiagnosticsIdentifyTagsAndEntityMembership() {
        val world = World()
        val entity = world.create()
        world.add(entity, ValueComponent(9))
        world.add(entity, TestTag)

        val worldInfo = world.describeStorage().associateBy { it.type }
        assertEquals(ComponentStorageKind.SparseSet, worldInfo.getValue(ValueComponent::class).kind)
        assertEquals(ComponentStorageKind.TagSparseSet, worldInfo.getValue(TestTag::class).kind)
        assertEquals(1, worldInfo.getValue(TestTag::class).componentCount)

        val entityInfo = world.inspectStorage(entity)
        assertEquals(entity, entityInfo?.entity)
        assertEquals(setOf(ValueComponent::class, TestTag::class), entityInfo?.components?.map { it.type }?.toSet())
        assertNull(world.inspectStorage(Entity.of(entity.id, entity.generation + 1)))
    }

    @Test
    fun tagImplementationsMustReuseOneSingletonInstance() {
        val world = World()
        world.add(world.create(), InvalidTag())

        val failure = assertFailsWith<IllegalArgumentException> {
            world.add(world.create(), InvalidTag())
        }

        assertTrue(failure.message?.contains("singleton objects") == true)
    }

    private fun Family2<ValueComponent, TestTag>.entities(): Set<Entity> {
        val result = mutableSetOf<Entity>()
        forEach { entity, _, _ -> result += entity }
        return result
    }
}

private data class ValueComponent(val value: Int)

private data object TestTag : EcsTag

private class InvalidTag : EcsTag
