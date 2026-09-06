/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import com.awakekt.awake.ecs.World
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The bug this composite exists to prevent is silent: two listeners registered on a system with
 * one slot, and one of them simply never runs. So the tests are about *both* halves happening,
 * and in an order teardown can rely on.
 */
class CompositeCellStreamListenerTest {

    private class Recorder(private val name: String, private val log: MutableList<String>) : AsyncWorldCellStreamListener {
        override suspend fun loadCell(coord: WorldCellCoord): CellContent {
            log += "load:$name"
            return CellContent { log += "apply:$name" }
        }

        override fun onCellUnload(world: World, coord: WorldCellCoord) {
            log += "unload:$name"
        }
    }

    private val coord = WorldCellCoord(0, 0)

    @Test
    fun everyListenerLoadsAndApplies() = runTest {
        val log = mutableListOf<String>()
        val composite = CompositeCellStreamListener(Recorder("nav", log), Recorder("mesh", log))

        composite.loadCell(coord).applyTo(World())

        assertTrue(log.containsAll(listOf("load:nav", "load:mesh", "apply:nav", "apply:mesh")), "$log")
    }

    /** Applies run in list order, so a listener that needs an earlier one's entities can say so. */
    @Test
    fun appliesRunInListOrder() = runTest {
        val log = mutableListOf<String>()
        val composite = CompositeCellStreamListener(Recorder("first", log), Recorder("second", log))

        composite.loadCell(coord).applyTo(World())

        assertEquals(listOf("apply:first", "apply:second"), log.filter { it.startsWith("apply:") })
    }

    /** Teardown is the mirror: whatever was built on top of something else comes off first. */
    @Test
    fun unloadsRunInReverseOrder() {
        val log = mutableListOf<String>()
        val composite = CompositeCellStreamListener(Recorder("first", log), Recorder("second", log))

        composite.onCellUnload(World(), coord)

        assertEquals(listOf("unload:second", "unload:first"), log)
    }

    /** A half-loaded cell is worse than an unloaded one, so one failure fails the whole cell. */
    @Test
    fun aFailedLoadAppliesNothing() = runTest {
        val log = mutableListOf<String>()
        val failing = object : AsyncWorldCellStreamListener {
            override suspend fun loadCell(coord: WorldCellCoord): CellContent = error("no terrain")
            override fun onCellUnload(world: World, coord: WorldCellCoord) = Unit
        }
        val composite = CompositeCellStreamListener(Recorder("nav", log), failing)

        assertFailsWith<IllegalStateException> { composite.loadCell(coord) }

        assertTrue(log.none { it.startsWith("apply:") }, "$log")
    }

    @Test
    fun anEmptyCompositeIsARegistrationMistake() {
        assertFailsWith<IllegalArgumentException> { CompositeCellStreamListener(emptyList()) }
    }
}
