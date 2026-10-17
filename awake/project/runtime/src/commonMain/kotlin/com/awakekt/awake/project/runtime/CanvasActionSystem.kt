/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.turnBy
import com.awakekt.awake.scene.controls.camera.zoomBy
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.controls.movement.MovementActions
import com.awakekt.awake.scene.controls.movement.steerPlayers
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasElementKind
import com.awakekt.awake.ui.canvas.CanvasLook
import kotlin.math.ln

/** The canvas action a Joystick steers the player with. */
@Deprecated("The move input action", ReplaceWith("MovementActions.MOVE", "com.awakekt.awake.scene.controls.movement.MovementActions"))
const val MOVE_ACTION = MovementActions.MOVE

/** The canvas action a Button makes the player jump with, for as long as it is held. */
@Deprecated("The jump input action", ReplaceWith("MovementActions.JUMP", "com.awakekt.awake.scene.controls.movement.MovementActions"))
const val JUMP_ACTION = MovementActions.JUMP

/**
 * Adds the scene's canvas controls to its input actions, after the keyboard, then sets the players'
 * intent again: a Joystick deflected steers the axis action it names, up being positive, and a Button
 * holds and presses the button action it names, and presses its `doubleAction` on a double-click. A
 * `move` Joystick, a `jump` Button and a `run` Button so steer, jump and run as the keys do. A
 * Joystick pushed past its sprint threshold also holds its `joystick.sprintAction`, so one thumb can
 * both steer and run. A Button with `button` settings also presses its `longPressAction` on a long
 * press and its `releaseAction` when let go, and a disabled or cooling one does nothing. An aimed
 * button pushes its aim to its `aim.aimAction` while it is dragged and on the frame it fires. A
 * LookArea's swipe turns the active camera rig and its pinch zooms it, and each is pushed to the
 * axis actions it names.
 *
 * An element naming no action of the scene is left to the game, its presses unconsumed. Where the
 * controls sit and how big they are is the scene's to decide.
 */
class CanvasActionSystem : System {
    private var touched = false

    override fun update(world: World, delta: Float) {
        val actions = world.inputActions()
        world.family<CanvasElement>().forEach { _, element -> if (element.kind == CanvasElementKind.LookArea) look(world, element, actions) }
        if (actions == null) return
        touched = false
        world.family<CanvasElement>().forEach { _, element ->
            if (feed(element, actions)) touched = true
            if (feedSprint(element, actions)) touched = true
            if (feedDouble(element, actions)) touched = true
        }
        if (touched) world.steerPlayers(actions)
    }

    /**
     * A LookArea's swipe and pinch since last frame: pushed to its `action` (up positive) and its
     * `look.zoomAction` when the scene declares them, and turning and zooming the active camera rig
     * while `look.turnsCamera`.
     */
    private fun look(world: World, element: CanvasElement, actions: InputActions?) {
        val settings = element.look ?: CanvasLook.Default
        val look = element.consumeLook()
        val zoom = element.consumeZoom()
        val looked = look.x != 0f || look.y != 0f
        if (looked && actions != null && element.action in actions) actions.push(element.action, look.x, -look.y)
        if (zoom != 1f && actions != null && settings.zoomAction in actions) actions.push(settings.zoomAction, ln(zoom), 0f)
        if (!settings.turnsCamera || (!looked && zoom == 1f)) return
        world.queryEach(CameraRig::class, ActiveCamera::class) { _, rig, _ ->
            rig.turnBy(look.x * LOOK_RADIANS_PER_DP, -look.y * LOOK_RADIANS_PER_DP)
            rig.zoomBy(zoom)
        }
    }

    /**
     * Holds a Joystick's sprint action while its knob is past its sprint threshold, pressing it on the
     * frame it gets there, and returns whether it did.
     */
    private fun feedSprint(element: CanvasElement, actions: InputActions): Boolean {
        val sprint = element.joystick?.sprintAction
        if (element.kind != CanvasElementKind.Joystick || sprint.isNullOrEmpty() || sprint !in actions) return false
        if (element.consumeSprintStart()) actions.press(sprint)
        if (element.sprinting) actions.hold(sprint)
        return element.sprinting
    }

    /**
     * An action button: held while held, pressed when it fires, and its long-press and release
     * actions pressed when those happen, each only when the scene declares it, so one the scene does
     * not is left for the game to read.
     */
    private fun feedActionButton(element: CanvasElement, actions: InputActions): Boolean {
        val settings = element.button ?: return false
        var fed = false
        var fired = false
        if (element.action in actions) {
            val held = element.isHeld
            fired = element.consumePress()
            if (held) actions.hold(element.action)
            if (fired) actions.press(element.action)
            fed = held || fired
        }
        if (settings.longPressAction in actions && element.consumeLongPress()) {
            actions.press(settings.longPressAction)
            fed = true
        }
        if (settings.releaseAction in actions && element.consumeRelease()) {
            actions.press(settings.releaseAction)
            fed = true
        }
        return feedAim(element, actions, fired) || fed
    }

    /**
     * Pushes an aimed button's aim to its `aimAction` while it is dragged, and on the frame it fires,
     * up being positive; a cancelled shot is taken so the game does not see it twice.
     */
    private fun feedAim(element: CanvasElement, actions: InputActions, fired: Boolean): Boolean {
        val aim = element.button?.aim
        if (aim == null || aim.aimAction !in actions) return false
        val pushing = element.aiming || fired
        if (pushing) actions.push(aim.aimAction, element.aimX, -element.aimY)
        return pushing
    }

    /** Presses [element]'s double-click action when a Button was double-clicked, and returns whether it did. */
    private fun feedDouble(element: CanvasElement, actions: InputActions): Boolean {
        if (element.kind != CanvasElementKind.Button || element.doubleAction !in actions) return false
        val doubled = element.consumeDoublePress()
        if (doubled) actions.press(element.doubleAction)
        return doubled
    }

    /** Adds what [element] does this frame to [actions], and returns whether it did anything. */
    private fun feed(element: CanvasElement, actions: InputActions): Boolean = when {
        element.kind == CanvasElementKind.Button && element.button != null -> feedActionButton(element, actions)
        element.action !in actions -> false
        else -> feedPlain(element, actions)
    }

    /** A Joystick's deflection, or a plain Button's hold and tap, added to the action it names. */
    private fun feedPlain(element: CanvasElement, actions: InputActions): Boolean =
        when (element.kind) {
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

/** How far a dp of look swipe turns the camera, in radians: a thumb across a phone is about a half turn. */
private const val LOOK_RADIANS_PER_DP = 0.008f
