/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Jumps and falls for [MovementControl] entities. Each one lands back at the height it first stood
 * at, so this suits a flat floor only.
 */
// ponytail: flat floor only; uneven ground needs the physics character controller.
class JumpSystem(
    private val jumpVelocity: Float = DEFAULT_JUMP_VELOCITY,
    private val gravity: Float = DEFAULT_GRAVITY,
) : System {
    override fun update(world: World, delta: Float) {
        world.queryEach(Transform::class, MovementControl::class) { _, transform, control ->
            val position = transform.position
            if (control.restY.isNaN()) control.restY = position.y
            val grounded = position.y <= control.restY && control.verticalVelocity <= 0f
            if (grounded && !control.jump) return@queryEach
            if (grounded) control.verticalVelocity = jumpVelocity

            control.verticalVelocity += gravity * delta
            position.y += control.verticalVelocity * delta
            if (position.y <= control.restY) {
                position.y = control.restY
                control.verticalVelocity = 0f
            }
        }
    }

    companion object {
        const val DEFAULT_JUMP_VELOCITY = 5f
        const val DEFAULT_GRAVITY = -14f
    }
}
