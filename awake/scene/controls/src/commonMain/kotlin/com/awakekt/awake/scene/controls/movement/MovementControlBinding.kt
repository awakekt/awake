/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentBinding
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.SceneResolutionContext
import com.awakekt.awake.scene.controls.camera.CameraRigBinding
import com.awakekt.awake.scene.document.SceneComponent
import com.awakekt.awake.scene.document.SceneValidationIssue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.reflect.KClass

/**
 * Marks the entity the player moves. [speed] and [runSpeed] (while Shift is held) are in units per
 * second; a null [speed] uses the system's, a null [runSpeed] keeps [speed]. [turnSpeed] is how many
 * radians per second it turns to face where it moves; 0 leaves its facing alone.
 */
@Serializable
@SerialName("movement_control")
data class SceneMovementControl(
    val speed: Float? = null,
    val runSpeed: Float? = null,
    val turnSpeed: Float = 0f,
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
            },
        )
    }

    override fun export(world: World, entity: Entity, component: MovementControl): SceneMovementControl =
        SceneMovementControl(speed = component.speed, runSpeed = component.runSpeed, turnSpeed = component.turnSpeed)
}

/** Registers the controls' scene components, `movement_control` and `camera_rig`, for loading and saving. */
fun SceneComponentRegistry.registerControls(): SceneComponentRegistry =
    register(MovementControlBinding).register(CameraRigBinding)
