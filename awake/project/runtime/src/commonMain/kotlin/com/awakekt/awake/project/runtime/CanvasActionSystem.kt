/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.syncCanvasElements
import com.awakekt.awake.scene.controls.camera.ActiveCamera
import com.awakekt.awake.scene.controls.camera.CameraRig
import com.awakekt.awake.scene.controls.camera.turnBy
import com.awakekt.awake.scene.controls.camera.zoomBy
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.controls.movement.MovementActions
import com.awakekt.awake.scene.controls.movement.steerPlayers
import com.awakekt.awake.ui.canvas.CanvasButtonWidget
import com.awakekt.awake.ui.canvas.CanvasJoystickWidget
import com.awakekt.awake.ui.canvas.CanvasLook
import com.awakekt.awake.ui.canvas.CanvasLookAreaWidget
import com.awakekt.awake.ui.canvas.CanvasPointerState
import kotlin.math.ln

/** The canvas action a Joystick steers the player with. */
@Deprecated("The move input action", ReplaceWith("MovementActions.MOVE", "com.awakekt.awake.scene.controls.movement.MovementActions"))
const val MOVE_ACTION = MovementActions.MOVE

/** The canvas action a Button makes the player jump with, for as long as it is held. */
@Deprecated("The jump input action", ReplaceWith("MovementActions.JUMP", "com.awakekt.awake.scene.controls.movement.MovementActions"))
const val JUMP_ACTION = MovementActions.JUMP

/**
 * Adds the scene's canvas controls to its input actions, after the keyboard, then sets the players'
 * intent again: a joystick deflected steers the axis action it names, up being positive, and a button
 * holds and presses the button action it names, and presses its `doubleAction` on a double-click. A
 * `move` joystick, a `jump` button and a `run` button so steer, jump and run as the keys do. A
 * joystick pushed past its sprint threshold also holds its `sprintAction`, so one thumb can both
 * steer and run. A button with settings also presses its `longPressAction` on a long press and its
 * `releaseAction` when let go, and a disabled or cooling one does nothing. An aimed button pushes its
 * aim to its `aimAction` while it is dragged and on the frame it fires. A look area's swipe turns the
 * active camera rig and its pinch zooms it, and each is pushed to the axis actions it names.
 *
 * It reads each control by its components, its widget and its [CanvasPointerState], so it never asks
 * an element what kind it is. An element naming no action of the scene is left to the game, its
 * presses unconsumed. Where the controls sit and how big they are is the scene's to decide.
 */
class CanvasActionSystem : System {
    private var touched = false

    override fun update(world: World, delta: Float) {
        // A control added or changed by code this frame is read as it is now.
        world.syncCanvasElements()
        val actions = world.inputActions()
        world.family<CanvasLookAreaWidget, CanvasPointerState>().forEach { _, area, pointer -> look(world, area, pointer, actions) }
        if (actions == null) return
        touched = false
        world.family<CanvasJoystickWidget, CanvasPointerState>().forEach { _, stick, pointer ->
            if (feedStick(stick, pointer, actions)) touched = true
            if (feedSprint(stick, pointer, actions)) touched = true
        }
        world.family<CanvasButtonWidget, CanvasPointerState>().forEach { _, button, pointer ->
            if (feedButton(button, pointer, actions)) touched = true
            if (feedDouble(button, pointer, actions)) touched = true
        }
        if (touched) world.steerPlayers(actions)
    }

    /**
     * A look area's swipe and pinch since last frame: pushed to its `action` (up positive) and its
     * `zoomAction` when the scene declares them, and turning and zooming the active camera rig while
     * `turnsCamera`.
     */
    private fun look(world: World, area: CanvasLookAreaWidget, pointer: CanvasPointerState, actions: InputActions?) {
        val settings = area.settings ?: CanvasLook.Default
        val look = pointer.consumeLook()
        val zoom = pointer.consumeZoom()
        val looked = look.x != 0f || look.y != 0f
        if (looked && actions != null && area.action in actions) actions.push(area.action, look.x, -look.y)
        if (zoom != 1f && actions != null && settings.zoomAction in actions) actions.push(settings.zoomAction, ln(zoom), 0f)
        if (!settings.turnsCamera || (!looked && zoom == 1f)) return
        world.queryEach(CameraRig::class, ActiveCamera::class) { _, rig, _ ->
            rig.turnBy(look.x * LOOK_RADIANS_PER_DP, -look.y * LOOK_RADIANS_PER_DP)
            rig.zoomBy(zoom)
        }
    }

