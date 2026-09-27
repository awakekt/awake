/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.core

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EntityNamesTest {
    private val world = World()
    private val names = EntityNames(world)

    private fun named(name: String): Entity = world.create().also { world.add(it, Name(name)) }

    @Test
    fun findsANamedEntity() {
        named("Floor")
        val door = named("Door")
        assertEquals(door, names.find("Door"))
    }

    @Test
    fun aRepeatedLookupDoesNotScanAgain() {
        val door = named("Door")
        names.find("Door")
        val rebuildsAfterFirst = names.rebuilds
        repeat(10) { assertEquals(door, names.find("Door")) }
        assertEquals(rebuildsAfterFirst, names.rebuilds)
    }

    @Test
    fun followsARename() {
        val door = named("Door")
        names.find("Door")
        world.get<Name>(door)!!.value = "Gate"
        assertNull(names.find("Door"))
        assertEquals(door, names.find("Gate"))
    }

    @Test
    fun aDestroyedEntityIsNotFound() {
        val door = named("Door")
        names.find("Door")
        world.destroy(door)
        assertNull(names.find("Door"))
    }

    @Test
    fun aRecycledIdUnderTheSameNameIsTheNewEntity() {
        val old = named("Door")
        names.find("Door")
        world.destroy(old)
        val replacement = named("Door")
        assertEquals(replacement, names.find("Door"))
    }

    @Test
    fun aNameAddedAfterTheIndexWasBuiltIsFound() {
        named("Floor")
        names.find("Floor")
        val late = named("Late")
        assertEquals(late, names.find("Late"))
    }
}
