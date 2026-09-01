/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.rendering.streaming

import io.github.awakelab.awake.core.geometry.MeshGeometry
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.render.mesh.Mesh
import io.github.awakelab.awake.render.testing.NoopRenderer
import io.github.awakelab.awake.scene.core.transform.Transform
import io.github.awakelab.awake.scene.rendering.mesh.MeshRenderer
import io.github.awakelab.awake.scene.world.WorldCellCoord
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A streamed mesh fails by outliving its cell — a leaked GPU buffer and an entity drawing terrain
 * that is no longer loaded — or by never arriving. Both are silent, so each is asserted here.
 */
class MeshCellStreamerTest {

    /** Counts destroys, which is the half of the lifecycle nothing else can observe. */
    private class CountingRenderer : NoopRenderer() {
        var created = 0
            private set
        var destroyed = 0
            private set

        override fun createMesh(geometry: MeshGeometry): Mesh {
            created++
            return object : Mesh {
                override val format = geometry.format
                override val sizeBytes: Long = 0
                override fun destroy() {
                    destroyed++
                }
            }
        }
    }

    private val renderer = CountingRenderer()
    private val material = renderer.createMaterial()

    private fun geometry() = MeshGeometry(
        vertices = FloatArray(VertexFormat.PositionNormalColor.strideBytes / Float.SIZE_BYTES),
        indices = intArrayOf(0),
        format = VertexFormat.PositionNormalColor,
    )

    private fun streamer(geometryFor: suspend (WorldCellCoord) -> MeshGeometry? = { geometry() }) =
        MeshCellStreamer(renderer, material, cellSize = 16f, geometryFor = geometryFor)

    @Test
    fun aLoadedCellSpawnsAMeshAtThatCellsCentre() = runTest {
        val world = World()
        val streamer = streamer()

        streamer.loadCell(WorldCellCoord(2, -1)).applyTo(world)

        var found = 0
        world.family<Transform, MeshRenderer>().forEach { _, transform, _ ->
            found++
            // Cell (2, -1) at 16m spans x 32..48 and z -16..0, so its CENTRE is (40, _, -8).
            // Corner would put this position outside the geometry it belongs to, which is what
            // LOD distance and culling then measure.
            assertEquals(40f, transform.position.x)
            assertEquals(-8f, transform.position.z)
        }
        assertEquals(1, found, "One cell should spawn exactly one mesh entity.")
        assertEquals(1, renderer.created)
    }

    /** The upload is the frame-thread half: suspending must not touch the GPU. */
    @Test
    fun nothingIsUploadedUntilTheContentIsApplied() = runTest {
        val streamer = streamer()

        streamer.loadCell(WorldCellCoord(0, 0))

        assertEquals(0, renderer.created, "createMesh ran off the frame thread.")
    }

    /**
     * The entity goes at once; the mesh waits. Destroying it with the entity is a use-after-free —
     * the GPU is still reading the command buffer that refers to it — which is what the Vulkan
     * validation layer caught the first time this streamer ran for real.
     */
    @Test
    fun unloadingDropsTheEntityAndRetiresTheMesh() = runTest {
        val world = World()
        val streamer = streamer()
        val coord = WorldCellCoord(0, 0)
        streamer.loadCell(coord).applyTo(world)

        streamer.onCellUnload(world, coord)

        var remaining = 0
        world.family<MeshRenderer>().forEach { _, _ -> remaining++ }
        assertEquals(0, remaining, "The entity should go immediately.")
        assertEquals(0, renderer.destroyed, "The mesh must outlive the frames that may draw it.")
        assertEquals(1, streamer.retiringMeshCount)
        assertTrue(streamer.residentCells.isEmpty())
    }

    @Test
    fun aRetiredMeshIsDestroyedOnceTheFramesThatCouldDrawItHavePassed() = runTest {
        val world = World()
        val streamer = MeshCellStreamer(renderer, material, cellSize = 16f, retireFrames = 2) { geometry() }
        val coord = WorldCellCoord(0, 0)
        streamer.loadCell(coord).applyTo(world)
        streamer.onCellUnload(world, coord)

        streamer.update(world, delta = 0f)
        assertEquals(0, renderer.destroyed, "One frame is not enough with retireFrames = 2.")
        streamer.update(world, delta = 0f)

        assertEquals(1, renderer.destroyed)
        assertEquals(0, streamer.retiringMeshCount)
    }

    /** Teardown may stall, and leaking every streamed mesh until device destruction may not. */
    @Test
    fun disposeDestroysEverythingImmediately() = runTest {
        val world = World()
        val streamer = streamer()
        streamer.loadCell(WorldCellCoord(0, 0)).applyTo(world)
        streamer.loadCell(WorldCellCoord(1, 0)).applyTo(world)

        streamer.dispose(world)

        assertEquals(2, renderer.destroyed)
        assertEquals(0, streamer.retiringMeshCount)
        assertTrue(streamer.residentCells.isEmpty())
    }

    /** Unloading a cell this streamer never spawned must not destroy someone else's entity. */
    @Test
    fun unloadingAnUnknownCellDoesNothing() = runTest {
        val world = World()
        val streamer = streamer()
        streamer.loadCell(WorldCellCoord(0, 0)).applyTo(world)

        streamer.onCellUnload(world, WorldCellCoord(9, 9))

        assertEquals(0, renderer.destroyed)
        assertEquals(setOf(WorldCellCoord(0, 0)), streamer.residentCells)
    }

    /** Ocean, void, a region nobody authored: an ordinary answer, not an error. */
    @Test
    fun aCellWithNoGeometryDrawsNothing() = runTest {
        val world = World()
        val streamer = streamer { null }

        streamer.loadCell(WorldCellCoord(0, 0)).applyTo(world)

        assertEquals(0, renderer.created)
        assertTrue(streamer.residentCells.isEmpty())
    }

    /** A second load without an unload would otherwise leak the first mesh and double-draw. */
    @Test
    fun reloadingACellReplacesWhatItHad() = runTest {
        val world = World()
        val streamer = streamer()
        val coord = WorldCellCoord(1, 1)
        streamer.loadCell(coord).applyTo(world)

        streamer.loadCell(coord).applyTo(world)

        assertEquals(2, renderer.created)
        assertEquals(1, streamer.retiringMeshCount, "The replaced mesh was leaked rather than retired.")
        var entities = 0
        world.family<MeshRenderer>().forEach { _, _ -> entities++ }
        assertEquals(1, entities)
        assertNotNull(streamer.residentCells.singleOrNull())
    }
}
