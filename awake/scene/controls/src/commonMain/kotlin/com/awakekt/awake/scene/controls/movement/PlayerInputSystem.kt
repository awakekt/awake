/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.movement

import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.input.inputActions

/**
 * Reads the player's keys and pointer buttons into the scene's input actions, then sets each player's
 * [MovementControl] intent from them, as [steerPlayers] says. A scene with no `input_actions` gets
 * [MovementActions.defaults], so later systems, a game's own included, read the same actions.
 *
 * While the UI has the keys, such as while a text field is focused, no key reaches the actions: the
 * player stops and a held action lets go, and a toggle stays as it was.
 */
class PlayerInputSystem(
    /** This frame's input, with whatever the UI claimed already taken out. */
    private val inputProvider: () -> GameplayInput,
) : System {
    override fun update(world: World, delta: Float) {
        val actions = world.inputActions() ?: InputActions(MovementActions.defaults).also { world.add(world.create(), it) }
        actions.beginFrame()
        actions.read(inputProvider())
        world.steerPlayers(actions)
    }
}
