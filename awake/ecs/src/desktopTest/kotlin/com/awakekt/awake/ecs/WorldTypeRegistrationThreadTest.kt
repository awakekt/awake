/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The first `add` of a component type registers it, and that is checked like any other change. */
class WorldTypeRegistrationThreadTest {
    private val enabledBefore = FrameThreadGuard.enabled

    private class Health(var points: Int = 0)

    private class Armor(var rating: Int = 0)

    @AfterTest
    fun restore() {
        FrameThreadGuard.enabled = enabledBefore
    }

    private fun boundWorld(): World {
        FrameThreadGuard.enabled = true
        return World().apply { frameThread.bind() }
    }

    @Test
    fun registeringATypeOffTheFrameThreadThrowsAndRegistersNothing() {
        val world = boundWorld()

        val thrown = offThread { world.components.typeId(Health::class) }

        assertIs<IllegalStateException>(thrown)
        assertTrue("new component type" in thrown.message.orEmpty(), thrown.message)
        assertNull(world.components.typeIdOrNull(Health::class), "a refused type gets no ID")
    }

    @Test
    fun theFirstAddOfATypeOnTheFrameThreadRegistersIt() {
        val world = boundWorld()
        val entity = world.create()

        world.add(entity, Health(3))
        world.add(entity, Armor::class).rating = 2

        assertEquals(3, world.get<Health>(entity)?.points)
        assertEquals(2, world.get<Armor>(entity)?.rating)
    }

    @Test
    fun aWorldNeverBoundRegistersTypesOnAnyThread() {
        FrameThreadGuard.enabled = true
        val world = World()

        assertNull(offThread { world.components.typeId(Health::class) })
    }

    private fun offThread(block: () -> Unit): Throwable? {
        var thrown: Throwable? = null
        thread { thrown = runCatching(block).exceptionOrNull() }.join()
        return thrown
    }
}
