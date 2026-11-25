/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.ComponentTypeId
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.EntityList
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.MeshCollider
import com.awakekt.awake.scene.physics.PhysicsSystem
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.terrain.TerrainComponent

/**
 * Opt-in observer-driven static collider streamer for large open worlds.
 *
 * Register this system in the fixed simulation phase before [TerrainColliderSystem],
 * [com.awakekt.awake.scene.physics.MeshColliderSystem], and [PhysicsSystem]. On the first tick
 * with a valid observer position it parks distant static colliders before the collider systems
 * build their native physics shapes, and on subsequent ticks it destroys bodies that leave range
 * and restores parked colliders as the observer approaches:
 *
 * - **Static terrains (`TerrainComponent.collider`)**: distant terrains have their
 *   [com.awakekt.awake.scene.physics.PhysicsBody] destroyed via [PhysicsSystem.destroyBody] and
 *   removed, and their [TerrainComponent.collider] flag parked to `false` while keeping
 *   [TerrainComponent.heightmap] intact everywhere for rendering and height queries.
 * - **Static meshes (`MeshCollider`)**: distant static mesh colliders have their
 *   [com.awakekt.awake.scene.physics.PhysicsBody] destroyed and removed, and their [MeshCollider]
 *   component parked off the entity so `MeshColliderSystem` does not build them. When a parked
 *   mesh streams back into range, its [MeshCollider] is re-attached and `onMeshActivated` (when
 *   supplied) is notified before `MeshColliderSystem` runs on the same tick.
 *
 * Headless servers and small scenes that need all static colliders resident everywhere simply do
 * not register this system; [TerrainColliderSystem] and `MeshColliderSystem` remain unchanged.
 *
 * Call [close] when the scene stops to clear all internal tracking tables and retained references.
 *
 * @param physics Physics system that owns creation and destruction of native bodies for this world.
 * @param config Activation/deactivation distances and observer movement step.
 * @param observer Resolves the current observer world position on each tick, or `null` when no
 *   observer is ready yet. Defaults to [defaultObserver] (the primary [Camera]'s lens eye).
 * @param onMeshActivated Optional callback invoked for each previously parked mesh collider entity
 *   after its [MeshCollider] component is restored when streaming back into range.
 */
