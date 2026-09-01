/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.scene.physics.streaming

import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.ecs.World
import io.github.awakelab.awake.physics.HeightFieldShape
import io.github.awakelab.awake.scene.physics.PhysicsBody
import io.github.awakelab.awake.scene.physics.PhysicsSystem
import io.github.awakelab.awake.scene.world.WorldCellCoord
import kotlinx.coroutines.test.runTest
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Streaming a large heightfield as per-cell tiles, and rebuilding those tiles when it is deformed.
 *
 * Two failures worth pinning. A cell size that disagrees with the tile extent offsets every
 * collider from its terrain -- uniformly, so it looks like the whole world is subtly wrong rather
 * than like a configuration mistake. And a deform on a shared edge sample that rebuilds only one
 * side leaves a seam that used to line up standing as a wall exactly where the player just dug.
 */
class HeightFieldCellStreamingTest {

    private val width = 13
    private val tileSamples = 4
    private val scale = Vec3f(2f, 1f, 2f)
    private var heights = FloatArray(width * width) { 1f }

    private fun streamer(physics: RecordingPhysicsWorld) = heightFieldCellStreamer(
        physicsWorld = physics,
        width = width,
        depth = width,
        scale = scale,
        tileSamples = tileSamples,
    ) { heights }

    private suspend fun PhysicsCellStreamer.load(world: World, coord: WorldCellCoord) {
        loadCell(coord).applyTo(world)
    }

    private fun World.shapeOfSingleBody(): HeightFieldShape {
        var found: HeightFieldShape? = null
        queryEach(PhysicsBody::class) { _, body -> found = body.shape as? HeightFieldShape }
        return requireNotNull(found) { "no heightfield body in the world" }
    }

    @Test
    fun aStreamedTileSitsWhereItsCellDoes() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val streamer = streamer(physics)

        streamer.load(world, WorldCellCoord(1, 0))
        PhysicsSystem(physics).update(world, 1f / 60f)

        // Cell size is derived as (tileSamples - 1) * scale.x = 6, so cell 1's centre is at 9.
        // The tile is centred on its own extent, so this is exactly where its samples sit. A cell
        // size passed by hand is the thing that silently breaks this.
        val position = physics.positions.single()
        assertTrue(abs(position.x - 9f) < 1e-4f, "x=${position.x}, expected 6 + 3")
        assertTrue(abs(position.z - 3f) < 1e-4f, "z=${position.z}, expected 0 + 3")
    }

    @Test
    fun aStreamedTileCarriesItsOwnCornerOfTheField() = runTest {
        // A ramp along x, so a tile's samples identify which part of the field it came from.
        heights = FloatArray(width * width) { index -> (index % width).toFloat() }
        val physics = RecordingPhysicsWorld()
        val world = World()
        val streamer = streamer(physics)

        streamer.load(world, WorldCellCoord(1, 0))
        PhysicsSystem(physics).update(world, 1f / 60f)

        // Tile 1 starts at sample 1 * (4 - 1) = 3, not at 4: tiles share their edges.
        val shape = world.shapeOfSingleBody()
        assertEquals(3f, shape.heightAt(0, 0))
        assertEquals(6f, shape.heightAt(3, 0))
    }

    @Test
    fun aDeformRebuildsTheCellItTouched() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val streamer = streamer(physics)
        streamer.load(world, WorldCellCoord(0, 0))
        val system = PhysicsSystem(physics)
        system.update(world, 1f / 60f)
        val before = physics.liveBodies.single()

        heights = heights.copyOf().also { it[1 * width + 1] = 5f }
        streamer.reloadCells(tilesTouchedByEdit(1, 1, 1, 1, tileSamples)).applyTo(world)
        system.update(world, 1f / 60f)

        assertEquals(5f, world.shapeOfSingleBody().heightAt(1, 1), "the rebuilt tile is stale")
        // The old body has to go, not merely be forgotten: a collider nothing holds a handle to is
        // invisible, still solid, and unremovable.
        assertTrue(before in physics.destroyed, "the stale collider was left in the world")
        assertEquals(1, physics.liveBodies.size, "live bodies: ${physics.liveBodies}")
    }

    @Test
    fun anEditOnASharedEdgeRebuildsBothTiles() {
        // Sample 3 is the last of tile 0 and the first of tile 1. Rebuilding one leaves the seam
        // stepped, which is the deform-time form of the tiling trap.
        val touched = tilesTouchedByEdit(3, 0, 3, 0, tileSamples)

        assertEquals(setOf(WorldCellCoord(0, 0), WorldCellCoord(1, 0)), touched)
    }

    @Test
    fun anEditInsideOneTileRebuildsOnlyThatTile() {
        // The other half: over-invalidating turns every footstep into a rebuild of the neighbourhood.
        assertEquals(setOf(WorldCellCoord(0, 0)), tilesTouchedByEdit(1, 1, 2, 2, tileSamples))
    }

    @Test
    fun reloadingIgnoresCellsThatAreNotStreamedIn() = runTest {
        val physics = RecordingPhysicsWorld()
        val world = World()
        val streamer = streamer(physics)
        streamer.load(world, WorldCellCoord(0, 0))
        PhysicsSystem(physics).update(world, 1f / 60f)

        streamer.reloadCells(listOf(WorldCellCoord(5, 5))).applyTo(world)
        PhysicsSystem(physics).update(world, 1f / 60f)

        // A collider spawned for a cell nobody loaded would never be unloaded either: no load
        // claimed it, so no unload takes it back.
        assertEquals(setOf(WorldCellCoord(0, 0)), streamer.residentCells)
        assertEquals(1, physics.liveBodies.size, "live bodies: ${physics.liveBodies}")
    }
}
