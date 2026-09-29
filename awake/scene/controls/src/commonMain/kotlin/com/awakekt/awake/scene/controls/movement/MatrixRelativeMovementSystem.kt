/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Matrix-Relative Movement: Dynamically transforms player input vectors based on the forward
 * and right vectors of the currently active camera. This ensures that 'W' always moves the
 * player in the direction the camera is looking (projected onto the horizontal plane).
 *
 * It writes [Transform.position] directly, so nothing stops it at a wall. An entity moved by a
 * character controller must not also run this system.
 */
class MatrixRelativeMovementSystem(
    private val speed: Float = 5f,
) : System {
    private val basis = CameraRelativeBasis()

    override fun update(world: World, delta: Float) {
        basis.update(world)
        world.queryEach(Transform::class, MovementControl::class) { _, transform, control ->
            if (control.moveX == 0f && control.moveZ == 0f) return@queryEach
            val step = (control.speed ?: speed) * delta
            transform.position.x += basis.worldX(control.moveX, control.moveZ) * step
            transform.position.z += basis.worldZ(control.moveX, control.moveZ) * step
        }
    }
}
