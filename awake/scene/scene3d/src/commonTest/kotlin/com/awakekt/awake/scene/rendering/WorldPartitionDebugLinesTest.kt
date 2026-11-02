/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.diagnostics.DebugLines
import com.awakekt.awake.core.diagnostics.DebugRegistry
import com.awakekt.awake.core.diagnostics.DebugRows
import com.awakekt.awake.core.diagnostics.DebugSource
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.renderer.LineSegment
import com.awakekt.awake.render.testing.NoopRenderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.debug.DebugLineBuffer
import com.awakekt.awake.scene.rendering.debug.DebugVisualizationSystem
import com.awakekt.awake.scene.world.StreamObserver
import com.awakekt.awake.scene.world.WorldPartitionSystem
import com.awakekt.awake.world.WorldPartitionConfig
import com.awakekt.awake.world.WorldPartitionDebugSource
import com.awakekt.awake.world.WorldPartitionDebugSource.Companion.CELL_LINES
import com.awakekt.awake.world.WorldPartitionDebugSource.Companion.CIRCLE_SEGMENTS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** The world partition's debug source, fed by a real [WorldPartitionSystem], through the hosts that draw sources. */
class WorldPartitionDebugLinesTest {
    private val config = WorldPartitionConfig(cellSize = 10f, loadingRadius = 10f, unloadRadius = 20f)

    @Test
    fun steadyFramesReuseEveryCellAndCircleSegment() {
        val world = World()
        val partition = WorldPartitionSystem(config)
        world.observerAt(Vec3f(5f, 0f, 5f))
        val registry = DebugRegistry(setOf(WorldPartitionDebugSource.ID))
        registry.register(WorldPartitionDebugSource(partition.probe))
        val buffer = DebugLineBuffer()

        partition.update(world, FRAME)
        buffer.clear()
        buffer.addSources(registry)
        val first = buffer.lines.toList()
        partition.update(world, FRAME)
        buffer.clear()
        buffer.addSources(registry)

        assertEquals(partition.activeCells.size * CELL_LINES + 2 * CIRCLE_SEGMENTS, first.size)
        assertEquals(first.size, buffer.lines.size)
        for (i in first.indices) assertSame(first[i], buffer.lines[i], "segment $i is reused, not made again")
    }

    @Test
    fun theCellsAndCirclesReachTheRendererInOneCall() {
        val world = World()
        val partition = WorldPartitionSystem(config)
        world.observerAt(Vec3f(5f, 0f, 5f))
        val registry = DebugRegistry(setOf(WorldPartitionDebugSource.ID))
        registry.register(WorldPartitionDebugSource(partition.probe))
        val renderer = RecordingRenderer()

        partition.update(world, FRAME)
        DebugVisualizationSystem(renderer, { null }, registry).update(world, FRAME)

        assertEquals(1, renderer.calls)
        assertEquals(partition.activeCells.size * CELL_LINES + 2 * CIRCLE_SEGMENTS, renderer.last?.size)
    }

    @Test
    fun theSourceOffIsNeverCalledAndNothingIsDrawn() {
        val world = World()
        val partition = WorldPartitionSystem(config)
        world.observerAt(Vec3f(5f, 0f, 5f))
        val registry = DebugRegistry(setOf("net"))
        val counted = CountingSource(WorldPartitionDebugSource(partition.probe))
        registry.register(counted)
        val renderer = RecordingRenderer()

        partition.update(world, FRAME)
        DebugVisualizationSystem(renderer, { null }, registry).update(world, FRAME)

        assertEquals(0, counted.calls)
        assertEquals(0, renderer.calls)
        assertEquals(partition.activeCells.size, partition.probe.cellCount, "the probe still counts")
    }

    private fun World.observerAt(position: Vec3f) = create().also {
        add(it, Transform(position = position))
        add(it, StreamObserver)
    }

    private class RecordingRenderer : NoopRenderer() {
        var calls = 0
        var last: List<LineSegment>? = null

        override fun drawDebugLines(lines: List<LineSegment>) {
            calls++
            last = lines.toList()
        }
    }

    private class CountingSource(private val source: DebugSource) : DebugSource by source {
        var calls = 0

        override fun rows(out: DebugRows) {
            calls++
            source.rows(out)
        }

        override fun lines(out: DebugLines) {
            calls++
            source.lines(out)
        }
    }

    private companion object {
        const val FRAME = 1f / 60f
    }
}
