/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.physics

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.BoxShape
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.physics.CollisionLayer
import com.awakekt.awake.physics.MotionType
import com.awakekt.awake.physics.PhysicsShape
import com.awakekt.awake.physics.SphereShape
import com.awakekt.awake.physics.defaultLayerFor
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.document.SceneVec3
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/** A collision shape a scene can describe, centred on its node. */
@Serializable
sealed interface SceneCollisionShape

/**
 * A box collision shape; [halfExtents] is half its size along each axis.
 *
 * @property halfExtents Half-extent dimensions along each coordinate axis in meters.
 */
@Serializable
@SerialName("box")
data class SceneBoxShape(val halfExtents: SceneVec3 = SceneVec3(HALF, HALF, HALF)) : SceneCollisionShape

/**
 * A spherical collision shape defined by [radius].
 *
 * @property radius Radius of the sphere in meters.
 */
@Serializable
@SerialName("sphere")
data class SceneSphereShape(val radius: Float = HALF) : SceneCollisionShape

/**
 * An upright capsule collision shape: a cylinder of `2 * halfHeight` capped by hemispheres of [radius].
 *
 * @property halfHeight Half-height of the cylindrical body in meters.
 * @property radius Radius of the cylinder and hemispherical caps in meters.
 */
@Serializable
@SerialName("capsule")
data class SceneCapsuleShape(val halfHeight: Float = HALF, val radius: Float = HALF) : SceneCollisionShape

/**
 * The triangles of a model asset, scaled by the node: a static collider that matches the model, so
 * a bridge can be walked on and an arch walked under. [MeshColliderSystem] builds it.
 *
 * @property mesh Project path of the `.glb` or `.gltf` model.
 * @property primitive Which primitive of the model to collide with, counted in node order; `null` merges them all.
 */
@Serializable
@SerialName("mesh")
data class SceneMeshShape(val mesh: String, val primitive: Int? = null) : SceneCollisionShape

/**
 * The convex hull of a model asset's vertices, scaled by the node: a collider for a prop that moves
 * and is not a box. Concave detail is lost (shrink-wrapped). Supports any [MotionType] (including
 * dynamic) and trigger sensors. [MeshColliderSystem] builds it.
 *
 * @property mesh Project path of the `.glb` or `.gltf` model.
 * @property primitive Which primitive of the model to collide with, counted in node order; `null` merges them all.
 */
@Serializable
@SerialName("convex_hull")
data class SceneConvexHullShape(val mesh: String, val primitive: Int? = null) : SceneCollisionShape

/**
 * A collider or rigid body, as authored in a scene. [layer] is a collision-layer index; null takes
 * the default for [motion]. A [sensor] detects what passes through it instead of blocking it.
 *
 * @property shape Collision geometry for this body.
 * @property motion Motion type controlling whether the body is static, kinematic, or dynamic.
 * @property layer Collision layer index, or `null` for the layer default matching [motion].
 * @property sensor Whether this body functions as a trigger sensor rather than a solid collider.
 */
@Serializable
@SerialName("physics_body")
data class ScenePhysicsBody(
    val shape: SceneCollisionShape = SceneBoxShape(),
    val motion: MotionType = MotionType.STATIC,
    val layer: Int? = null,
    val sensor: Boolean = false,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        val positive = when (shape) {
            is SceneBoxShape -> shape.halfExtents.let { it.x > 0f && it.y > 0f && it.z > 0f }
            is SceneSphereShape -> shape.radius > 0f
            is SceneCapsuleShape -> shape.halfHeight > 0f && shape.radius > 0f
            is SceneMeshShape, is SceneConvexHullShape -> true
        }
        if (!positive) add(SceneValidationIssue(path, "physics_body.shape sizes must be greater than 0"))
        if (shape is SceneMeshShape) addAll(shape.validate(path))
        if (shape is SceneConvexHullShape) addAll(shape.validate(path))
        if (layer != null && layer < 0) add(SceneValidationIssue(path, "physics_body.layer must not be negative"))
    }

    // A triangle mesh is a surface with no inside, so it can neither move nor be a sensor.
    private fun SceneMeshShape.validate(path: String): List<SceneValidationIssue> = buildList {
        if (mesh.isBlank()) add(SceneValidationIssue(path, "physics_body.shape.mesh must name a model"))
        if (primitive != null && primitive < 0) add(SceneValidationIssue(path, "physics_body.shape.primitive must not be negative"))
        if (motion != MotionType.STATIC) {
            val message = "physics_body with a mesh shape must be STATIC, not $motion; a moving body needs a box, sphere, capsule or convex_hull"
            add(SceneValidationIssue(path, message))
        }
        if (sensor) add(SceneValidationIssue(path, "physics_body with a mesh shape cannot be a sensor"))
    }

    private fun SceneConvexHullShape.validate(path: String): List<SceneValidationIssue> = buildList {
        if (mesh.isBlank()) add(SceneValidationIssue(path, "physics_body.shape.mesh must name a model"))
        if (primitive != null && primitive < 0) add(SceneValidationIssue(path, "physics_body.shape.primitive must not be negative"))
    }
}

