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

/** The canvas action a Joystick steers the player with. */
const val MOVE_ACTION = "move"

/** The canvas action a Button makes the player jump with, for as long as it is held. */
const val JUMP_ACTION = "jump"

/**
 * Turns the scene's canvas controls into movement intent, after the keyboard: a [MOVE_ACTION]
 * Joystick steers while it is deflected, up being forward, and a held [JUMP_ACTION] Button jumps.
 * Where they sit and how big they are is the scene's to decide.
 */
class CanvasActionSystem : System {
    override fun update(world: World, delta: Float) {
        var moveX = 0f
        var moveZ = 0f
        var steering = false
        var jumping = false
        world.family<CanvasElement>().forEach { _, element ->
            val deflected = element.stickX != 0f || element.stickY != 0f
            if (element.action == MOVE_ACTION && element.kind == CanvasElementKind.Joystick && deflected) {
                moveX = element.stickX
                moveZ = -element.stickY
                steering = true
            }
            if (element.action == JUMP_ACTION && element.kind == CanvasElementKind.Button && element.isHeld) {
                jumping = true
            }
        }
        if (!steering && !jumping) return
        world.queryEach(MovementControl::class) { _, control ->
            if (control.driver != MovementDriver.Player) return@queryEach
            if (steering) {
                control.moveX = moveX
                control.moveZ = moveZ
            }
            if (jumping) control.jump = true
        }
    }
}
