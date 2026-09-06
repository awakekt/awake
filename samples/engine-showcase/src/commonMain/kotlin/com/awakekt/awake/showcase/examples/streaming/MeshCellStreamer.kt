/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.examples.streaming

import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.renderer.CullMode
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.world.AsyncWorldCellStreamListener
import com.awakekt.awake.scene.world.CellContent
import com.awakekt.awake.scene.world.WorldCellCoord

/**
 * Gives every streamed cell a mesh: built off the frame thread, uploaded and spawned on it.
 *
 * The split is the whole design, and it is forced rather than chosen. Building geometry is
 * arithmetic over arrays and belongs off the frame thread; `createMesh` talks to the GPU device
 * and the `World` gets an entity, and neither is thread-safe. So [geometryFor] runs in the
 * suspending half with no renderer and no world in reach, and the upload and spawn run in the
 * [CellContent] the partition system applies on the frame thread.
 *
 * Geometry is built in the cell's **local** space — its own CENTRE is the origin — and the entity
 * carries a [Transform] at the cell's centre. Centre rather than corner so that a cell's position
 * is where the cell is: LOD distance, culling spheres and origin rebasing all read that position,
 * and a corner puts it most of a cell away from anything drawn. Building in world space would work
 * until a
 * world large enough to matter loses float precision far from the origin, which is a bug that
 * shows up as jittering terrain a long way into a play session and nowhere near this code.
 *
 * [geometryFor] returning null means the cell has nothing to draw, which is an ordinary answer for
 * ocean, void, or a region nobody authored — no entity is spawned and unloading it does nothing.
 *
 * The mesh is destroyed [retireFrames] frames *after* its cell unloads, not with it. Freeing it
 * immediately is a use-after-free the validation layer catches and a driver crash it does not: the
 * GPU is still reading last frame's command buffer, which still refers to that mesh. This is the
 * same hazard the Vulkan UI mesh growth path already had to fix, solved here without a device
 * stall — [update] retires meshes on a delay instead of `waitIdle` blocking the frame that
 * happened to cross a streaming boundary.
 *
 * One mesh per cell, with exactly one holder. Cells that *share* a mesh want
 * [SceneAssetLibrary][com.awakekt.awake.scene.runtime.SceneAssetLibrary] instead, which
 * refcounts and can retain a released mesh against a budget — destroying a shared mesh on the
 * first unload is precisely the bug it exists to prevent.
 */
class MeshCellStreamer(
    private val renderer: Renderer,
    private val material: Material,
    private val cellSize: Float,
    private val cullMode: CullMode = CullMode.Back,
    /**
     * Frames an unloaded mesh is kept alive before being destroyed.
     *
     * ponytail: a fixed count rather than a fence, because the scene layer cannot see a backend's
     * frames-in-flight. Three covers double and triple buffering; raise it, or replace it with a
     * real fence handed down from the backend, if a validation layer ever complains again.
     */
    private val retireFrames: Int = DEFAULT_RETIRE_FRAMES,
    private val geometryFor: suspend (WorldCellCoord) -> MeshGeometry?,
) : AsyncWorldCellStreamListener,
    System {

    init {
        require(cellSize > 0f && cellSize.isFinite()) { "cellSize must be positive and finite; was $cellSize." }
        require(retireFrames >= 0) { "retireFrames must not be negative; was $retireFrames." }
    }

    /** What each resident cell spawned, so unloading can take exactly that back. */
    private val spawned = HashMap<WorldCellCoord, CellMesh>()

    /** Unloaded meshes the GPU may still be reading. Drained by [update]. */
    private val retiring = ArrayList<RetiringMesh>()

    /** The cells with a live mesh right now. For tests and diagnostics. */
    val residentCells: Set<WorldCellCoord> get() = spawned.keys

    /** Meshes unloaded but not yet destroyed. Non-zero for a few frames after any unload. */
    val retiringMeshCount: Int get() = retiring.size

    /**
     * Destroys whatever has outlived the frames that could still reference it.
     *
     * Register this alongside the streaming system — it is a `System` for exactly that reason, so
     * a consumer does not have to remember a second per-frame call with a name of its own.
     */
    override fun update(world: World, delta: Float) {
        var index = 0
        while (index < retiring.size) {
            val entry = retiring[index]
            entry.framesLeft--
            if (entry.framesLeft > 0) {
                index++
            } else {
                entry.mesh.destroy()
                retiring.removeAt(index)
            }
        }
    }

    /**
     * Destroys everything immediately, waiting for the GPU first.
     *
     * For teardown, where a stall costs nothing and leaking every streamed mesh until device
     * destruction costs real memory. [update] is the per-frame path; this is the end of one.
     */
    fun dispose(world: World) {
        spawned.keys.toList().forEach { coord -> retire(world, coord) }
        renderer.waitIdle()
        retiring.forEach { it.mesh.destroy() }
        retiring.clear()
    }

    override suspend fun loadCell(coord: WorldCellCoord): CellContent {
        val geometry = geometryFor(coord) ?: return CellContent { }
        return CellContent { world -> spawn(world, coord, geometry) }
    }

    /**
     * Replaces rather than accumulates: a cell that somehow loads twice without an unload between
     * would otherwise leak its first mesh and leave two entities drawing in the same place.
     */
    private fun spawn(world: World, coord: WorldCellCoord, geometry: MeshGeometry) {
        retire(world, coord)
        val mesh = renderer.createMesh(geometry)
        val entity = world.create()
        val half = cellSize * HALF
        world.add(
            entity,
            Transform(position = Vec3f(coord.x * cellSize + half, 0f, coord.z * cellSize + half)),
        )
        world.add(entity, MeshRenderer(mesh = mesh, material = material, cullMode = cullMode))
        spawned[coord] = CellMesh(entity, mesh)
    }

    override fun onCellUnload(world: World, coord: WorldCellCoord) {
        retire(world, coord)
    }

    /**
     * Takes back everything this streamer created for [coord], and nothing it did not.
     *
     * The entity goes now — an entity is the ECS's to free and nothing else refers to it — while
     * its mesh waits out the frames that may still be drawing it.
     */
    private fun retire(world: World, coord: WorldCellCoord) {
        val existing = spawned.remove(coord) ?: return
        world.destroy(existing.entity)
        retiring += RetiringMesh(existing.mesh, retireFrames.coerceAtLeast(1))
    }

    private class CellMesh(val entity: Entity, val mesh: Mesh)

    private class RetiringMesh(val mesh: Mesh, var framesLeft: Int)

    private companion object {
        /** Covers double and triple buffering, which is every backend here. */
        const val DEFAULT_RETIRE_FRAMES = 3
        private const val HALF = 0.5f
    }
}
