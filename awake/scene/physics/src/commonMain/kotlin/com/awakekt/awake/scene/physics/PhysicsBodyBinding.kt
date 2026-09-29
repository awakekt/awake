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

/** A box; [halfExtents] is half its size along each axis. */
@Serializable
@SerialName("box")
data class SceneBoxShape(val halfExtents: SceneVec3 = SceneVec3(HALF, HALF, HALF)) : SceneCollisionShape

@Serializable
@SerialName("sphere")
data class SceneSphereShape(val radius: Float = HALF) : SceneCollisionShape

/** An upright capsule: a cylinder of `2 * halfHeight` capped by hemispheres of [radius]. */
@Serializable
@SerialName("capsule")
data class SceneCapsuleShape(val halfHeight: Float = HALF, val radius: Float = HALF) : SceneCollisionShape

/**
 * A collider or rigid body, as authored in a scene. [layer] is a collision-layer index; null takes
 * the default for [motion]. A [sensor] detects what passes through it instead of blocking it.
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
        }
        if (!positive) add(SceneValidationIssue(path, "physics_body.shape sizes must be greater than 0"))
        if (layer != null && layer < 0) add(SceneValidationIssue(path, "physics_body.layer must not be negative"))
    }
}

/**
 * Loads `physics_body` into a [PhysicsBody]; [PhysicsSystem] builds the live body. A body whose shape
 * a scene can't describe, such as a terrain heightfield, is left out when saving.
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
        world.add(
            entity,
            PhysicsBody(
                shape = component.shape.toPhysicsShape(),
                motionType = component.motion,
                layer = component.layer?.let(::CollisionLayer) ?: defaultLayerFor(component.motion),
                sensor = component.sensor,
            ),
        )
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
}

private fun PhysicsShape.toSceneShape(): SceneCollisionShape? = when (this) {
    is BoxShape -> SceneBoxShape(SceneVec3(halfExtents.x, halfExtents.y, halfExtents.z))
    is SphereShape -> SceneSphereShape(radius)
    is CapsuleShape -> SceneCapsuleShape(halfHeight, radius)
    else -> null
}

private const val HALF = 0.5f
