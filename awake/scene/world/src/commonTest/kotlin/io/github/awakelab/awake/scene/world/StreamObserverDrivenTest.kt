/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.world

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `update` used to be an empty body, so adding this system to a scene did nothing until something
 * called `updateObserverPosition` by hand -- and nothing did. These cover the driving itself
 * rather than the grid maths, which `WorldPartitionSystemTest` already owns.
 */
class StreamObserverDrivenTest {

    private class RecordingListener : WorldCellStreamListener {
        val loaded = mutableListOf<WorldCellCoord>()
        val unloaded = mutableListOf<WorldCellCoord>()
        override fun onCellLoad(world: World, coord: WorldCellCoord) {
            loaded += coord
        }
        override fun onCellUnload(world: World, coord: WorldCellCoord) {
            unloaded += coord
        }
    }

    private val config = WorldPartitionConfig(
        cellSize = 256f,
        loadingRadius = 512f,
        unloadRadius = 768f,
    )

    private fun observerAt(world: World, position: Vec3f) = world.create().also { entity ->
        world.add(entity, Transform(position = position))
        world.add(entity, StreamObserver)
    }

    @Test
    fun anObserverEntityDrivesStreamingWithNoExternalCall() {
        val world = World()
        val listener = RecordingListener()
        val system = WorldPartitionSystem(config, listener)
        observerAt(world, Vec3f(0f, 0f, 0f))

        system.update(world, delta = 0.016f)

        assertTrue(system.activeCells.isNotEmpty(), "The observer's own cell should have loaded.")
        assertTrue(listener.loaded.contains(WorldCellCoord(0, 0)))
    }

    @Test
    fun movingTheObserverStreamsTheNewRegionInAndTheOldOneOut() {
        val world = World()
        val listener = RecordingListener()
        val system = WorldPartitionSystem(config, listener)
        val observer = observerAt(world, Vec3f(0f, 0f, 0f))
        system.update(world, delta = 0.016f)
        listener.loaded.clear()

        // Far enough that the origin cell falls outside the unload radius.
        world.add(observer, Transform(position = Vec3f(4000f, 0f, 0f)))
        system.update(world, delta = 0.016f)

        assertTrue(listener.loaded.isNotEmpty(), "Cells around the new position should load.")
        assertTrue(
            listener.unloaded.contains(WorldCellCoord(0, 0)),
            "The origin cell is now far past the unload radius and should have been released.",
        )
        assertTrue(WorldCellCoord(0, 0) !in system.activeCells)
    }

    /** A scene that has not opted into streaming should cost nothing, not stream around 0,0. */
    @Test
    fun aWorldWithNoObserverStreamsNothing() {
        val world = World()
        val listener = RecordingListener()
        val system = WorldPartitionSystem(config, listener)
        world.create().also { world.add(it, Transform(position = Vec3f(0f, 0f, 0f))) }

        system.update(world, delta = 0.016f)

        assertEquals(0, system.activeCells.size)
        assertTrue(listener.loaded.isEmpty())
    }

    /** The tag alone is not enough -- without a Transform there is no position to stream around,
     * and treating that as the origin would quietly load the wrong region. */
    @Test
    fun anObserverWithoutATransformIsIgnored() {
        val world = World()
        val listener = RecordingListener()
        val system = WorldPartitionSystem(config, listener)
        world.create().also { world.add(it, StreamObserver) }

        system.update(world, delta = 0.016f)

        assertTrue(listener.loaded.isEmpty())
    }
}
