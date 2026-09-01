/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.navigation.grid

import io.github.awakelab.awake.asset.terrain.Heightmap
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.world.StreamObserver
import io.github.awakelab.awake.scene.world.WorldCellCoord
import io.github.awakelab.awake.scene.world.WorldPartitionConfig
import io.github.awakelab.awake.scene.world.WorldPartitionSystem
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Driven through the real `WorldPartitionSystem` rather than by calling the listener directly:
 * the parts that can go wrong here — a bake landing after its cell left, an unload racing a
 * search — only exist in that arrangement.
 */
class NavGridCellStreamerTest {

    private val config = WorldPartitionConfig(
        cellSize = 8f,
        loadingRadius = 8f,
        unloadRadius = 16f,
    )

    /** Flat ground covering one cell, at one sample per metre. */
    private fun flatCell(samples: Int = 9): Heightmap = Heightmap(
        samples = FloatArray(samples * samples),
        width = samples,
        depth = samples,
        scale = Vec3f(1f, 1f, 1f),
    )

    private fun World.observerAt(x: Float, z: Float) = create().also {
        add(it, Transform(position = Vec3f(x, 0f, z)))
        add(it, StreamObserver)
    }

    private fun grid() = StreamedNavGrid(samplesPerCell = 8, sampleSize = 1f)

    @Test
    fun bakesATileForEveryCellTheWorldStreamsIn() = runTest {
        val world = World()
        val grid = grid()
        val system = WorldPartitionSystem(
            config,
            asyncStreamListener = NavGridCellStreamer(grid, config, { flatCell() }),
            loadScope = this,
        )
        world.observerAt(4f, 4f)

        system.update(world, 0f)
        testScheduler.advanceUntilIdle()
        system.applyLoadedCells(world)

        assertTrue(WorldCellCoord(0, 0) in grid.residentCells, "${grid.residentCells}")
        // The whole point: a route exists over terrain nobody baked by hand.
        assertTrue(grid.findPath(Vec3f(1f, 0f, 1f), Vec3f(6f, 0f, 6f)).isNotEmpty())
    }

    @Test
    fun dropsATileWhenItsCellLeavesTheRadius() = runTest {
        val world = World()
        val grid = grid()
        val system = WorldPartitionSystem(
            config,
            asyncStreamListener = NavGridCellStreamer(grid, config, { flatCell() }),
            loadScope = this,
        )
        val observer = world.observerAt(4f, 4f)
        system.update(world, 0f)
        testScheduler.advanceUntilIdle()
        system.applyLoadedCells(world)
        assertTrue(grid.residentCells.isNotEmpty())

        world.add(observer, Transform(position = Vec3f(900f, 0f, 900f)))
        system.update(world, 0f)
        testScheduler.advanceUntilIdle()

        assertEquals(false, WorldCellCoord(0, 0) in grid.residentCells, "${grid.residentCells}")
    }

    /** A bake that finishes after its cell is gone must not publish a tile nobody can reach. */
    @Test
    fun aBakeThatOutlivesItsCellIsDiscarded() = runTest {
        val world = World()
        val grid = grid()
        val system = WorldPartitionSystem(
            config,
            asyncStreamListener = NavGridCellStreamer(grid, config, { flatCell() }),
            loadScope = this,
        )
        val observer = world.observerAt(4f, 4f)

        system.update(world, 0f)
        world.add(observer, Transform(position = Vec3f(900f, 0f, 900f)))
        system.update(world, 0f)
        testScheduler.advanceUntilIdle()
        system.applyLoadedCells(world)

        // The cells around the observer's new position are legitimately resident; the one it left
        // is what must not be, even though its bake finished after the move.
        assertEquals(
            false,
            WorldCellCoord(0, 0) in grid.residentCells,
            "${grid.residentCells}",
        )
    }

    /** Terrain nobody authored is not walkable, and must not be a crash either. */
    @Test
    fun aCellWithNoTerrainLoadsNoTile() = runTest {
        val world = World()
        val grid = grid()
        val system = WorldPartitionSystem(
            config,
            asyncStreamListener = NavGridCellStreamer(grid, config, { null }),
            loadScope = this,
        )
        world.observerAt(4f, 4f)

        system.update(world, 0f)
        testScheduler.advanceUntilIdle()
        system.applyLoadedCells(world)

        assertEquals(emptySet(), grid.residentCells)
    }

    /**
     * The mismatch the navigation plan names as its main risk. Caught at construction, because
     * the symptom otherwise is a path that stops at a cell boundary with nothing to blame.
     */
    @Test
    fun rejectsAGridWhoseCellIsNotTheWorldsCell() {
        val grid = StreamedNavGrid(samplesPerCell = 7, sampleSize = 1f)

        val failure = assertFailsWith<IllegalArgumentException> {
            NavGridCellStreamer(grid, config, { flatCell() })
        }

        assertTrue(failure.message.orEmpty().contains("8.0m"), failure.message)
    }
}
