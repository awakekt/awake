/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.world.AsyncWorldCellStreamListener
import com.awakekt.awake.world.CellContent
import com.awakekt.awake.world.WorldCellCoord
import com.awakekt.awake.world.WorldCellState
import com.awakekt.awake.world.WorldPartitionConfig
import com.awakekt.awake.world.WorldPartitionProbe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What [WorldPartitionSystem.probe] records: the debug source reads only this, so each state is asserted here. */
class WorldPartitionProbeTest {
    private val config = WorldPartitionConfig(cellSize = 10f, loadingRadius = 10f, unloadRadius = 20f)

    private class GatedLoader : AsyncWorldCellStreamListener {
        private val gate = CompletableDeferred<Unit>()

        override suspend fun loadCell(coord: WorldCellCoord): CellContent {
            gate.await()
            return CellContent { }
        }

        override fun onCellUnload(world: World, coord: WorldCellCoord) = Unit

        fun release() = gate.complete(Unit)
    }

    private fun World.observerAt(position: Vec3f) = create().also {
        add(it, Transform(position = position))
        add(it, StreamObserver)
    }

    @Test
    fun cellsReadUntilTheirLoadIsAppliedThenShow() = runTest {
        val world = World()
        val loader = GatedLoader()
        val system = WorldPartitionSystem(config, asyncStreamListener = loader, loadScope = this)
        world.observerAt(Vec3f(5f, 0f, 5f))

        system.update(world, 0f)
        testScheduler.runCurrent()
        val probe = system.probe
        assertTrue(probe.hasObserver)
        assertEquals(0 to 0, probe.observerCellX to probe.observerCellZ)
        assertEquals(system.activeCells.size, probe.cellCount)
        assertEquals(probe.cellCount, probe.reading, "every load is still in flight")
        assertEquals(0, probe.shown)

        loader.release()
        testScheduler.advanceUntilIdle()
        system.update(world, 0f)

        assertEquals(0, probe.reading)
        assertEquals(probe.cellCount, probe.shown, "applied loads show")
    }

    @Test
    fun aLoadedCellPastTheLoadingRadiusIsLeaving() {
        val world = World()
        val system = WorldPartitionSystem(config)
        val observer = world.observerAt(Vec3f(5f, 0f, 5f))
        system.update(world, 0f)
        assertTrue(WorldCellCoord(0, 0) in system.activeCells)

        // Cell (0, 0)'s centre is (5, 5): 15 m from here, past the loading radius but inside the unloading one.
        world.add(observer, Transform(position = Vec3f(20f, 0f, 5f)))
        system.update(world, 0f)

        val probe = system.probe
        val index = (0 until probe.cellCount).first { probe.cellXAt(it) == 0 && probe.cellZAt(it) == 0 }
        assertEquals(WorldCellState.Leaving, probe.stateAt(index))
        assertEquals(probe.cellCount, probe.reading + probe.shown + probe.leaving)
        assertEquals(system.activeCells.size, probe.cellCount)
        assertEquals(2, probe.observerCellX)
    }

    @Test
    fun theObserverIsRecordedAbsoluteAndAsDrawnUnderAShiftedOrigin() {
        val world = World()
        world.add(world.create(), WorldOrigin(stepX = 1, quantum = 1000f))
        val system = WorldPartitionSystem(config)
        world.observerAt(Vec3f(5f, 1f, 5f))

        system.update(world, 0f)

        val probe = system.probe
        assertEquals(Vec3f(1005f, 1f, 5f), probe.observer)
        assertEquals(Vec3f(5f, 1f, 5f), probe.observerLocal)
        assertEquals(100, probe.observerCellX)
    }

    @Test
    fun losingTheObserverKeepsTheCellsAndClearingForgetsThem() {
        val world = World()
        val system = WorldPartitionSystem(config)
        val observer = world.observerAt(Vec3f(5f, 0f, 5f))
        system.update(world, 0f)
        val cells = system.probe.cellCount

        world.destroy(observer)
        system.update(world, 0f)
        assertFalse(system.probe.hasObserver)
        assertEquals(cells, system.probe.cellCount, "the partition still holds them")

        system.clear(world)
        assertEquals(0, system.probe.cellCount)
    }

    @Test
    fun aStillObserverIsRecordedTheSameEveryUpdate() {
        val world = World()
        val system = WorldPartitionSystem(config)
        world.observerAt(Vec3f(5f, 0f, 5f))
        system.update(world, 0f)
        val probe: WorldPartitionProbe = system.probe
        val first = (0 until probe.cellCount).map { Triple(probe.cellXAt(it), probe.cellZAt(it), probe.stateAt(it)) }.toSet()

        system.update(world, 0f)

        val second = (0 until probe.cellCount).map { Triple(probe.cellXAt(it), probe.cellZAt(it), probe.stateAt(it)) }.toSet()
        assertEquals(first, second)
        assertEquals(system.activeCells, first.map { WorldCellCoord(it.first, it.second) }.toSet())
    }
}
