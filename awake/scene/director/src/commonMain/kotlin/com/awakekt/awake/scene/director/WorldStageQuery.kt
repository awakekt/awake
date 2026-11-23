/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.director

import com.awakekt.awake.core.camera.CameraGround
import com.awakekt.awake.core.camera.CameraObstruction
import com.awakekt.awake.core.camera.CameraOptics
import com.awakekt.awake.core.math.Aabb
import com.awakekt.awake.core.math.ArcLengthSpline3f
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.director.DirectedCameraPose
import com.awakekt.awake.director.StageQuery
import com.awakekt.awake.director.StageRayHit
import com.awakekt.awake.director.StageSubjectState
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.CameraCollisionProbes
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.mesh.MeshBounds
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Optional sightline raycaster callback answering whether geometry blocks a ray from [from] to [to].
 */
fun interface StageSightlineProbe {
    /** Optional per-frame world binding called by [WorldStageQuery.prepare]. */
    fun prepare(world: World) {}

    /**
     * Casts a ray from [from] to [to], ignoring [ignoreEntity] when non-null, writing into [outHit].
     *
     * @return `true` if the ray hit an occluder before reaching [to].
     */
    fun cast(from: Vec3f, to: Vec3f, ignoreEntity: Entity?, outHit: StageRayHit): Boolean

    /** No-op probe reporting every sightline unobstructed. */
    companion object {
        /** Unobstructed sightline probe. */
        val None: StageSightlineProbe = StageSightlineProbe { _, _, _, out ->
            out.reset()
            false
        }
    }
}

/**
 * ECS-backed implementation of [StageQuery] translating scene entities, `Transform` hierarchies,
 * `MeshBounds`, `CameraRailComponent`, `VirtualCameraComponent`, terrain heights, and sightline
 * probes for `:awake:director`.
 *
 * Maintains a lightweight per-frame entity name cache and previous-frame position map so velocity
 * look-ahead and subject lookups stay fast without allocating inside `System.update`.
 *
 * @param collisionProbes Ground and obstacle collision probes shared with `CameraSystem`.
 * @param sightlineProbe Optional physics sightline raycaster for occlusion telemetry.
 */
