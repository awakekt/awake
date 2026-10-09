/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.core.input.AxisAction
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputActionDefinition
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ecs.World

/**
 * The input actions a player's [MovementControl] follows, and what triggers them in a scene that does
 * not bind them itself.
 */
object MovementActions {
    /** The axis that steers the player, up being forward. */
    const val MOVE = "move"

    /** The button the player jumps with. */
    const val JUMP = "jump"

    /** The button the player runs with. */
    const val RUN = "run"

    /** W/A/S/D and the arrows move, Space jumps, and Shift runs while held. */
    val defaults: List<InputActionDefinition> = listOf(
        AxisAction(
            MOVE,
            up = setOf(Key.W, Key.ArrowUp),
            down = setOf(Key.S, Key.ArrowDown),
            left = setOf(Key.A, Key.ArrowLeft),
            right = setOf(Key.D, Key.ArrowRight),
        ),
        ButtonAction(JUMP, keys = setOf(Key.Space)),
        ButtonAction(RUN, keys = setOf(Key.Shift)),
    )
}

/**
 * Sets the intent of each [MovementDriver.Player] control from [actions]: [MovementActions.MOVE]
 * steers, [MovementActions.JUMP] jumps and [MovementActions.RUN] runs. An agent's control is left
 * alone.
 *
 * [PlayerInputSystem] calls it once it has read the keys. A system that adds another source to the
 * actions afterwards, such as on-screen controls, calls it again.
 */
fun World.steerPlayers(actions: InputActions) {
    val moveX = actions.axisX(MovementActions.MOVE)
    val moveZ = actions.axisY(MovementActions.MOVE)
    val jump = actions.isActive(MovementActions.JUMP)
    val run = actions.isActive(MovementActions.RUN)
    queryEach(MovementControl::class) { _, control ->
        if (control.driver != MovementDriver.Player) return@queryEach
        control.moveX = moveX
        control.moveZ = moveZ
        control.jump = jump
        control.run = run
    }
}
