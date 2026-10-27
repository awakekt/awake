/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.core.input.ActionTrigger
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.canvas.SceneCanvas
import com.awakekt.awake.ui.canvas.CanvasButtonWidget
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasPointerState
import com.awakekt.awake.ui.canvas.CanvasRect
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Components changed before the canvas draws must already drive the current frame's actions. */
class CanvasComponentActionsTest {
    @Test
    fun aComponentReplacementUsesThePendingPressWithoutConsumingUnknownActions() {
        val world = World()
        val actions = InputActions(listOf(ButtonAction("use", trigger = ActionTrigger.Press)))
        world.add(world.create(), actions)
        val entity = world.create()
        world.add(entity, CanvasRect().apply { offsetX = 0f; offsetY = 0f; width = 40f; height = 20f })
        world.add(entity, CanvasButtonWidget().apply { action = "private-action" })
        click(world)
        val pointer = assertNotNull(world.get<CanvasPointerState>(entity))
        val system = CanvasActionSystem()

        system.update(world, DELTA)
        assertFalse(actions.wasPressed("use"))
        val replacement = CanvasButtonWidget().apply { action = "use" }
        world.add(entity, replacement)
        system.update(world, DELTA)

        assertTrue(actions.wasPressed("use"), "an undeclared action left the press for the replacement widget")
        assertSame(replacement, world.get<CanvasElement>(entity)?.get(CanvasButtonWidget::class))
        assertSame(pointer, world.get<CanvasPointerState>(entity), "changing authored settings preserves live input")
        assertFalse(pointer.consumePress(), "the declared action consumes its press once")
    }

    @Test
    fun removingAWidgetBeforeTheActionPassStopsDispatchAndLeavesThePressForTheGame() {
        val world = World()
        val actions = InputActions(listOf(ButtonAction("use", trigger = ActionTrigger.Press)))
        world.add(world.create(), actions)
        val entity = world.create()
        world.add(entity, CanvasRect().apply { offsetX = 0f; offsetY = 0f; width = 40f; height = 20f })
        world.add(entity, CanvasButtonWidget().apply { action = "use" })
        click(world)
        assertNotNull(world.get<CanvasElement>(entity)).remove(CanvasButtonWidget::class)

        CanvasActionSystem().update(world, DELTA)

        assertFalse(actions.wasPressed("use"), "the previous widget must leave the queried family immediately")
        assertFalse(world.has<CanvasButtonWidget>(entity))
        assertTrue(assertNotNull(world.get<CanvasPointerState>(entity)).consumePress())
    }

    private fun click(world: World) {
        val host = ComposeHost()
        fun frame(down: Boolean = false, pressed: Boolean = false, released: Boolean = false) {
            host.frame(FrameInput(100, 100, pointerX = 10, pointerY = 10, pointerDown = down, pointerPressed = pressed, pointerReleased = released)) {
                SceneCanvas(world)
            }
        }
        frame()
        frame(down = true, pressed = true)
        frame(released = true)
    }

    private companion object {
        const val DELTA = 1f / 60f
    }
}
