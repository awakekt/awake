/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.schema.PropertyRange
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
 * Marks the entity the player moves. [speed] and [runSpeed] (while running) are in units per second;
 * a null [speed] uses the system's, a null [runSpeed] keeps [speed]. The player runs while [runKey] is
 * held, or with [RunMode.Toggle] starts running and presses [runKey] to walk and run again. [turnSpeed]
 * is how many radians per second it turns to face where it moves; 0 leaves its facing alone. [driver]
 * is what sets the intent: the local player (the default), or code such as AI or a network, in world
 * space.
 *
 * @property speed Standard movement speed in units per second, or `null` to use the system default.
 * @property runSpeed Accelerated run speed in units per second, or `null` to keep [speed].
 * @property turnSpeed Angular rotation rate in radians per second when turning toward movement direction.
 * @property driver What sets the intent: [MovementDriver.Player] or [MovementDriver.Agent].
 * @property runMode Whether [runKey] is held to run or pressed to switch between walking and running.
 * @property runKey The key the player runs with.
 */
@Serializable
@SerialName("movement_control")
data class SceneMovementControl(
    @PropertyRange(min = 0.0, exclusiveMin = true) val speed: Float? = null,
    @PropertyRange(min = 0.0, exclusiveMin = true) val runSpeed: Float? = null,
    @PropertyRange(min = 0.0) val turnSpeed: Float = 0f,
    val driver: MovementDriver = MovementDriver.Player,
    val runMode: RunMode = RunMode.Hold,
    val runKey: Key = Key.Shift,
) : SceneComponent {
    override fun validate(path: String): List<SceneValidationIssue> = buildList {
        if (speed != null && speed <= 0f) {
            add(SceneValidationIssue(path, "movement_control.speed must be greater than 0"))
        }
        if (runSpeed != null && runSpeed <= 0f) {
            add(SceneValidationIssue(path, "movement_control.runSpeed must be greater than 0"))
        }
        if (turnSpeed < 0f) add(SceneValidationIssue(path, "movement_control.turnSpeed must not be negative"))
        if (driver == MovementDriver.Player && runKey in PLAYER_MOVE_AND_JUMP_KEYS) {
            add(SceneValidationIssue(path, "movement_control.runKey must not be a key the player moves or jumps with: $runKey"))
        }
        if (driver == MovementDriver.Player && runKey == Key.Unknown) {
            add(SceneValidationIssue(path, "movement_control.runKey must be a key that can be pressed"))
        }
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
                runMode = component.runMode
                runKey = component.runKey
                // Only the player's input ever switches it back, so only a player starts running.
                run = component.runMode == RunMode.Toggle && component.driver == MovementDriver.Player
            },
        )
    }

    override fun export(world: World, entity: Entity, component: MovementControl): SceneMovementControl =
        SceneMovementControl(
            speed = component.speed,
            runSpeed = component.runSpeed,
            turnSpeed = component.turnSpeed,
            driver = component.driver,
            runMode = component.runMode,
            runKey = component.runKey,
        )
}

/**
 * Registers the controls scene components (`movement_control` and `camera_rig`) with this registry.
 *
 * @return This [SceneComponentRegistry] instance for chaining.
 */
fun SceneComponentRegistry.registerControls(): SceneComponentRegistry =
    register(MovementControlBinding).register(CameraRigBinding)
