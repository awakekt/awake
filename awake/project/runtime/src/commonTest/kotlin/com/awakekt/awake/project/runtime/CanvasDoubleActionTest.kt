/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.core.input.ActionTrigger
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.CanvasElement
import com.awakekt.awake.scene.canvas.CanvasElementKind
import com.awakekt.awake.scene.canvas.SceneCanvas
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A Button's double-click presses the input action its `doubleAction` names. */
class CanvasDoubleActionTest {
    @Test
    fun aDoubleClickPressesItsAction() {
        val world = World()
        val actions = InputActions(listOf(ButtonAction("equip", trigger = ActionTrigger.Press)))
        world.add(world.create(), actions)
        world.add(
            world.create(),
            CanvasElement().apply {
                kind = CanvasElementKind.Button
                offsetX = 10f
                offsetY = 10f
                width = 40f
                height = 20f
                doubleAction = "equip"
            },
        )
        val host = ComposeHost()
        val system = CanvasActionSystem()
        fun frame(pressed: Boolean = false, down: Boolean = false, released: Boolean = false) {
            host.frame(FrameInput(200, 100, pointerX = 30, pointerY = 20, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                SceneCanvas(world)
            }
        }
        fun clickAndRun() {
            actions.beginFrame()
            frame()
            frame(pressed = true, down = true)
            frame(released = true)
            system.update(world, DELTA)
        }

        clickAndRun()
        assertFalse(actions.wasPressed("equip"), "one click is no double-click")

        clickAndRun()
        assertTrue(actions.wasPressed("equip"))
    }

    private companion object {
        const val DELTA = 1f / 60f
    }
}
