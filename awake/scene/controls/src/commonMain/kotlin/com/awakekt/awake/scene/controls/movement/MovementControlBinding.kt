/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.core.schema.PropertyRange
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.controls.camera.CameraRigBinding
import com.awakekt.awake.scene.controls.input.InputActionsBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Marks the entity the player moves. [speed] and [runSpeed] (while running) are in units per second;
 * a null [speed] uses the system's, a null [runSpeed] keeps [speed]. A player follows the scene's
 * `move`, `jump` and `run` input actions, which `input_actions` binds. [turnSpeed] is how many radians
 * per second it turns to face where it moves; 0 leaves its facing alone. [driver] is what sets the
 * intent: the local player (the default), or code such as AI or a network, in world space.
 *
 * @property speed Standard movement speed in units per second, or `null` to use the system default.
 * @property runSpeed Accelerated run speed in units per second, or `null` to keep [speed].
 * @property turnSpeed Angular rotation rate in radians per second when turning toward movement direction.
 * @property driver What sets the intent: [MovementDriver.Player] or [MovementDriver.Agent].
 */
@Serializable
@SerialName("movement_control")
data class SceneMovementControl(
    @PropertyRange(min = 0.0, exclusiveMin = true) val speed: Float? = null,
    @PropertyRange(min = 0.0, exclusiveMin = true) val runSpeed: Float? = null,
    @PropertyRange(min = 0.0) val turnSpeed: Float = 0f,
    val driver: MovementDriver = MovementDriver.Player,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (speed != null && speed <= 0f) {
            add(SceneValidationIssue(path, "movement_control.speed must be greater than 0"))
        }
        if (runSpeed != null && runSpeed <= 0f) {
            add(SceneValidationIssue(path, "movement_control.runSpeed must be greater than 0"))
        }
        if (turnSpeed < 0f) add(SceneValidationIssue(path, "movement_control.turnSpeed must not be negative"))
    }
}

/**
 * Scene component binding for serializing and deserializing [MovementControl] components as [SceneMovementControl].
 */
object MovementControlBinding : SceneComponentBinding<MovementControl, SceneMovementControl> {
    override val componentClass: KClass<MovementControl> = MovementControl::class
    override val schemaClass: KClass<SceneMovementControl> = SceneMovementControl::class
    override val serializer = SceneMovementControl.serializer()

    override fun attachTyped(
        world: World,
        entity: Entity,
        component: SceneMovementControl,
        context: SceneResolutionContext,
    ) {
        world.add(
            entity,
            MovementControl().apply {
                speed = component.speed
                runSpeed = component.runSpeed
                turnSpeed = component.turnSpeed
                driver = component.driver
            },
        )
    }

    override fun export(world: World, entity: Entity, component: MovementControl): SceneMovementControl =
        SceneMovementControl(
            speed = component.speed,
            runSpeed = component.runSpeed,
            turnSpeed = component.turnSpeed,
            driver = component.driver,
        )
}

/**
 * Registers the controls scene components (`movement_control`, `camera_rig` and `input_actions`) with this registry.
 *
 * @return This [SceneComponentRegistry] instance for chaining.
 */
fun SceneComponentRegistry.registerControls(): SceneComponentRegistry =
    register(MovementControlBinding).register(CameraRigBinding).register(InputActionsBinding)
