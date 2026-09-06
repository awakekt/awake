/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.movement.MovementControl
import kotlin.math.sqrt

/**
 * Dedicated system for handling user input and mapping it to [MovementControl] intent.
 * This is the ONLY system that should read/consume hardware snapshots for movement.
 */
class PlayerInputSystem(
    /** Provider for the hardware snapshot for the current frame. */
    /** This frame's input, with whatever the UI claimed already taken out. */
    private val inputProvider: () -> GameplayInput,
) : System {
    override fun update(world: World, delta: Float) {
        val input = inputProvider()

        // Cleared, not just unread: a system that saw no keys would leave the player walking in
        // whatever direction was last held. Typing "wasd" into a focused field must stop it.
        if (input.keysOwnedByUi) {
            world.queryEach(MovementControl::class) { _, control ->
                control.moveX = 0f
                control.moveZ = 0f
            }
            return
        }

        var moveX = 0f
        var moveZ = 0f
        if (input.isDown(Key.W) || input.isDown(Key.ArrowUp)) moveZ += 1f
        if (input.isDown(Key.S) || input.isDown(Key.ArrowDown)) moveZ -= 1f
        if (input.isDown(Key.A) || input.isDown(Key.ArrowLeft)) moveX -= 1f
        if (input.isDown(Key.D) || input.isDown(Key.ArrowRight)) moveX += 1f

        // Normalize vector to prevent diagonal speed boost
        if (moveX != 0f || moveZ != 0f) {
            val len = sqrt(moveX * moveX + moveZ * moveZ)
            moveX /= len
            moveZ /= len
        }

        world.queryEach(MovementControl::class) { _, control ->
            control.moveX = moveX
            control.moveZ = moveZ
        }
    }
}
