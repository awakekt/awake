/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.character

import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.physics.PhysicsWorld
import com.awakekt.awake.scene.controls.movement.CameraRelativeBasis
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.physics.character.KinematicCharacterController

/**
 * Moves every [CharacterController] entity through [physicsWorld]: its [MovementControl] intent,
 * relative to the active camera, at the intent's speed or [defaultSpeed], plus gravity, a jump when
 * the intent asks while grounded, and whatever the ground under it is carrying. Walls stop it,
 * steps and slopes within its limits don't.
 *
 * Register it in the fixed phase, after `PhysicsSystem`, so it collides with bodies that step
 * built, and falls the same on every frame rate. Don't also run `MatrixRelativeMovementSystem`
 * on these entities: it moves them straight through walls.
 */
class CharacterControllerSystem(
    private val physicsWorld: PhysicsWorld,
    private val defaultSpeed: Float = DEFAULT_SPEED,
) : System {
    private val basis = CameraRelativeBasis()
    private val motion = Vec3f()

    override fun update(world: World, delta: Float) {
        basis.update(world)
        world.queryEach(Transform::class, CharacterController::class) { entity, transform, character ->
            val body = character.controller
                ?: KinematicCharacterController(physicsWorld, character.config, transform.position)
                    .also { character.controller = it }
            val intent = world.get<MovementControl>(entity)
            val moveX = intent?.moveX ?: 0f
            val moveZ = intent?.moveZ ?: 0f
            val step = (intent?.currentSpeed(defaultSpeed) ?: defaultSpeed) * delta

            // Just after take-off the ground probe still reaches the floor, so a rising character
            // is airborne whatever the probe says; otherwise the next step would cancel the jump.
            val standing = body.isGrounded && character.verticalVelocity <= 0f
            character.verticalVelocity = when {
                standing && intent?.jump == true && character.jumpSpeed > 0f -> character.jumpSpeed
                standing -> 0f
                else -> character.verticalVelocity + character.gravity * delta
            }
            motion.set(
                basis.worldX(moveX, moveZ) * step + body.groundVelocity.x * delta,
                character.verticalVelocity * delta + body.groundVelocity.y * delta,
                basis.worldZ(moveX, moveZ) * step + body.groundVelocity.z * delta,
            )
            body.move(motion, delta)
            transform.position.set(body.position)
            intent?.turnToward(transform, basis.worldX(moveX, moveZ), basis.worldZ(moveX, moveZ), delta)
        }
    }

    private companion object {
        const val DEFAULT_SPEED = 5f
    }
}
