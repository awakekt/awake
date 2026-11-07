/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.DegreesOfFreedom
import com.awakekt.awake.physics.MeshShape
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.defaultLayerFor
import com.awakekt.awake.scene.core.Name
import com.awakekt.awake.scene.core.transform.Transform

/**
 * A static body that collides with a model's triangles, waiting for [MeshColliderSystem] to build
 * it. A scene's `mesh` collision shape loads as this.
 *
 * @property mesh Project path of the model.
 * @property primitive Which primitive of the model to collide with; `null` merges them all.
 * @property layer Collision layer of the body it becomes.
 * @property degreesOfFreedom Allowed motion in world axes, retained when the body is built.
 */
data class MeshCollider(
    val mesh: String,
    val primitive: Int? = null,
    val layer: CollisionLayer = defaultLayerFor(MotionType.STATIC),
    val degreesOfFreedom: DegreesOfFreedom = DegreesOfFreedom.ALL,
)

/**
 * A body that collides with a convex hull of a model's vertices, waiting for [MeshColliderSystem] to
 * build it. A scene's `convex_hull` collision shape loads as this.
 *
 * Unlike [MeshCollider], a convex hull encloses a volume so it supports any [MotionType] (including
 * dynamic props) and can act as a trigger [sensor].
 *
 * @property mesh Project path of the model.
 * @property primitive Which primitive of the model to collide with; `null` merges them all.
 * @property motion Motion type controlling whether the body is static, kinematic, or dynamic.
 * @property layer Collision layer of the body it becomes.
 * @property sensor Whether this body functions as a trigger sensor rather than a solid collider.
 * @property degreesOfFreedom Allowed motion in world axes, retained when the body is built.
 */
data class ConvexHullCollider(
    val mesh: String,
    val primitive: Int? = null,
    val motion: MotionType = MotionType.DYNAMIC,
    val layer: CollisionLayer = defaultLayerFor(motion),
    val sensor: Boolean = false,
    val degreesOfFreedom: DegreesOfFreedom = DegreesOfFreedom.ALL,
)

/** The triangles of a model, for [MeshColliderSystem]. */
fun interface CollisionMeshSource {
    /**
     * The triangles of [mesh] in the model's own space, unscaled, or `null` when they were not loaded.
     * Return the same shape for the same reference, so placements share it.
     *
     * @param mesh Project path of the model.
     * @param primitive Which primitive; `null` for all of them merged.
     */
    fun meshShape(mesh: String, primitive: Int?): MeshShape?
}

/**
 * Gives each [MeshCollider] and [ConvexHullCollider] a live [PhysicsBody] built from its model's
 * vertices, scaled by the node's `Transform.scale`. Register it before [PhysicsSystem], which places
 * the body by the same `Transform`'s position and rotation. Built once per entity: a later scale change
 * does not reach it.
 *
 * Only colliders still waiting for a body are visited (#1428). Each kind is a maintained family of
 * `Transform` and the collider that excludes [PhysicsBody], so an entity joins it when it gains a
 * collider and leaves it when its body is added. A step with nothing to build reads two sizes.
 *
 * Throws when [meshes] has no triangles for a collider, naming the model and the node.
 */
class MeshColliderSystem(private val meshes: CollisionMeshSource) : System {
    private var familiesWorld: World? = null
    private var familiesGeneration = -1
    private lateinit var pendingMeshes: Family
    private lateinit var pendingHulls: Family

    // Reused: adding a body takes the entity out of the family being read, so the batch is copied first.
    private var batch = LongArray(INITIAL_BATCH)

    override fun update(world: World, delta: Float) {
        resolveFamilies(world)
        if (pendingMeshes.size > 0) drain(pendingMeshes) { entity -> buildMesh(world, entity) }
        if (pendingHulls.size > 0) drain(pendingHulls) { entity -> buildHull(world, entity) }
    }

    private fun resolveFamilies(world: World) {
        if (familiesWorld === world && familiesGeneration == world.generation) return
        pendingMeshes = world.family { all(Transform::class, MeshCollider::class).exclude(PhysicsBody::class) }
        pendingHulls = world.family { all(Transform::class, ConvexHullCollider::class).exclude(PhysicsBody::class) }
        familiesWorld = world
        familiesGeneration = world.generation
    }

    private inline fun drain(family: Family, build: (Entity) -> Unit) {
        if (batch.size < family.size) batch = LongArray(family.size)
        var count = 0
        family.forEach { entity -> batch[count++] = entity.packed }
        for (index in 0 until count) build(Entity(batch[index]))
    }

    private fun buildMesh(world: World, entity: Entity) {
        val transform = world.get<Transform>(entity) ?: return
        val collider = world.get<MeshCollider>(entity) ?: return
        val node = world.get<Name>(entity)?.value ?: "entity ${entity.id}"
        val shape = checkNotNull(meshes.meshShape(collider.mesh, collider.primitive)) {
            "No collision mesh loaded for ${collider.mesh} on node $node"
        }
        val scale = transform.scale
        require(scale.x != 0f && scale.y != 0f && scale.z != 0f) {
            "Node $node has a zero scale, which flattens its collision mesh ${collider.mesh}"
        }
        world.add(
            entity,
            PhysicsBody(shape.scaledBy(scale), MotionType.STATIC, collider.layer, degreesOfFreedom = collider.degreesOfFreedom),
        )
    }

    private fun buildHull(world: World, entity: Entity) {
        val transform = world.get<Transform>(entity) ?: return
        val collider = world.get<ConvexHullCollider>(entity) ?: return
        val node = world.get<Name>(entity)?.value ?: "entity ${entity.id}"
        val mesh = checkNotNull(meshes.meshShape(collider.mesh, collider.primitive)) {
            "No collision mesh loaded for ${collider.mesh} on node $node"
        }
        val scale = transform.scale
        require(scale.x != 0f && scale.y != 0f && scale.z != 0f) {
            "Node $node has a zero scale, which flattens its collision hull ${collider.mesh}"
        }
        val scaledPoints = FloatArray(mesh.vertices.size) { index ->
            mesh.vertices[index] * when (index % 3) {
                0 -> scale.x
                1 -> scale.y
                else -> scale.z
            }
        }
        world.add(
            entity,
            PhysicsBody(
                shape = com.awakekt.awake.physics.ConvexHullShape(scaledPoints),
                motionType = collider.motion,
                layer = collider.layer,
                sensor = collider.sensor,
                degreesOfFreedom = collider.degreesOfFreedom,
            ),
        )
    }
}

/**
 * Scale is baked into the vertices because a body takes only a position and rotation. Applied in
 * model space, before the body's rotation, so any per-axis scale is exact.
 */
private fun MeshShape.scaledBy(scale: Vec3f): MeshShape {
    if (scale.x == 1f && scale.y == 1f && scale.z == 1f) return this
    val scaled = FloatArray(vertices.size) { index ->
        vertices[index] * when (index % 3) {
            0 -> scale.x
            1 -> scale.y
            else -> scale.z
        }
    }
    // A mirror turns every triangle's winding, and so its solid side, around; swapping two corners turns it back.
    val mirrored = scale.x * scale.y * scale.z < 0f
    val wound = if (mirrored) IntArray(indices.size) { index -> indices[index + MIRROR_SWAP[index % 3]] } else indices
    return MeshShape(scaled, wound)
}

/** First size of [MeshColliderSystem]'s reused batch; it grows to the largest batch seen. */
private const val INITIAL_BATCH = 16

/** Swaps each triangle's second and third corner. */
private val MIRROR_SWAP = intArrayOf(0, 1, -1)
