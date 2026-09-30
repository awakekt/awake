/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.CapsuleShape
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.core.motion.GroundContact
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import com.awakekt.awake.scene.physics.character.CharacterConfig
import com.awakekt.awake.scene.physics.character.KinematicCharacterController
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * A character that walks and falls through physics instead of through walls, as authored in a scene.
 * Its body is an upright capsule of [radius] around a cylinder of `2 * halfHeight`. It climbs steps
 * up to [stepHeight] and walks up slopes to [slopeLimit] radians. It jumps at [jumpSpeed] units per
 * second, and a [jumpSpeed] of 0 means it can't jump. [gravity] is its downward acceleration.
 */
@Serializable
@SerialName("character_controller")
data class SceneCharacterController(
    val radius: Float = DEFAULT_RADIUS,
    val halfHeight: Float = DEFAULT_HALF_HEIGHT,
    val stepHeight: Float = DEFAULTS.stepHeight,
    val slopeLimit: Float = DEFAULTS.slopeLimitRadians,
    val jumpSpeed: Float = 0f,
    val gravity: Float = DEFAULT_GRAVITY,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (radius <= 0f || halfHeight <= 0f) {
            add(SceneValidationIssue(path, "character_controller.radius and halfHeight must be greater than 0"))
        }
        if (stepHeight < 0f) add(SceneValidationIssue(path, "character_controller.stepHeight must not be negative"))
        if (jumpSpeed < 0f) add(SceneValidationIssue(path, "character_controller.jumpSpeed must not be negative"))
    }
}

/**
 * The live character. [CharacterControllerSystem] builds its [KinematicCharacterController] on the
 * first update, once a physics world exists.
 */
class CharacterController(
    val config: CharacterConfig,
    var jumpSpeed: Float,
    var gravity: Float,
) {
    internal var controller: KinematicCharacterController? = null
    internal var verticalVelocity: Float = 0f

    /** Whether it stood on walkable ground after its last move. */
    val isGrounded: Boolean get() = controller?.isGrounded ?: false
}

object CharacterControllerBinding : SceneComponentBinding<CharacterController, SceneCharacterController> {
    override val componentClass: KClass<CharacterController> = CharacterController::class
    override val schemaClass: KClass<SceneCharacterController> = SceneCharacterController::class
    override val serializer = SceneCharacterController.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneCharacterController,
        context: SceneResolutionContext,
    ) {
        val config = CharacterConfig(
            shape = CapsuleShape(halfHeight = component.halfHeight, radius = component.radius),
            slopeLimitRadians = component.slopeLimit,
            stepHeight = component.stepHeight,
            stepDownDistance = component.stepHeight,
        )
        world.add(entity, CharacterController(config, component.jumpSpeed, component.gravity))
        world.add(entity, GroundContact())
    }

    override fun export(world: World, entity: Entity, component: CharacterController) = SceneCharacterController(
        radius = component.config.shape.radius,
        halfHeight = component.config.shape.halfHeight,
        stepHeight = component.config.stepHeight,
        slopeLimit = component.config.slopeLimitRadians,
        jumpSpeed = component.jumpSpeed,
        gravity = component.gravity,
    )
}

/** Registers `character_controller` for loading and saving. */
fun SceneComponentRegistry.registerCharacter(): SceneComponentRegistry = register(CharacterControllerBinding)

private const val DEFAULT_RADIUS = 0.5f
private const val DEFAULT_HALF_HEIGHT = 0.5f
private const val DEFAULT_GRAVITY = -9.81f
private val DEFAULTS = CharacterConfig(CapsuleShape(DEFAULT_HALF_HEIGHT, DEFAULT_RADIUS))
