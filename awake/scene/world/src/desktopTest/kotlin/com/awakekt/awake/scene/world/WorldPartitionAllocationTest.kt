/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.world

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.world.WorldCellCoord
import com.awakekt.awake.world.WorldPartitionConfig
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A frame that loads and unloads nothing allocates nothing in [WorldPartitionSystem]'s scan, with the
 * observer still and with it moving inside the cells it has already loaded.
 */
class WorldPartitionAllocationTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun aStillObserversFrameAllocatesNothing() {
        val (world, system) = streaming()
        val observer = Vec3f(100f, 0f, 100f)

        assertEquals(0L, cheapestWindow { system.updateObserverPosition(world, observer) })
    }

    @Test
    fun anObserverMovingInsideItsLoadedCellsAllocatesNothing() {
        val (world, system) = streaming()
        val observer = Vec3f(100f, 0f, 100f)
        var step = 0
        val move = {
            // A 20 m circle: its first lap loads the few cells it reaches, every later lap loads nothing.
            val angle = (step++ % 64) * (Math.PI * 2 / 64)
            observer.set(100f + 20f * kotlin.math.cos(angle).toFloat(), 0f, 100f + 20f * kotlin.math.sin(angle).toFloat())
            system.updateObserverPosition(world, observer)
        }
        repeat(64) { move() }
        val loadedAfterOneLap = system.activeCells.size

        assertEquals(0L, cheapestWindow(move))
        assertEquals(loadedAfterOneLap, system.activeCells.size, "later laps load and unload nothing")
    }

    private fun streaming(): Pair<World, WorldPartitionSystem> {
        val world = World()
        val system = WorldPartitionSystem(
            config = WorldPartitionConfig(cellSize = 64f, loadingRadius = 256f, unloadRadius = 384f),
            streamListener = object : WorldCellStreamListener {
                override fun onCellLoad(world: World, coord: WorldCellCoord) = Unit

                override fun onCellUnload(world: World, coord: WorldCellCoord) = Unit
            },
        )
        system.updateObserverPosition(world, Vec3f(100f, 0f, 100f))
        return world to system
    }

    /**
     * Bytes allocated by the cheapest of [WINDOWS] windows of [FRAMES] frames each, after a warm-up.
     * A per-frame allocation fills every window; one that only overlaps the JIT finishing does not.
     */
    private fun cheapestWindow(frame: () -> Unit): Long {
        repeat(WARMUP_FRAMES) { frame() }
        var cheapest = Long.MAX_VALUE
        repeat(WINDOWS) {
            val before = threadBean.currentThreadAllocatedBytes
            repeat(FRAMES) { frame() }
            cheapest = minOf(cheapest, threadBean.currentThreadAllocatedBytes - before)
        }
        return cheapest
    }

    private companion object {
        const val WARMUP_FRAMES = 5_000
        const val WINDOWS = 5
        const val FRAMES = 500
    }
}
