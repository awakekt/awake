/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.terrain.PagedHeightmap
import com.awakekt.awake.core.camera.CameraGround
import com.awakekt.awake.core.camera.CameraObstruction
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.controls.camera.CameraCollisionProbes
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import kotlin.math.sqrt

/**
 * Answers a played scene's camera collision: the ground is the highest of its `terrain` heightmaps
 * (each placed at its entity's `Transform` translation) and its `paged_terrain` at a position, which
 * the solver uses both to lift the eye and to pull it in front of a rise; an obstacle is a body on a
 * layer the rig names that a sphere swept through [physics] touches. A host with no physics world
 * still keeps the eye out of the terrain.
 *
 * Nothing here allocates per call.
 *
 * @param physics The scene's physics world, or `null` when the host gives none.
 * @param pagedHeights The streamed terrain's heights, or `null` when the scene streams none.
 */
internal class SceneCameraCollisionProbes(
    private val physics: PhysicsWorld?,
    private val pagedHeights: PagedHeightmap?,
) : CameraCollisionProbes {
    private var world: World? = null
    private var sphere: SphereShape? = null
    private val ray = Vec3f()
    private val origin = Vec3f()

    override val ground: CameraGround = CameraGround(::groundAt)

    override val obstruction: CameraObstruction? = physics?.let { CameraObstruction(::clearFraction) }

    override fun prepare(world: World) {
        this.world = world
    }

    private fun groundAt(x: Float, z: Float): Float {
        var highest = pagedHeights?.heightAtWorld(x.toDouble(), z.toDouble()) ?: Float.NaN
        val currentWorld = world ?: return highest
        currentWorld.queryEach<TerrainComponent> { entity, terrain ->
            terrainOrigin(currentWorld, entity, origin)
            val local = terrain.heightmap.heightAtWorld(x - origin.x, z - origin.z)
            if (!local.isNaN()) {
                val height = local + origin.y
                if (highest.isNaN() || height > highest) highest = height
            }
        }
        return highest
    }

    /**
     * Writes into [out] the translation [entity]'s terrain lies at in [world], matching
     * `TerrainContentSystem`: a child whose `worldMatrix` `TransformSystem` has built reads that
     * matrix's translation, and one not yet built sums local positions up its parent chain.
     */
    private fun terrainOrigin(world: World, entity: Entity, out: Vec3f): Vec3f {
        val transform = world.get<Transform>(entity)
        return when {
            transform == null -> out.set(0f, 0f, 0f)
            transform.parent == null -> out.set(transform.position)
            transform.worldVersion > 0 -> {
                val placed = transform.worldMatrix
                out.set(placed.m03, placed.m13, placed.m23)
            }
            else -> sumParentPositions(world, transform, out)
        }
    }

    private fun sumParentPositions(world: World, start: Transform, out: Vec3f): Vec3f {
        var x = start.position.x
        var y = start.position.y
        var z = start.position.z
        var parent = start.parent
        var depth = 0
        while (parent != null && depth < MAX_PARENT_DEPTH) {
            val above = world.get<Transform>(parent)
            if (above == null) {
                parent = null
            } else if (above.parent != null && above.worldVersion > 0) {
                val placed = above.worldMatrix
                x += placed.m03
                y += placed.m13
                z += placed.m23
                parent = null
            } else {
                x += above.position.x
                y += above.position.y
                z += above.position.z
                parent = above.parent
                depth++
            }
        }
        return out.set(x, y, z)
    }

    private fun clearFraction(from: Vec3f, to: Vec3f, radius: Float, layer: Int): Float {
        val physics = physics ?: return 1f
        val only = CollisionLayer(layer)
        return if (radius > 0f) sweep(physics, from, to, radius, only) else cast(physics, from, to, only)
    }

    /** How far a sphere of [radius] gets from [from] to [to] before it touches a body on [layer]. */
    private fun sweep(physics: PhysicsWorld, from: Vec3f, to: Vec3f, radius: Float, layer: CollisionLayer): Float {
        val shape = sphere?.takeIf { it.radius == radius } ?: SphereShape(radius).also { sphere = it }
        return physics.shapeCast(shape, from, to, onlyLayer = layer)?.fraction ?: 1f
    }

    /** The same for a ray, for a camera that asks for no radius. */
    private fun cast(physics: PhysicsWorld, from: Vec3f, to: Vec3f, layer: CollisionLayer): Float {
        ray.set(to).sub(from)
        val length = sqrt(ray.x * ray.x + ray.y * ray.y + ray.z * ray.z)
        val hit = if (length > 0f) physics.raycast(from, ray.scale(1f / length), length, layer) else null
        return hit?.let { it.distance / length } ?: 1f
    }

    private companion object {
        const val MAX_PARENT_DEPTH = 64
    }
}