class WorldStageQuery(
    private val collisionProbes: CameraCollisionProbes = CameraCollisionProbes.None,
    private val sightlineProbe: StageSightlineProbe = StageSightlineProbe.None,
) : StageQuery {
    private var currentWorld: World? = null
    private val namedEntities = HashMap<String, Entity>()
    private val prevPositions = HashMap<Int, Vec3f>()
    private val velocities = HashMap<Int, Vec3f>()
    private val scratchAabb = Aabb(Vec3f(0f, 0f, 0f), Vec3f(0f, 0f, 0f))
    private val scratchPos = Vec3f()
    private val scratchForward = Vec3f()
    private val scratchRight = Vec3f()

    override val cameraGround: CameraGround? get() = collisionProbes.ground
    override val cameraObstruction: CameraObstruction? get() = collisionProbes.obstruction

    /**
     * Refreshes entity name lookup and velocity estimates once per frame before [DirectorPlayer.step].
     *
     * @param world Active ECS world.
     * @param deltaSeconds Elapsed step duration in seconds (`> 0f` updates velocity estimates).
     */
    fun prepare(world: World, deltaSeconds: Float) {
        currentWorld = world
        collisionProbes.prepare(world)
        sightlineProbe.prepare(world)
        namedEntities.clear()
        world.queryEach(Name::class) { entity, name ->
            if (name.value.isNotBlank() && !namedEntities.containsKey(name.value)) {
                namedEntities[name.value] = entity
            }
            val transform = world.get<Transform>(entity)
            if (transform != null) {
                readWorldPosition(transform, scratchPos)
                val key = entity.id
                val prev = prevPositions[key]
                if (prev != null && deltaSeconds > MIN_DT) {
                    val vel = velocities.getOrPut(key) { Vec3f(0f, 0f, 0f) }
                    val invDt = 1f / deltaSeconds
                    vel.set(
                        (scratchPos.x - prev.x) * invDt,
                        (scratchPos.y - prev.y) * invDt,
                        (scratchPos.z - prev.z) * invDt,
                    )
                    prev.set(scratchPos)
                } else if (prev == null) {
                    prevPositions[key] = Vec3f(scratchPos.x, scratchPos.y, scratchPos.z)
                }
            }
        }
    }

    /** Finds the entity named [name] from the last [prepare] scan, or `null` if absent. */
    fun findEntity(name: String): Entity? = namedEntities[name]

    override fun readSubject(name: String, out: StageSubjectState): Boolean {
        val world = currentWorld
        val entity = if (world != null) namedEntities[name] else null
        val transform = if (world != null && entity != null) world.get<Transform>(entity) else null
        if (world == null || entity == null || transform == null) return false

        out.found = true
        readWorldPosition(transform, out.position)

        val bounds = world.get<MeshBounds>(entity)
        val marker = world.get<StageMarkerComponent>(entity)
        if (bounds != null) {
            bounds.writeWorldBounds(transform, scratchAabb)
            out.boundsMin.set(scratchAabb.min)
            out.boundsMax.set(scratchAabb.max)
        } else if (marker != null) {
            val r = marker.authored.radius
            out.boundsMin.set(out.position.x - r, out.position.y, out.position.z - r)
            out.boundsMax.set(out.position.x + r, out.position.y + r * 2f, out.position.z + r)
        } else {
            out.boundsMin.set(out.position.x - FALLBACK_HALF_WIDTH, out.position.y, out.position.z - FALLBACK_HALF_WIDTH)
            out.boundsMax.set(out.position.x + FALLBACK_HALF_WIDTH, out.position.y + FALLBACK_HEIGHT, out.position.z + FALLBACK_HALF_WIDTH)
        }

        readWorldForward(transform, out.forward)
        val vel = velocities[entity.id]
        if (vel != null) out.velocity.set(vel) else out.velocity.set(0f, 0f, 0f)
        return true
    }

    override fun readSpline(name: String): ArcLengthSpline3f? {
        val world = currentWorld
        val entity = if (world != null) namedEntities[name] else null
        return if (world != null && entity != null) world.get<CameraRailComponent>(entity)?.spline else null
    }

    override fun readNamedCamera(name: String, out: DirectedCameraPose): Boolean {
        val world = currentWorld
        val entity = if (world != null) namedEntities[name] else null
        var found = false
        if (world != null && entity != null) {
            val vcam = world.get<VirtualCameraComponent>(entity)
            val cam = if (vcam == null) world.get<Camera>(entity) else null
            if (vcam != null) {
                val authored = vcam.authored
                val transform = world.get<Transform>(entity)
                if (authored.eye != null) {
                    out.eye.set(authored.eye.x, authored.eye.y, authored.eye.z)
                } else if (transform != null) {
                    readWorldPosition(transform, out.eye)
                } else {
                    out.eye.set(0f, FALLBACK_HEIGHT, DEFAULT_EYE_Z)
                }
                out.center.set(authored.center.x, authored.center.y, authored.center.z)
                out.fovYRadians = authored.lens.resolvedFovYRadians()
                out.near = authored.lens.near
                out.far = authored.lens.far
                out.rollRadians = authored.lens.rollDeg * DEG_TO_RAD
                CameraOptics.cameraBasis(out.eye, out.center, out.rollRadians, scratchForward, scratchRight, out.up)
                found = true
            } else if (cam != null) {
                out.eye.set(cam.lens.eye)
                out.center.set(cam.lens.center)
                out.up.set(cam.lens.up)
                out.fovYRadians = cam.lens.fovYRadians
                out.near = cam.lens.near
                out.far = cam.lens.far
                out.rollRadians = 0f
                found = true
            }
        }
        return found
    }

    override fun groundHeight(x: Float, z: Float): Float = collisionProbes.ground?.heightAt(x, z) ?: Float.NaN

    override fun castSightline(
        from: Vec3f,
        to: Vec3f,
        ignoreEntityName: String?,
        outHit: StageRayHit,
    ): Boolean {
        val ignored = ignoreEntityName?.let(namedEntities::get)
        return sightlineProbe.cast(from, to, ignored, outHit)
    }

    private fun readWorldPosition(transform: Transform, out: Vec3f): Vec3f =
        if (transform.parent != null && transform.worldVersion > 0) {
            out.set(transform.worldMatrix.m03, transform.worldMatrix.m13, transform.worldMatrix.m23)
        } else {
            out.set(transform.position)
        }

    private fun readWorldForward(transform: Transform, out: Vec3f): Vec3f {
        if (transform.worldVersion > 0) {
            val m = transform.worldMatrix
            // Local -Z forward column transformed by worldMatrix.
            out.set(-m.m02, -m.m12, -m.m22)
            if (out.length3() > MIN_LEN) {
                return out.normalize()
            }
        }
        val yaw = transform.rotation.y
        return out.set(sin(yaw), 0f, -cos(yaw)).normalize()
    }

    private companion object {
        const val MIN_DT = 1e-5f
        const val MIN_LEN = 1e-5f
        const val FALLBACK_HALF_WIDTH = 0.45f
        const val FALLBACK_HEIGHT = 1.8f
        const val DEFAULT_EYE_Z = 6f
        const val DEG_TO_RAD = (PI / 180.0).toFloat()
    }
}