    /** A joystick's deflection added to the axis action it names, and whether it was deflected. */
    private fun feedStick(stick: CanvasJoystickWidget, pointer: CanvasPointerState, actions: InputActions): Boolean {
        if (stick.action !in actions) return false
        val deflected = pointer.stickX != 0f || pointer.stickY != 0f
        if (deflected) actions.push(stick.action, pointer.stickX, -pointer.stickY)
        return deflected
    }

    /**
     * Holds a joystick's sprint action while its knob is past its sprint threshold, pressing it on the
     * frame it gets there, and returns whether it did.
     */
    private fun feedSprint(stick: CanvasJoystickWidget, pointer: CanvasPointerState, actions: InputActions): Boolean {
        val sprint = stick.settings?.sprintAction
        if (sprint.isNullOrEmpty() || sprint !in actions) return false
        if (pointer.consumeSprintStart()) actions.press(sprint)
        if (pointer.sprinting) actions.hold(sprint)
        return pointer.sprinting
    }

    /** A plain button's hold and tap added to the action it names, or an action button's as [feedActionButton] adds them. */
    private fun feedButton(button: CanvasButtonWidget, pointer: CanvasPointerState, actions: InputActions): Boolean = when {
        button.settings != null -> feedActionButton(button, pointer, actions)
        button.action !in actions -> false
        else -> feedPress(button, pointer, actions)
    }

    /** Holds [button]'s action while it is held and presses it on a tap, and returns whether it did either. */
    private fun feedPress(button: CanvasButtonWidget, pointer: CanvasPointerState, actions: InputActions): Boolean {
        val held = pointer.isHeld
        val pressed = pointer.consumePress()
        if (held) actions.hold(button.action)
        if (pressed) actions.press(button.action)
        return held || pressed
    }

    /**
     * An action button: held while held, pressed when it fires, and its long-press and release
     * actions pressed when those happen, each only when the scene declares it, so one the scene does
     * not is left for the game to read.
     */
    private fun feedActionButton(button: CanvasButtonWidget, pointer: CanvasPointerState, actions: InputActions): Boolean {
        val settings = checkNotNull(button.settings)
        var fed = false
        var fired = false
        if (button.action in actions) {
            val held = pointer.isHeld
            fired = pointer.consumePress()
            if (held) actions.hold(button.action)
            if (fired) actions.press(button.action)
            fed = held || fired
        }
        if (settings.longPressAction in actions && pointer.consumeLongPress()) {
            actions.press(settings.longPressAction)
            fed = true
        }
        if (settings.releaseAction in actions && pointer.consumeRelease()) {
            actions.press(settings.releaseAction)
            fed = true
        }
        return feedAim(button, pointer, actions, fired) || fed
    }

    /**
     * Pushes an aimed button's aim to its `aimAction` while it is dragged, and on the frame it fires,
     * up being positive.
     */
    private fun feedAim(button: CanvasButtonWidget, pointer: CanvasPointerState, actions: InputActions, fired: Boolean): Boolean {
        val aim = button.settings?.aim
        if (aim == null || aim.aimAction !in actions) return false
        val pushing = pointer.aiming || fired
        if (pushing) actions.push(aim.aimAction, pointer.aimX, -pointer.aimY)
        return pushing
    }

    /** Presses a button's double-click action when it was double-clicked, and returns whether it did. */
    private fun feedDouble(button: CanvasButtonWidget, pointer: CanvasPointerState, actions: InputActions): Boolean {
        if (button.doubleAction !in actions) return false
        val doubled = pointer.consumeDoublePress()
        if (doubled) actions.press(button.doubleAction)
        return doubled
    }
}

/** How far a dp of look swipe turns the camera, in radians: a thumb across a phone is about a half turn. */
private const val LOOK_RADIANS_PER_DP = 0.008f
