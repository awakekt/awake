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
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.ui.canvas.CanvasButtonWidget
import com.awakekt.awake.ui.canvas.CanvasElement
import com.awakekt.awake.ui.canvas.CanvasJoystickWidget
import com.awakekt.awake.ui.canvas.CanvasLookAreaWidget
import com.awakekt.awake.ui.canvas.CanvasRect

/** A scene without authored input starts reading it as soon as a runtime HUD supplies controls. */
internal class RuntimeCanvasInputSystem(input: () -> GameplayInput) : System {
    private val player = PlayerInputSystem(input)

    override fun update(world: World, delta: Float) {
        if (world.family<InputActions>().size > 0) {
            player.update(world, delta)
            return
        }
        // Do not synchronize an empty canvas: the compatibility sync has temporary bookkeeping,
        // and an idle/headless scene must keep its allocation-free steady state.
        if (world.family<CanvasElement>().size > 0 || world.family<CanvasRect>().size > 0) world.syncCanvasElements()
        if (world.needsCanvasActions()) player.update(world, delta)
    }
}

/** Local UI buttons with no named game action must not create keyboard/movement defaults. */
private fun World.needsCanvasActions(): Boolean =
    family<CanvasLookAreaWidget>().size > 0 || hasStickActions() || hasButtonActions()

private fun World.hasStickActions(): Boolean {
    val sticks = family<CanvasJoystickWidget>()
    for (index in 0 until sticks.size) {
        val stick = sticks.componentAt(index)
        if (stick.action.isNotEmpty() || !stick.settings?.sprintAction.isNullOrEmpty()) return true
    }
    return false
}

private fun World.hasButtonActions(): Boolean {
    val buttons = family<CanvasButtonWidget>()
    for (index in 0 until buttons.size) {
        if (buttons.componentAt(index).namesAction()) return true
    }
    return false
}

private fun CanvasButtonWidget.namesAction(): Boolean {
    if (action.isNotEmpty() || doubleAction.isNotEmpty()) return true
    return !settings?.longPressAction.isNullOrEmpty() || !settings?.releaseAction.isNullOrEmpty() || !settings?.aim?.aimAction.isNullOrEmpty()
}

/** Keeps idle scenes out of the canvas action reader, including its compatibility lookups. */
internal class RuntimeCanvasActionsSystem : System {
    private val actions = CanvasActionSystem()

    override fun update(world: World, delta: Float) {
        if (world.family<CanvasElement>().size == 0 && world.family<CanvasRect>().size == 0) return
        world.syncCanvasElements()
        actions.update(world, delta)
    }
}
