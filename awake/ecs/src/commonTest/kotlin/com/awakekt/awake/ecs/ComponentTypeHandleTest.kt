/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** A [ComponentType] handle finds the same component as the class does, in every world. */
class ComponentTypeHandleTest {
    private data class Health(val value: Int)

    private data class Speed(val value: Float)

    private val healthType = componentType<Health>()
    private val speedType = componentType<Speed>()

    @Test
    fun handleReadsWhatTheClassWrote() {
        val world = World()
        val entity = world.create()
        world.add(entity, Health(10))

        assertEquals(Health(10), world.get(entity, healthType))
        assertTrue(world.has(entity, healthType))
        assertEquals(world.typeId(Health::class), world.typeId(healthType))
    }

    @Test
    fun classReadsWhatTheHandleWrote() {
        val world = World()
        val entity = world.create()
        assertNull(world.add(entity, speedType, Speed(2f)))

        assertEquals(Speed(2f), world.get<Speed>(entity))
        assertEquals(Speed(2f), world.get(entity, Speed::class))
        assertEquals(Speed(2f), world.remove(entity, speedType))
        assertFalse(world.has<Speed>(entity))
    }

    @Test
    fun oneHandleServesWorldsThatNumberedTheirTypesDifferently() {
        val first = World()
        val second = World()
        val a = first.create()
        val b = second.create()
        first.add(a, Health(1))
        first.add(a, Speed(1f))
        second.add(b, Speed(2f))
        second.add(b, Health(2))

        assertNotEquals(first.typeId(healthType), second.typeId(healthType))
        assertEquals(Health(1), first.get(a, healthType))
        assertEquals(Health(2), second.get(b, healthType))
        assertEquals(Speed(2f), second.get<Speed>(b))
    }

    @Test
    fun aTypeNoEntityCarriedIsAbsentAndUnregistered() {
        val world = World()
        val entity = world.create()

        assertNull(world.get(entity, healthType))
        assertFalse(world.has(entity, healthType))
        assertNull(world.remove(entity, healthType))
        assertEquals(ComponentStorageKind.Unregistered, world.storageKind<Health>())
    }

    @Test
    fun clearForgetsTheWorldsIdsButNotTheHandle() {
        val world = World()
        world.add(world.create(), Health(1))
        world.clear()
        val entity = world.create()

        assertNull(world.get(entity, healthType))
        world.add(entity, Speed(3f))
        world.add(entity, Health(3))
        assertEquals(Health(3), world.get(entity, healthType))
        assertEquals(Health(3), world.get<Health>(entity))
    }

    @Test
    fun handleFamiliesAreTheClassFamilies() {
        val world = World()
        val entity = world.create()
        world.add(entity, Health(1))
        world.add(entity, Speed(1f))

        assertSame(world.family(Health::class), world.family(healthType))
        assertSame(world.family(Health::class, Speed::class), world.family(healthType, speedType))
    }

    @Test
    fun handlesForOneClassAreEqual() {
        assertEquals(healthType, componentType(Health::class))
        assertNotEquals<ComponentType<*>>(healthType, speedType)
    }
}
