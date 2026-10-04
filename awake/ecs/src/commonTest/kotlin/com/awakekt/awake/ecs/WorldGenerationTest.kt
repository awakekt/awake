/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class WorldGenerationTest {
    private data class Alpha(val value: Int)
    private data class Beta(val value: Int)

    @Test
    fun clearChangesTheGenerationAndOrdinaryChangesDoNot() {
        val world = World()
        val start = world.generation

        val entity = world.create()
        world.add(entity, Alpha(1))
        world.destroy(entity)
        assertEquals(start, world.generation)

        world.clear()
        assertNotEquals(start, world.generation)
    }

    @Test
    fun aTypeIdResolvedBeforeClearMustBeResolvedAgain() {
        val world = World()
        val alphaBefore = world.typeId(Alpha::class)
        world.add(world.create(), Alpha(1))

        world.clear()
        // Beta registers first, so it takes the id Alpha held before the clear.
        val betaAfter = world.typeId(Beta::class)
        world.add(world.create(), Beta(2))

        assertEquals(alphaBefore, betaAfter, "Type ids restart from zero after clear()")
        assertNull(world.componentStore<Alpha>(world.typeId(Alpha::class)))
    }

    @Test
    fun componentStoreIsNullUntilSomethingCarriesTheType() {
        val world = World()
        assertNull(world.componentStore<Alpha>(world.typeId(Alpha::class)))

        val entity = world.create()
        world.add(entity, Alpha(7))

        val store = world.componentStore<Alpha>(world.typeId(Alpha::class))
        assertEquals(Alpha(7), store?.get(entity))
        assertSame(store, world.componentStore<Alpha>(world.typeId(Alpha::class)))
    }
}
