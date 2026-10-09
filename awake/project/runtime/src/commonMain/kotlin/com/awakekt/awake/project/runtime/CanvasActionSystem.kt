/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.ecs.System
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.CanvasElementKind
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.controls.movement.RunMode

/** The canvas action a Joystick steers the player with. */
const val MOVE_ACTION = "move"

/** The canvas action a Button makes the player jump with, for as long as it is held. */
const val JUMP_ACTION = "jump"

/**
 * The canvas action a Button runs the player with: a [RunMode.Hold] player runs while it is held, and
 * each press switches a [RunMode.Toggle] one between walking and running, as its run key does.
 */
const val RUN_ACTION = "run"

/**
 * Turns the scene's canvas controls into movement intent, after the keyboard: a [MOVE_ACTION]
 * Joystick steers while it is deflected, up being forward, a held [JUMP_ACTION] Button jumps, and a
 * [RUN_ACTION] Button runs.
 * Where they sit and how big they are is the scene's to decide.
 */
class CanvasActionSystem : System {
    private val asked = CanvasAsks()

    override fun update(world: World, delta: Float) {
        asked.read(world)
        if (!asked.anything) return
        world.queryEach(MovementControl::class) { _, control ->
            if (control.driver == MovementDriver.Player) asked.applyTo(control)
        }
    }
}

/** What the scene's canvas controls ask of the player this frame. */
private class CanvasAsks {
    private var moveX = 0f
    private var moveZ = 0f
    private var steering = false
    private var jumping = false
    private var runHeld = false
    private var runPressed = false

    val anything: Boolean get() = steering || jumping || runHeld || runPressed

    fun read(world: World) {
        steering = false
        jumping = false
        runHeld = false
        runPressed = false
        world.family<CanvasElement>().forEach { _, element -> read(element) }
    }

    private fun read(element: CanvasElement) {
        when (element.action) {
            MOVE_ACTION -> if (element.kind == CanvasElementKind.Joystick && (element.stickX != 0f || element.stickY != 0f)) {
                moveX = element.stickX
                moveZ = -element.stickY
                steering = true
            }
            JUMP_ACTION -> if (element.kind == CanvasElementKind.Button && element.isHeld) jumping = true
            RUN_ACTION -> if (element.kind == CanvasElementKind.Button) {
                if (element.isHeld) runHeld = true
                if (element.consumePress()) runPressed = true
            }
        }
    }

    fun applyTo(control: MovementControl) {
        if (steering) {
            control.moveX = moveX
            control.moveZ = moveZ
        }
        if (jumping) control.jump = true
        when (control.runMode) {
            RunMode.Hold -> if (runHeld) control.run = true
            RunMode.Toggle -> if (runPressed) control.run = !control.run
        }
    }
}