/**
 * Loads `physics_body` into a [PhysicsBody]; [PhysicsSystem] builds the live body. A `mesh` shape
 * loads as a [MeshCollider] and a `convex_hull` shape loads as a [ConvexHullCollider], which
 * [MeshColliderSystem] turns into the body and saves as its model reference. A body whose shape a
 * scene can't describe, such as a terrain heightfield, is left out when saving.
 */
object PhysicsBodyBinding : SceneComponentBinding<PhysicsBody, ScenePhysicsBody> {
    override val componentClass: KClass<PhysicsBody> = PhysicsBody::class
    override val schemaClass: KClass<ScenePhysicsBody> = ScenePhysicsBody::class
    override val serializer = ScenePhysicsBody.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: ScenePhysicsBody,
        context: SceneResolutionContext,
    ) {
        val layer = component.layer?.let(::CollisionLayer) ?: defaultLayerFor(component.motion)
        if (component.shape is SceneMeshShape) {
            world.add(entity, MeshCollider(component.shape.mesh, component.shape.primitive, layer))
            return
        }
        if (component.shape is SceneConvexHullShape) {
            world.add(
                entity,
                ConvexHullCollider(
                    mesh = component.shape.mesh,
                    primitive = component.shape.primitive,
                    motion = component.motion,
                    layer = layer,
                    sensor = component.sensor,
                ),
            )
            return
        }
        world.add(
            entity,
            PhysicsBody(
                shape = component.shape.toPhysicsShape(),
                motionType = component.motion,
                layer = layer,
                sensor = component.sensor,
            ),
        )
    }

    override fun exportFrom(world: World, entity: Entity): ScenePhysicsBody? {
        val meshCollider = world.get<MeshCollider>(entity)
        val hullCollider = world.get<ConvexHullCollider>(entity)
        return when {
            meshCollider != null -> ScenePhysicsBody(
                shape = SceneMeshShape(meshCollider.mesh, meshCollider.primitive),
                layer = meshCollider.layer.index.takeIf { meshCollider.layer != defaultLayerFor(MotionType.STATIC) },
            )
            hullCollider != null -> ScenePhysicsBody(
                shape = SceneConvexHullShape(hullCollider.mesh, hullCollider.primitive),
                motion = hullCollider.motion,
                layer = hullCollider.layer.index.takeIf { hullCollider.layer != defaultLayerFor(hullCollider.motion) },
                sensor = hullCollider.sensor,
            )
            else -> super.exportFrom(world, entity)
        }
    }

    override fun export(world: World, entity: Entity, component: PhysicsBody): ScenePhysicsBody? {
        val shape = component.shape.toSceneShape() ?: return null
        return ScenePhysicsBody(
            shape = shape,
            motion = component.motionType,
            layer = component.layer.index.takeIf { component.layer != defaultLayerFor(component.motionType) },
            sensor = component.sensor,
        )
    }
}

/** Registers `physics_body` for loading and saving. */
fun SceneComponentRegistry.registerPhysics(): SceneComponentRegistry = register(PhysicsBodyBinding)

private fun SceneCollisionShape.toPhysicsShape(): PhysicsShape = when (this) {
    is SceneBoxShape -> BoxShape(Vec3f(halfExtents.x, halfExtents.y, halfExtents.z))
    is SceneSphereShape -> SphereShape(radius)
    is SceneCapsuleShape -> CapsuleShape(halfHeight, radius)
    is SceneMeshShape -> error("a mesh shape loads as a MeshCollider, not a PhysicsBody")
    is SceneConvexHullShape -> error("a convex_hull shape loads as a ConvexHullCollider, not a PhysicsBody")
}

private fun PhysicsShape.toSceneShape(): SceneCollisionShape? = when (this) {
    is BoxShape -> SceneBoxShape(SceneVec3(halfExtents.x, halfExtents.y, halfExtents.z))
    is SphereShape -> SceneSphereShape(radius)
    is CapsuleShape -> SceneCapsuleShape(halfHeight, radius)
    else -> null
}

private const val HALF = 0.5f
