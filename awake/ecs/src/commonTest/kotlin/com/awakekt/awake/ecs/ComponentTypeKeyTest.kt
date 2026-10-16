/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** Two component classes that share a simple name are still two component types. */
class ComponentTypeKeyTest {

    private class Player {
        data class Health(val value: Int)
    }

    private class Monster {
        data class Health(val value: Int)
    }

    @Test
    fun classesOfOneSimpleNameAreDistinctTypes() {
        val world = World()
        val entity = world.create()
        world.add(entity, Player.Health(10))
        world.add(entity, Monster.Health(20))

        assertNotEquals(world.typeId(Player.Health::class), world.typeId(Monster.Health::class))
        assertEquals(Player.Health(10), world.get<Player.Health>(entity))
        assertEquals(Monster.Health(20), world.get(entity, Monster.Health::class))
        world.remove<Player.Health>(entity)
        assertEquals(false, world.has<Player.Health>(entity))
        assertEquals(true, world.has(entity, Monster.Health::class))
    }
}
