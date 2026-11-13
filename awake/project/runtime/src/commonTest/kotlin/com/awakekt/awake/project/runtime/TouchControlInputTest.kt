/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.foundation.clickable
import com.awakekt.awake.compose.foundation.layout.Row
import com.awakekt.awake.compose.foundation.layout.Spacer
import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.game.TouchAim
import com.awakekt.awake.compose.game.TouchAimState
import com.awakekt.awake.compose.game.TouchStick
import com.awakekt.awake.compose.game.TouchStickState
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.PointerFrame
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.ActionTrigger
import com.awakekt.awake.core.input.AxisAction
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.steerPlayers
import com.awakekt.awake.ui.canvas.CanvasJoystickWidget
import com.awakekt.awake.ui.canvas.CanvasRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val EPS = 0.001f
private const val DELTA = 1f / 60f

/**
 * A Compose stick and aim, dragged through the Compose host's pointer path, move the player and fire
 * the actions the canvas controls do, with the game's code unchanged.
 */
class TouchControlInputTest {
    private val actions = InputActions(
        listOf(AxisAction("move"), AxisAction("aim"), ButtonAction("fire", trigger = ActionTrigger.Press)),
    )
    private val world = World().apply { add(create(), actions) }
    private val player = MovementControl().also { world.add(world.create(), it) }
    private val stick = TouchStickState()
    private val aim = TouchAimState()
    private val composeHost = ComposeHost()
    private val canvasHost = ComposeHost()
    private var buttonTaps = 0
    private val bridge = TouchControlInputSystem()
    private val canvasActions = CanvasActionSystem()

    init {
        world.add(world.create(), TouchStickInput(stick))
        val entity = world.create()
        world.add(entity, CanvasRect().apply { offsetX = 0f; offsetY = 0f; width = 100f; height = 100f })
        world.add(entity, CanvasJoystickWidget().apply { action = "move" })
    }

    /** One frame of the Compose controls, then the actions systems, as the capability plan orders them. */
    private fun composeFrame(vararg touches: PointerFrame) {
        composeHost.frame(FrameInput(WIDTH, HEIGHT, pointers = touches.toList())) {
            Row {
                TouchStick(Modifier.size(100.dp), stick)
                Spacer(Modifier.size(40.dp).clickable { buttonTaps++ })
                TouchAim(Modifier.size(100.dp), aim, radius = 50.dp)
            }
        }
        newFrame()
        canvasActions.update(world, DELTA)
        bridge.update(world, DELTA)
    }

    private fun canvasFrame(vararg touches: PointerFrame) {
        canvasHost.frame(FrameInput(WIDTH, HEIGHT, pointers = touches.toList())) { SceneCanvas(world) }
        newFrame()
        canvasActions.update(world, DELTA)
        bridge.update(world, DELTA)
    }

    /** What the player-input system does first each frame: clear the actions and steer, so a released control zeroes the player. */
    private fun newFrame() {
        actions.beginFrame()
        world.steerPlayers(actions)
    }

    private fun down(id: Long, x: Int, y: Int, pressed: Boolean = false) = PointerFrame(id, x, y, down = true, pressed = pressed)

    private fun up(id: Long, x: Int, y: Int) = PointerFrame(id, x, y, down = false, released = true)

    @Test
    fun draggingTheComposeStickSteersAsTheCanvasJoystickDoesForTheSameGesture() {
        canvasFrame()
        canvasFrame(down(1, 50, 50, pressed = true))
        canvasFrame(down(1, 75, 30))
        val canvas = actions.axisX("move") to actions.axisY("move")
        val canvasMove = player.moveX to player.moveZ
        canvasFrame(up(1, 75, 30))

        composeFrame()
        composeFrame(down(1, 50, 50, pressed = true))
        composeFrame(down(1, 75, 30))

        assertEquals(canvas.first, actions.axisX("move"), EPS)
        assertEquals(canvas.second, actions.axisY("move"), EPS)
        assertEquals(canvasMove.first, player.moveX, EPS)
        assertEquals(canvasMove.second, player.moveZ, EPS)
        assertTrue(actions.axisX("move") > 0.4f && actions.axisY("move") > 0.2f, "right and up are positive on the action")
    }

    @Test
    fun releasingTheComposeStickReturnsTheMovementToZero() {
        composeFrame()
        composeFrame(down(1, 50, 50, pressed = true))
        composeFrame(down(1, 100, 50))
        assertEquals(1f, player.moveX, EPS)

        composeFrame(up(1, 100, 50))

        assertEquals(0f, actions.axisX("move"), EPS)
        assertEquals(0f, player.moveX, EPS)
        assertEquals(0f, player.moveZ, EPS)
    }

    @Test
    fun aSecondFingerOnTheButtonLeavesTheStickAlone() {
        composeFrame()
        composeFrame(down(1, 50, 50, pressed = true))
        composeFrame(down(1, 75, 50))
        composeFrame(down(1, 75, 50), down(2, 120, 20, pressed = true))
        composeFrame(down(1, 75, 50), up(2, 120, 20))

        assertEquals(1, buttonTaps)
        assertEquals(0.5f, actions.axisX("move"), EPS)
        assertEquals(0.5f, player.moveX, EPS)
    }

    @Test
    fun theAimReachesItsActionWhileDraggedAndOnRelease() {
        world.add(world.create(), TouchAimInput(aim, aimAction = "aim", fireAction = "fire"))
        composeFrame()
        composeFrame(down(1, 190, 50, pressed = true)) // the aim control starts at x = 140
        composeFrame(down(1, 190, 0))
        assertEquals(0f, actions.axisX("aim"), EPS)
        assertEquals(1f, actions.axisY("aim"), EPS, "dragging up aims up")
        assertFalse(actions.wasPressed("fire"))

        composeFrame(up(1, 190, 0))

        assertEquals(1f, actions.axisY("aim"), EPS, "the aim it was let go with is pushed on the frame it fires")
        assertTrue(actions.wasPressed("fire"))
        composeFrame()
        assertFalse(actions.wasPressed("fire"), "the fire is for one frame")
        assertEquals(0f, actions.axisY("aim"), EPS)
    }

    @Test
    fun aComposeStickAndACanvasJoystickCombineAndTheDeflectedComposeStickWins() {
        canvasFrame()
        canvasFrame(down(1, 50, 50, pressed = true))
        canvasFrame(down(1, 100, 50))
        assertEquals(1f, actions.axisX("move"), EPS, "the canvas joystick alone, with the Compose stick at rest")

        composeFrame()
        composeFrame(down(1, 50, 50, pressed = true))
        composeFrame(down(1, 0, 50))
        canvasFrame(down(1, 100, 50))
        // Both deflected, canvas read first and Compose second: the Compose stick sets the action.
        assertEquals(-1f, actions.axisX("move"), EPS)
        composeFrame(up(1, 0, 50))
        canvasFrame(down(1, 100, 50))
        assertEquals(1f, actions.axisX("move"), EPS, "the canvas joystick keeps the action once the Compose stick lets go")
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 200
    }
}
