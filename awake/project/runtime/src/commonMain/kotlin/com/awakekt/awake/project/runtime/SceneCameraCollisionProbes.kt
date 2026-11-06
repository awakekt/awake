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
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.scene.controls.camera.CameraCollisionProbes
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent
import kotlin.math.sqrt

/**
 * Answers a played scene's camera collision: the ground is the highest of its `terrain` heightmaps
 * and its `paged_terrain` at a position, and an obstacle is any static body a sphere swept through
 * [physics] touches. A host with no physics world still keeps the eye above the terrain.
 *
 * Only static bodies ([CollisionLayer.World]) stop the camera, so the player's own character and the
 * things moving around it never pull the eye in. Nothing here allocates per call.
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

    override val ground: CameraGround = CameraGround(::groundAt)

    override val obstruction: CameraObstruction? = physics?.let { CameraObstruction(::clearFraction) }

    override fun prepare(world: World) {
        this.world = world
    }

    private fun groundAt(x: Float, z: Float): Float {
        var highest = pagedHeights?.heightAtWorld(x.toDouble(), z.toDouble()) ?: Float.NaN
        world?.queryEach(TerrainComponent::class) { _, terrain ->
            val height = terrain.heightmap.heightAtWorld(x, z)
            if (!height.isNaN() && (highest.isNaN() || height > highest)) highest = height
        }
        return highest
    }

    private fun clearFraction(from: Vec3f, to: Vec3f, radius: Float): Float {
        val physics = physics ?: return 1f
        return if (radius > 0f) sweep(physics, from, to, radius) else cast(physics, from, to)
    }

    /** How far a sphere of [radius] gets from [from] to [to] before it touches a static body. */
    private fun sweep(physics: PhysicsWorld, from: Vec3f, to: Vec3f, radius: Float): Float {
        val shape = sphere?.takeIf { it.radius == radius } ?: SphereShape(radius).also { sphere = it }
        return physics.shapeCast(shape, from, to, onlyLayer = CollisionLayer.World)?.fraction ?: 1f
    }

    /** The same for a ray, for a camera that asks for no radius. */
    private fun cast(physics: PhysicsWorld, from: Vec3f, to: Vec3f): Float {
        ray.set(to).sub(from)
        val length = sqrt(ray.x * ray.x + ray.y * ray.y + ray.z * ray.z)
        val hit = if (length > 0f) physics.raycast(from, ray.scale(1f / length), length, CollisionLayer.World) else null
        return hit?.let { it.distance / length } ?: 1f
    }
}
