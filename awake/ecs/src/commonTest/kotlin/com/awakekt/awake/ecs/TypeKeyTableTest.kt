/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The world's type table finds each class by its own token, including two classes of one simple
 * name, which on Kotlin/Wasm share a hash and a probe sequence, and keeps doing so as it grows.
 */
class TypeKeyTableTest {
    private object Red { class Marker }

    private object Blue { class Marker }

    private class A
    private class B
    private class C
    private class D
    private class E
    private class F
    private class G
    private class H
    private class I

    @Test
    fun twoClassesOfOneSimpleNameAreTwoTypes() {
        val world = World()
        val red = world.create()
        val blue = world.create()
        world.add(red, Red.Marker())
        world.add(blue, Blue.Marker())

        assertEquals(Red.Marker::class, world.get<Red.Marker>(red)!!::class)
        assertNull(world.get<Blue.Marker>(red), "the other Marker is another type")
        assertEquals(Blue.Marker::class, world.get<Blue.Marker>(blue)!!::class)
    }

    @Test
    fun everyTypeIsFoundAfterTheTableGrows() {
        val table = TypeKeyTable()
        val types = listOf(A::class, B::class, C::class, D::class, E::class, F::class, G::class, H::class, I::class, Red.Marker::class, Blue.Marker::class)
        types.forEachIndexed { id, type -> table.put(componentTypeKeyOf(type), ComponentTypeId(id)) }
        types.forEachIndexed { id, type -> assertEquals(ComponentTypeId(id), table[componentTypeKeyOf(type)]) }
        assertNull(table[componentTypeKeyOf(TypeKeyTableTest::class)])
    }
}
