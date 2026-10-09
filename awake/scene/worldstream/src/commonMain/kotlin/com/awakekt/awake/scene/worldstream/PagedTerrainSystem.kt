/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.worldstream

import com.awakekt.awake.asset.terrain.Heightmap
import com.awakekt.awake.asset.terrain.TerrainPageCoord
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.world.findWorldOrigin
import com.awakekt.awake.terrain.TerrainPageStreamer

/**
 * Binds Core page residency to a scene observer and optional static heightfield entities. Add
 * before PhysicsSystem. PhysicsSystem and the scene's origin listener own live body movement.
 * Attach the terrain content feature once through ContentFeatureHost, independently of this system.
 */
class PagedTerrainSystem(
    /** Core residency and bounded asset-reader lifecycle. */
    val streamer: TerrainPageStreamer,
    private val observer: () -> Vec3f,
    private val physicsSystem: PhysicsSystem? = null,
    /** Cell radius with static collision around the observer. */
    val collisionRadius: Int = 1,
) : System {
    private data class Collider(val entity: Entity, val height: Heightmap, val owners: Int, val fallback: Heightmap) {
        fun matches(height: Heightmap, owners: Int, fallback: Heightmap): Boolean =
            this.height === height && this.owners == owners && this.fallback === fallback
    }
    private val colliders = mutableMapOf<TerrainPageCoord, Collider>()
    init {
        require(collisionRadius in 0..streamer.radius)
        if (physicsSystem != null) require(streamer.terrain.layout.samplesPerPage >= 4)
    }

    override fun update(world: World, delta: Float) {
        val origin = world.findWorldOrigin()
        val terrain = streamer.terrain
        terrain.originX = (origin?.stepX?.toDouble() ?: 0.0) * (origin?.quantum ?: 0f)
        terrain.originY = (origin?.stepY?.toDouble() ?: 0.0) * (origin?.quantum ?: 0f)
        terrain.originZ = (origin?.stepZ?.toDouble() ?: 0.0) * (origin?.quantum ?: 0f)
        val camera = observer()
        val x = camera.x.toDouble() + terrain.originX
        val z = camera.z.toDouble() + terrain.originZ
        streamer.update(x, z)
        if (physicsSystem == null) return
        val cellSize = terrain.layout.cellSize
        val cx = kotlin.math.floor(x / cellSize).toLong()
        val cz = kotlin.math.floor(z / cellSize).toLong()
        val wanted = terrain.residentCoords.filter { kotlin.math.abs(it.x.toLong() - cx) <= collisionRadius && kotlin.math.abs(it.z.toLong() - cz) <= collisionRadius }.toSet()
        for (coord in colliders.keys.toList()) if (coord !in wanted) retire(world, coord)
        for (coord in wanted) {
            val height = requireNotNull(terrain.page(coord)).height
            val owners = edgeOwners(coord)
            val fallback = terrain.heights.fallback
            if (colliders[coord]?.matches(height, owners, fallback) == true) continue
            // Replace the entity on a deformation; no background result can resurrect an unloaded body.
            retire(world, coord)
            val entity = world.create()
            world.add(
                entity,
                Transform(
                    position = Vec3f(
                        ((coord.x.toDouble() + 0.5) * cellSize - terrain.originX).toFloat(),
                        (-terrain.originY).toFloat(),
                        ((coord.z.toDouble() + 0.5) * cellSize - terrain.originZ).toFloat(),
                    ),
                ),
            )
            world.add(entity, PhysicsBody(HeightFieldShape(colliderSamples(coord, height), height.width, height.scale), motionType = MotionType.STATIC))
            colliders[coord] = Collider(entity, height, owners, fallback)
        }
    }

    /** Which positive-side neighbours are resident; they own this cell's far edge knots. */
    private fun edgeOwners(coord: TerrainPageCoord): Int {
        val terrain = streamer.terrain
        var owners = 0
        if (terrain.page(TerrainPageCoord(coord.x + 1, coord.z)) != null) owners = owners or 1
        if (terrain.page(TerrainPageCoord(coord.x, coord.z + 1)) != null) owners = owners or 2
        if (terrain.page(TerrainPageCoord(coord.x + 1, coord.z + 1)) != null) owners = owners or 4
        return owners
    }

    /**
     * The page's samples with its far column and row read from their owners, as the clipmap and
     * height queries read them: the coarse fallback while that neighbour is absent or loading.
     */
    private fun colliderSamples(coord: TerrainPageCoord, height: Heightmap): FloatArray {
        val heights = streamer.terrain.heights
        val layout = heights.layout
        val n = layout.intervals
        val firstX = (coord.x - layout.minCellX) * n
        val firstZ = (coord.z - layout.minCellZ) * n
        val samples = height.copySamples()
        for (i in 0..n) {
            samples[i * (n + 1) + n] = heights.sample(firstX + n, firstZ + i)
            samples[n * (n + 1) + i] = heights.sample(firstX + i, firstZ + n)
        }
        return samples
    }

    /** True only after the scene physics system has created this resident cell's real body. */
    fun collisionReady(world: World, coord: TerrainPageCoord): Boolean = colliders[coord]?.let { world.get<PhysicsBody>(it.entity)?.handle != null } ?: false

    /** Stops streaming and removes all terrain collision entities and bodies. */
    fun close(world: World) {
        streamer.close()
        colliders.keys.toList().forEach { retire(world, it) }
    }

    private fun retire(world: World, coord: TerrainPageCoord) {
        val collider = colliders.remove(coord) ?: return
        physicsSystem?.destroyBody(world, collider.entity)
        if (world.isAlive(collider.entity)) world.destroy(collider.entity)
    }
}
