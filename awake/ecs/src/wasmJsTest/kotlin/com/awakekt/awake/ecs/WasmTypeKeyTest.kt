/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * A `KClass` on Kotlin/Wasm hashes a qualified name it builds anew on every call, so a type lookup
 * keyed by it paid a string build and a full hash. The type table hashes the simple name instead,
 * without `String.hashCode`, which allocates on Kotlin/Wasm.
 */
class WasmTypeKeyTest {

    private class Position

    @Test
    fun aTypeKeyHashesTheSimpleName() {
        val expected = nameHash("Position")
        assertEquals(expected, componentTypeKeyHash(componentTypeKey<Position>()))
        assertEquals(expected, componentTypeKeyHash(componentTypeKeyOf(Position::class)))
        assertEquals(expected, WasmTypeKey(Position::class).hashCode())
    }

    private object Other {
        class Position
    }

    @Test
    fun aCallSiteTokenFindsItsOwnClassAmongClassesOfOneSimpleName() {
        val world = World()
        val entity = world.create()
        val mine = Position()
        val other = Other.Position()
        world.add(entity, mine)
        world.add(entity, other)
        assertSame(mine, world.get<Position>(entity))
        assertSame(other, world.get<Other.Position>(entity))
        assertSame(mine, world.get(entity, Position::class))
        assertSame(other, world.remove<Other.Position>(entity))
        assertNull(world.get<Other.Position>(entity))
        assertSame(mine, world.get<Position>(entity))
    }
}
