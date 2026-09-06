/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples.streaming

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.world.AsyncWorldCellStreamListener
import com.awakekt.awake.scene.world.CellContent
import com.awakekt.awake.scene.world.WorldCellCoord

/** A cell's collider sits at the cell's centre, matching `MeshCellStreamer`. */
private const val HALF = 0.5f

/**
 * Gives every streamed cell a collider, and takes it back when the cell leaves.
 *
 * Without this an open world holds every collider it has ever seen: bodies are created once and
 * never removed, so the population only grows and a world of any size walks into the backend's
 * body cap. Streaming them is what makes the world's size a function of the view distance rather
 * than of how far the player has walked.
 *
 * The same split as `MeshCellStreamer`, and forced by the same constraint. [shapeFor] runs off the
 * frame thread with no world in reach and returns a shape *description*; the body is created on
 * the frame thread inside the returned [CellContent]. Generating a cell's terrain samples is the
 * expensive half in practice and it is pure arithmetic over arrays, which is exactly what belongs
 * off-thread.
 *
 * ponytail: Jolt's own shape build still happens on the frame thread, inside `createBody`. Jolt
 * can cook a shape on any thread, but `PhysicsWorld` has no separate cook step to call -- adding
 * one means a new method on four backends, and it is worth doing only once a profile says the
 * cook rather than the sample generation is what costs.
 *
 * A body is spawned as an entity carrying [PhysicsBody] rather than created here directly, so
 * `PhysicsSystem` owns its creation exactly as it does for authored bodies -- one lifecycle, and
 * the collider shows up in `physicsDebugLines` like any other. Unloading destroys the body first
 * and then the entity, because nothing else will: `PhysicsSystem` does not notice an entity
 * disappearing, so an entity removed on its own leaves its body simulating forever.
 *
 * [shapeFor] returning null means the cell has no collision -- ocean, void, an unauthored region --
 * which is an ordinary answer and not an error.
 */
class PhysicsCellStreamer(
    private val physicsWorld: PhysicsWorld,
    private val cellSize: Float,
    private val layer: CollisionLayer = CollisionLayers.World,
    private val motionType: MotionType = MotionType.STATIC,
    private val shapeFor: suspend (WorldCellCoord) -> PhysicsShape?,
) : AsyncWorldCellStreamListener {

    init {
        require(cellSize > 0f && cellSize.isFinite()) { "cellSize must be positive and finite: $cellSize" }
    }

    /** What each resident cell spawned, so unloading takes back exactly that. */
    // LinkedHashMap and LinkedHashSet, not the hash forms: teardown iterates these, and
    // destruction order decides which body ids Jolt hands back out next, which feeds its
    // island ordering and so the simulation. Insertion order replays; hash order does not.
    private val spawned = LinkedHashMap<WorldCellCoord, Entity>()

    /** The cells with a live collider right now. For tests and diagnostics. */
    val residentCells: Set<WorldCellCoord> get() = spawned.keys

    override suspend fun loadCell(coord: WorldCellCoord): CellContent {
        val shape = shapeFor(coord) ?: return CellContent { }
        return CellContent { world -> spawn(world, coord, shape) }
    }

    override fun onCellUnload(world: World, coord: WorldCellCoord) {
        retire(world, coord)
    }

    /**
     * Rebuilds the colliders of cells whose source data has changed -- a deformed heightmap, a
     * destroyed wall.
     *
     * The same two-phase split as [loadCell], and for the same reason: the shapes are rebuilt here,
     * off the frame thread, and the returned [CellContent] swaps the bodies on it. Doing both at
     * once would put shape generation back on the frame thread, which is what the split exists to
     * prevent.
     *
     * **Only cells that are currently resident.** A caller invalidating a region does not know
     * which of it is streamed in, and rebuilding a cell that is not would spawn a collider for
     * terrain the player is nowhere near -- one that no unload will ever take back, because no load
     * ever claimed it.
     *
     * A cell whose shape has since become null is dropped rather than rebuilt: terrain flattened
     * out of existence should lose its collider, not keep a stale one.
     */
    suspend fun reloadCells(coords: Collection<WorldCellCoord>): CellContent {
        val rebuilt = coords.filter { it in spawned }.map { coord -> coord to shapeFor(coord) }
        return CellContent { world ->
            rebuilt.forEach { (coord, shape) ->
                if (shape == null) retire(world, coord) else spawn(world, coord, shape)
            }
        }
    }

    /** Destroys every streamed collider. For teardown, where leaving bodies behind leaks them. */
    fun dispose(world: World) {
        spawned.keys.toList().forEach { coord -> retire(world, coord) }
    }

    /**
     * Replaces rather than accumulates: a cell that loads twice without an unload between would
     * otherwise leave two colliders in the same place, and only one of them reachable to remove.
     */
    private fun spawn(world: World, coord: WorldCellCoord, shape: PhysicsShape) {
        retire(world, coord)
        val half = cellSize * HALF
        val entity = world.create()
        world.add(
            entity,
            Transform(position = Vec3f(coord.x * cellSize + half, 0f, coord.z * cellSize + half)),
        )
        world.add(entity, PhysicsBody(shape = shape, motionType = motionType, layer = layer))
        spawned[coord] = entity
    }

    private fun retire(world: World, coord: WorldCellCoord) {
        val entity = spawned.remove(coord) ?: return
        // The body first. Destroying the entity alone would leave a collider in the world with
        // nothing left holding its handle -- invisible, un-removable, and still solid.
        world.get<PhysicsBody>(entity)?.handle?.let(physicsWorld::destroyBody)
        world.destroy(entity)
    }
}
