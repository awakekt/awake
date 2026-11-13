/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.game.TouchAimState
import com.awakekt.awake.compose.game.TouchStickState
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.movement.MovementActions
import com.awakekt.awake.scene.controls.movement.steerPlayers

/**
 * Feeds a Compose `TouchStick` into the input action a canvas joystick feeds, so a game swaps one for
 * the other and its movement code, which reads [action], does not change. Add it to any entity next
 * to the world, passing the same [state] the `TouchStick` composable draws.
 *
 * It reads [state] the way the canvas joystick's deflection is read: while the stick is deflected it
 * pushes ([TouchStickState.x], up positive) to [action], and at rest it pushes nothing, so a canvas
 * joystick or the keys beside it keep the action. While both are deflected the Compose stick wins,
 * since it is read after the canvas controls.
 *
 * @property state The stick's live state, owned by the game.
 * @property action The axis action to steer; `move` by default, as a canvas joystick's usually is.
 */
class TouchStickInput(val state: TouchStickState, val action: String = MovementActions.MOVE)

/**
 * Feeds a Compose `TouchAim` into the actions a canvas aim button feeds. While a finger drags, the aim
 * is pushed to [aimAction] and [fireAction] is held; when it lets go the aim it let go with is pushed
 * once more and [fireAction] is pressed, as the canvas button does for the frame it fires. An action
 * the scene does not declare, or an empty one, is skipped, so it is left for the game.
 *
 * @property state The aim's live state, owned by the game.
 * @property aimAction The axis action that receives the aim, up positive.
 * @property fireAction The button action held while aiming and pressed on release.
 */
class TouchAimInput(val state: TouchAimState, val aimAction: String, val fireAction: String = "")

/** The screen-space ([x] right, [y] down) pair of a touch control as an axis action's, where up is positive. */
internal fun InputActions.pushScreen(action: String, x: Float, y: Float) = push(action, x, -y)

// Held in vals: on the JVM each `X::class` written out makes a new class reference, a few bytes a frame.
private val STICK_INPUT = TouchStickInput::class
private val AIM_INPUT = TouchAimInput::class
private val ACTIONS = InputActions::class

internal fun World.hasTouchControls(): Boolean = family(STICK_INPUT).size > 0 || family(AIM_INPUT).size > 0

/**
 * Adds the Compose touch controls to the input actions after the canvas controls, then sets the
 * players' intent again. It does nothing, and allocates nothing, in a scene that has none, and a held
 * finger allocates nothing either: it reads each control's state by index.
 */
internal class TouchControlInputSystem : System {
    override fun update(world: World, delta: Float) {
        if (!world.hasTouchControls()) return
        // Not World.inputActions(): its query allocates, and this runs every frame a finger is down.
        val holders = world.family(ACTIONS)
        if (holders.size == 0) return
        val actions = holders.componentAt(0)
        var touched = feedSticks(world, actions)
        if (feedAims(world, actions)) touched = true
        if (touched) world.steerPlayers(actions)
    }

    private fun feedSticks(world: World, actions: InputActions): Boolean {
        var fed = false
        val sticks = world.family(STICK_INPUT)
        for (index in 0 until sticks.size) {
            val stick = sticks.componentAt(index)
            val state = stick.state
            if (stick.action !in actions || (state.x == 0f && state.y == 0f)) continue
            actions.pushScreen(stick.action, state.x, state.y)
            fed = true
        }
        return fed
    }

    private fun feedAims(world: World, actions: InputActions): Boolean {
        var fed = false
        val aims = world.family(AIM_INPUT)
        for (index in 0 until aims.size) {
            if (feedAim(aims.componentAt(index), actions)) fed = true
        }
        return fed
    }

    private fun feedAim(aim: TouchAimInput, actions: InputActions): Boolean {
        val state = aim.state
        val aiming = state.isAiming
        // Asked every frame so a release is consumed even when its actions are not declared.
        val fired = state.consumeRelease()
        var fed = false
        if (aim.aimAction in actions && (aiming || fired)) {
            actions.pushScreen(aim.aimAction, state.x, state.y)
            fed = true
        }
        if (aim.fireAction in actions) {
            if (aiming) actions.hold(aim.fireAction)
            if (fired) actions.press(aim.fireAction)
            fed = fed || aiming || fired
        }
        return fed
    }
}
