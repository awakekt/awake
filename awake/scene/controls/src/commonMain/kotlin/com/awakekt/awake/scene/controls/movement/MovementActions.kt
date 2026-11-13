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
 *
 * It runs every frame, so it overwrites whatever else wrote a player's `moveX` and `moveZ`. A game
 * that takes over a player's motion, such as a flight model driving a free-moving character from the
 * player's input, sets the control's [MovementControl.driver] to [MovementDriver.Agent] while it does:
 * this leaves the control alone, and the game reads the actions itself and writes the world velocity.
 * Setting it back to [MovementDriver.Player] hands the control back.
 */
fun World.steerPlayers(actions: InputActions) {
    val moveX = actions.axisX(MovementActions.MOVE)
    val moveZ = actions.axisY(MovementActions.MOVE)
    val jump = actions.isActive(MovementActions.JUMP)
    val run = actions.isActive(MovementActions.RUN)
    queryEach(MOVEMENT_CONTROL) { _, control ->
        if (control.driver != MovementDriver.Player) return@queryEach
        control.moveX = moveX
        control.moveZ = moveZ
        control.jump = jump
        control.run = run
    }
}

// Held in a val: on the JVM `MovementControl::class` written out makes a new class reference every frame.
private val MOVEMENT_CONTROL = MovementControl::class