class StaticColliderStreamSystem(
    physics: PhysicsSystem,
    config: ColliderStreamConfig = ColliderStreamConfig(),
    private val observer: (World) -> Vec3f? = defaultObserver,
    private val onMeshActivated: ((Entity) -> Unit)? = null,
) : System,
    AutoCloseable {
    private val moveStepSq = config.moveStep * config.moveStep
    private val originScratch = Vec3f()
    private val activatedMeshes = EntityList()
    private val terrainTracker = TerrainColliderTracker(physics, config, originScratch)
    private val meshTracker = MeshColliderTracker(physics, config, originScratch)

    private var hasLastObserver = false
    private var lastObserverX = 0f
    private var lastObserverZ = 0f
    private var syncedWorld: World? = null
    private var syncedGeneration = -1
    private var syncedTerrainChanges = -1
    private var syncedMeshChanges = -1
    private var transformTypeId = ComponentTypeId(0)
    private var closed = false

    /** Total static terrain colliders currently tracked (active and parked). */
    val trackedTerrainCount: Int get() = terrainTracker.trackedCount

    /** Number of static terrain colliders currently parked out of physics. */
    val parkedTerrainCount: Int get() = terrainTracker.parkedCount

    /** Total static mesh colliders currently tracked (active and parked). */
    val trackedMeshCount: Int get() = meshTracker.trackedCount

    /** Number of static mesh colliders currently parked out of physics. */
    val parkedMeshCount: Int get() = meshTracker.parkedCount

    override fun update(world: World, delta: Float) {
        if (closed) return
        val focus = observer(world) ?: return

        val worldChanged = syncedWorld !== world || syncedGeneration != world.generation
        if (worldChanged) resetForWorld(world)

        val terrainFamily = world.family<TerrainComponent>()
        val meshFamily = world.family<Transform, MeshCollider>()
        val terrainsChanged = worldChanged || syncedTerrainChanges != terrainFamily.changes
        val meshesChanged = worldChanged || syncedMeshChanges != meshFamily.changes

        val prevTerrains = terrainTracker.trackedCount
        val prevMeshes = meshTracker.trackedCount
        if (terrainsChanged) terrainTracker.discover(terrainFamily)
        if (meshesChanged) meshTracker.discover(meshFamily)

        val moved = stepTrackers(world, focus.x, focus.z, prevTerrains, prevMeshes, terrainsChanged, meshesChanged)
        if (moved || terrainsChanged || meshesChanged) {
            syncedWorld = world
            syncedGeneration = world.generation
            syncedTerrainChanges = terrainFamily.changes
            syncedMeshChanges = meshFamily.changes
        }
        flushActivatedMeshes()
    }

    /** Clears all internal tracking lists, sets, and cached references. Closing twice is harmless. */
    override fun close() {
        if (closed) return
        closed = true
        terrainTracker.clear()
        meshTracker.clear()
        activatedMeshes.clear()
        hasLastObserver = false
        lastObserverX = 0f
        lastObserverZ = 0f
        syncedWorld = null
        syncedGeneration = -1
        syncedTerrainChanges = -1
        syncedMeshChanges = -1
    }

    private fun resetForWorld(world: World) {
        terrainTracker.clear()
        meshTracker.clear()
        activatedMeshes.clear()
        hasLastObserver = false
        syncedTerrainChanges = -1
        syncedMeshChanges = -1
        transformTypeId = world.typeId(Transform::class)
    }

    @Suppress("LongParameterList") // Local state unpacked from update to keep cyclomatic complexity low.
    private fun stepTrackers(
        world: World,
        obsX: Float,
        obsZ: Float,
        prevTerrains: Int,
        prevMeshes: Int,
        terrainsChanged: Boolean,
        meshesChanged: Boolean,
    ): Boolean {
        val dx = obsX - lastObserverX
        val dz = obsZ - lastObserverZ
        val movedEnough = !hasLastObserver || dx * dx + dz * dz >= moveStepSq
        val activated = if (onMeshActivated != null) activatedMeshes else null
        if (movedEnough) {
            lastObserverX = obsX
            lastObserverZ = obsZ
            hasLastObserver = true
            terrainTracker.evaluate(world, startIndex = 0, obsX, obsZ, transformTypeId)
            meshTracker.evaluate(world, startIndex = 0, obsX, obsZ, transformTypeId, activated)
        } else {
            if (terrainsChanged) {
                terrainTracker.evaluate(world, startIndex = prevTerrains, obsX, obsZ, transformTypeId)
                terrainTracker.prune(world, limit = prevTerrains)
            }
            if (meshesChanged) {
                meshTracker.evaluate(world, startIndex = prevMeshes, obsX, obsZ, transformTypeId, activated)
                meshTracker.prune(world, limit = prevMeshes)
            }
        }
        return movedEnough
    }

    private fun flushActivatedMeshes() {
        val callback = onMeshActivated
        if (activatedMeshes.isEmpty || callback == null) {
            activatedMeshes.clear()
            return
        }
        try {
            for (index in 0 until activatedMeshes.size) {
                callback(activatedMeshes[index])
            }
        } finally {
            activatedMeshes.clear()
        }
    }

    /** Default observer provider for [StaticColliderStreamSystem]. */
    companion object {
        /** Default observer that resolves the primary [Camera]'s lens eye position in [World]. */
        val defaultObserver: (World) -> Vec3f? = ::primaryCameraEye

        private fun primaryCameraEye(world: World): Vec3f? {
            val family = world.family<Camera>()
            val cameras = family.components()
            for (index in 0 until family.size) {
                val camera = cameras[index]
                if (camera.isPrimary) return camera.lens.eye
            }
            return null
        }
    }
}

internal fun resolveStaticOrigin(world: World, entity: Entity, transformTypeId: ComponentTypeId, out: Vec3f): Vec3f {
    val transform = world.get<Transform>(entity, transformTypeId)
    return when {
        transform == null -> out.set(0f, 0f, 0f)
        transform.parent == null -> out.set(transform.position)
        transform.worldVersion > 0 -> {
            val placed = transform.worldMatrix
            out.set(placed.m03, placed.m13, placed.m23)
        }
        else -> {
            var x = transform.position.x
            var y = transform.position.y
            var z = transform.position.z
            var parent = transform.parent
            var depth = 0
            while (parent != null && depth < MAX_STATIC_PARENT_DEPTH) {
                val above = world.get<Transform>(parent, transformTypeId)
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
            out.set(x, y, z)
        }
    }
}

private const val MAX_STATIC_PARENT_DEPTH = 64
