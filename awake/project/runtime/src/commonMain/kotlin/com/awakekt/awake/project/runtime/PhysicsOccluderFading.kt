/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.camera.OccluderFade
import com.awakekt.awake.core.camera.OccluderFader
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.CollisionLayers
import com.awakekt.awake.physics.HeightFieldShape
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraMode
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.PhysicsBody
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.DitherFade
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlin.math.sqrt

/**
 * Fades what stands between the active third-person camera and its target, for a rig whose
 * `occluderFade` asks: each frame it casts rays from the eye to the target's feet, chest and head
 * against static bodies, and every body hit, other than terrain, is an occluder. An [OccluderFader]
 * eases each occluder's opacity, and this system writes it as a [DitherFade] on every mesh at or under
 * the occluder's node, removing the ones it added once the occluder is opaque again.
 *
 * A played project runs it after the camera when the scene has a physics world and a camera rig. A
 * host that runs its own systems adds it after its camera system, passing its [PhysicsSystem], which
 * knows which node each body is for.
 *
 * @param physics The world the rays are cast in.
 * @param bodies The system that built the scene's bodies, for which node a body is.
 */
class PhysicsOccluderFading(private val physics: PhysicsWorld, private val bodies: PhysicsSystem) : System {
    private val fader = OccluderFader<Entity>()
    private val fadedMeshes = HashSet<Entity>()
    private val stillFaded = HashSet<Entity>()
    private val point = Vec3f()
    private val origin = Vec3f()
    private val direction = Vec3f()

    /** How many occluders are faded, partly or fully. */
    val occluders: Int get() = fader.size

    override fun update(world: World, delta: Float) {
        var settings: OccluderFade? = null
        world.queryEach(Camera::class, CameraRig::class) { entity, camera, rig ->
            val fade = rig.occluderFade
            val target = rig.targetEntity?.let { world.get<Transform>(it) }
            if (fade != null && target != null && rig.mode == CameraMode.ThirdPerson && world.has(entity, ActiveCamera::class)) {
                settings = fade
                findOccluders(world, camera.lens.eye, target, rig)
            }
        }
        val active = settings
        if (active == null) {
            if (fader.size > 0) fader.clear()
        } else {
            fader.step(active, delta)
        }
        applyFades(world)
    }

    /** Reports every occluder on the rays from [eye] to [target]'s feet, chest and head. */
    private fun findOccluders(world: World, eye: Vec3f, target: Transform, rig: CameraRig) {
        val base = target.position
        for (share in TARGET_HEIGHTS) {
            point.set(rig.offsetPosition).scale(share).add(base)
            castThrough(world, eye, point, rig.targetEntity)
        }
    }

    /** Casts from [from] to [to], reporting each static body passed through, up to [MAX_HITS_PER_RAY]. */
    private fun castThrough(world: World, from: Vec3f, to: Vec3f, target: Entity?) {
        origin.set(from)
        direction.set(to).sub(from)
        var remaining = sqrt(direction.x * direction.x + direction.y * direction.y + direction.z * direction.z)
        if (remaining <= 0f) return
        direction.scale(1f / remaining)
        var hits = 0
        while (hits < MAX_HITS_PER_RAY && remaining > 0f) {
            val hit = physics.raycast(origin, direction, remaining, CollisionLayers.World) ?: break
            val owner = bodies.entityFor(hit.handle)
            if (owner != null && owner != target && world.get<PhysicsBody>(owner)?.shape !is HeightFieldShape) fader.blocking(owner)
            val step = hit.distance + STEP_PAST
            origin.set(direction).scale(step).add(origin)
            remaining -= step
            hits++
        }
    }

    /** Writes each faded occluder's opacity on its meshes, and takes it off the meshes no longer faded. */
    private fun applyFades(world: World) {
        stillFaded.clear()
        if (fader.size > 0) {
            world.queryEach(MeshRenderer::class) { mesh, _ ->
                val opacity = fadeAbove(world, mesh)
                if (opacity < 1f) {
                    stillFaded += mesh
                    // Added after the query: a component added mid-iteration could move the mesh under it.
                    world.get<DitherFade>(mesh)?.opacity = opacity
                }
            }
        }
        for (mesh in stillFaded) {
            if (world.get<DitherFade>(mesh) == null) world.add(mesh, DitherFade(fadeAbove(world, mesh)))
        }
        for (mesh in fadedMeshes) {
            if (mesh !in stillFaded && world.isAlive(mesh)) world.remove<DitherFade>(mesh)
        }
        fadedMeshes.clear()
        fadedMeshes += stillFaded
    }

    /** The opacity of the faded occluder at or above [node], or `1` for none. */
    private fun fadeAbove(world: World, node: Entity): Float {
        var at: Entity? = node
        var steps = 0
        var opacity = 1f
        while (at != null && opacity >= 1f && steps++ < MAX_DEPTH) {
            opacity = fader.opacityOf(at)
            at = world.get<Transform>(at)?.parent
        }
        return opacity
    }

    private companion object {
        // The target's feet, chest and head, as shares of the rig's offset above it.
        val TARGET_HEIGHTS = floatArrayOf(0.1f, 0.6f, 1f)
        const val MAX_HITS_PER_RAY = 4

        // How far past a hit the next ray starts, so it does not find the same face again.
        const val STEP_PAST = 0.01f
        const val MAX_DEPTH = 64
    }
}
