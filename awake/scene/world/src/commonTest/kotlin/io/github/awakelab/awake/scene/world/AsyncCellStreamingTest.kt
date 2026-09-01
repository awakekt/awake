/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.world

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.Name
import io.github.awakelab.awake.scene.core.transform.Transform
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cancellation is the hard part of async streaming, not the dispatch. Every failure here is a
 * ghost entity or a leaked job rather than a crash, so each case is asserted directly.
 */
class AsyncCellStreamingTest {

    /** Loads block until released, so a test can hold a cell mid-flight and move the observer. */
    private class GatedLoader : AsyncWorldCellStreamListener {
        val started = mutableListOf<WorldCellCoord>()
        val unloaded = mutableListOf<WorldCellCoord>()
        val applied = mutableListOf<WorldCellCoord>()
        private val gate = CompletableDeferred<Unit>()

        override suspend fun loadCell(coord: WorldCellCoord): CellContent {
            started += coord
            gate.await()
            return CellContent { applied += coord }
        }

        override fun onCellUnload(world: World, coord: WorldCellCoord) {
            unloaded += coord
        }

        fun release() = gate.complete(Unit)
    }

    private val config = WorldPartitionConfig(
        cellSize = 256f,
        loadingRadius = 512f,
        unloadRadius = 768f,
    )

    private fun World.observerAt(position: Vec3f) = create().also {
        add(it, Transform(position = position))
        add(it, StreamObserver)
    }

    @Test
    fun aLoadThatFinishesWhileItsCellIsActiveIsApplied() = runTest {
        val world = World()
        val loader = GatedLoader()
        val system = WorldPartitionSystem(config, asyncStreamListener = loader, loadScope = this)
        world.observerAt(Vec3f(0f, 0f, 0f))

        system.update(world, delta = 0f)
        // launch() only schedules on a TestScope; the body runs when the scheduler does.
        testScheduler.runCurrent()
        assertTrue(loader.started.isNotEmpty(), "The observer's cells should have started loading.")
        assertTrue(loader.applied.isEmpty(), "Nothing may be applied before the load returns.")

        loader.release()
        testScheduler.advanceUntilIdle()
        system.applyLoadedCells(world)

        assertTrue(loader.applied.contains(WorldCellCoord(0, 0)))
    }

    /** The case that leaves ghosts: the cell is gone by the time its content arrives. */
    @Test
    fun contentForACellThatLeftTheRadiusIsDiscarded() = runTest {
        val world = World()
        val loader = GatedLoader()
        val system = WorldPartitionSystem(config, asyncStreamListener = loader, loadScope = this)
        val observer = world.observerAt(Vec3f(0f, 0f, 0f))

        system.update(world, delta = 0f)
        testScheduler.runCurrent()
        // Move far away while every load is still gated.
        world.add(observer, Transform(position = Vec3f(9000f, 0f, 0f)))
        system.update(world, delta = 0f)
        assertTrue(loader.unloaded.contains(WorldCellCoord(0, 0)), "The origin cell should unload.")

        loader.release()
        testScheduler.advanceUntilIdle()
        system.applyLoadedCells(world)

        assertTrue(
            !loader.applied.contains(WorldCellCoord(0, 0)),
            "Content arrived for a cell the observer had already left and was applied anyway.",
        )
    }

    /** Re-entering must load once more, not replay the cancelled attempt or start two. */
    @Test
    fun reEnteringACellStartsExactlyOneFreshLoad() = runTest {
        val world = World()
        val loader = GatedLoader()
        val system = WorldPartitionSystem(config, asyncStreamListener = loader, loadScope = this)
        val observer = world.observerAt(Vec3f(0f, 0f, 0f))

        system.update(world, delta = 0f)
        testScheduler.runCurrent()
        val firstAttempts = loader.started.count { it == WorldCellCoord(0, 0) }
        world.add(observer, Transform(position = Vec3f(9000f, 0f, 0f)))
        system.update(world, delta = 0f)
        testScheduler.runCurrent()
        world.add(observer, Transform(position = Vec3f(0f, 0f, 0f)))
        system.update(world, delta = 0f)
        testScheduler.runCurrent()

        assertEquals(
            firstAttempts + 1,
            loader.started.count { it == WorldCellCoord(0, 0) },
            "Re-entry should start one more load: not zero, and not a duplicate alongside the " +
                "cancelled one.",
        )

        // runTest fails a test that leaves coroutines running, and every load here is still
        // gated. Releasing is also the honest end state: the cancelled ones stay cancelled.
        loader.release()
        testScheduler.advanceUntilIdle()
    }

    /** A consumer with no async listener keeps the inline path it had. */
    @Test
    fun theSynchronousListenerStillRunsInline() {
        val world = World()
        val loaded = mutableListOf<WorldCellCoord>()
        val listener = object : WorldCellStreamListener {
            override fun onCellLoad(world: World, coord: WorldCellCoord) { loaded += coord }
            override fun onCellUnload(world: World, coord: WorldCellCoord) = Unit
        }
        val system = WorldPartitionSystem(config, streamListener = listener)
        world.observerAt(Vec3f(0f, 0f, 0f))

        system.update(world, delta = 0f)

        assertTrue(loaded.contains(WorldCellCoord(0, 0)), "No scope means the inline path.")
    }

    /** Applying really does reach the World, not just the listener's own bookkeeping. */
    @Test
    fun appliedContentMutatesTheWorldOnTheFrameThread() = runTest {
        val world = World()
        val listener = object : AsyncWorldCellStreamListener {
            override suspend fun loadCell(coord: WorldCellCoord) = CellContent { w ->
                w.add(w.create(), Name("cell-${coord.x}-${coord.z}"))
            }
            override fun onCellUnload(world: World, coord: WorldCellCoord) = Unit
        }
        val system = WorldPartitionSystem(config, asyncStreamListener = listener, loadScope = this)
        world.observerAt(Vec3f(0f, 0f, 0f))

        system.update(world, delta = 0f)
        testScheduler.advanceUntilIdle()
        system.applyLoadedCells(world)

        var named = 0
        world.family<Name>().forEach { _, _ -> named++ }
        assertTrue(named > 0, "Applied cell content should have created entities.")
    }
}
