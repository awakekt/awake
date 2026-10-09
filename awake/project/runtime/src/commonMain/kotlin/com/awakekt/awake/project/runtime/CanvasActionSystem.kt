/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.CanvasElementKind
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.controls.movement.MovementActions
import com.awakekt.awake.scene.controls.movement.steerPlayers

/** The canvas action a Joystick steers the player with. */
@Deprecated("The move input action", ReplaceWith("MovementActions.MOVE", "com.awakekt.awake.scene.controls.movement.MovementActions"))
const val MOVE_ACTION = MovementActions.MOVE

/** The canvas action a Button makes the player jump with, for as long as it is held. */
@Deprecated("The jump input action", ReplaceWith("MovementActions.JUMP", "com.awakekt.awake.scene.controls.movement.MovementActions"))
const val JUMP_ACTION = MovementActions.JUMP

/**
 * Adds the scene's canvas controls to its input actions, after the keyboard, then sets the players'
 * intent again: a Joystick deflected steers the axis action it names, up being positive, and a Button
 * holds and presses the button action it names. A `move` Joystick, a `jump` Button and a `run` Button
 * so steer, jump and run as the keys do.
 *
 * An element naming no action of the scene is left to the game, its presses unconsumed. Where the
 * controls sit and how big they are is the scene's to decide.
 */
class CanvasActionSystem : System {
    private var touched = false

    override fun update(world: World, delta: Float) {
        val actions = world.inputActions() ?: return
        touched = false
        world.family<CanvasElement>().forEach { _, element -> if (feed(element, actions)) touched = true }
        if (touched) world.steerPlayers(actions)
    }

    /** Adds what [element] does this frame to [actions], and returns whether it did anything. */
    private fun feed(element: CanvasElement, actions: InputActions): Boolean {
        if (element.action !in actions) return false
        return when (element.kind) {
            CanvasElementKind.Joystick -> {
                val deflected = element.stickX != 0f || element.stickY != 0f
                if (deflected) actions.push(element.action, element.stickX, -element.stickY)
                deflected
            }
            CanvasElementKind.Button -> {
                val held = element.isHeld
                val pressed = element.consumePress()
                if (held) actions.hold(element.action)
                if (pressed) actions.press(element.action)
                held || pressed
            }
            else -> false
        }
    }
}
