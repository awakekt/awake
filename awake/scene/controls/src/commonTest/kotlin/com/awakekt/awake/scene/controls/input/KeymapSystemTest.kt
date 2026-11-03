/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.input

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.ActionTrigger
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.keymap
import com.awakekt.awake.core.input.keymapEntries
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import com.awakekt.awake.scene.controls.movement.MovementActions
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KeymapSystemTest {
    private class Opened(var count: Int = 0)

    private val world = World()
    private val opened = Opened().also { world.add(world.create(), it) }
    private val keymap = keymap<World> {
        press("window.inventory", "Inventory", Key.I) { world -> world.query(Opened::class).forEach { world.get(it, Opened::class)!!.count++ } }
        hold("attack", "Attack", Key.F)
    }

    @Test
    fun itRunsTheHandlersOfWhatWasPressedWithTheWorld() {
        var input = GameplayInput(snapshot(down = setOf(Key.I, Key.F), pressed = setOf(Key.I, Key.F)), InputOwnership())
        val system = KeymapSystem(keymap) { input }

        system.update(world, DELTA)
        input = GameplayInput(snapshot(down = setOf(Key.I, Key.F)), InputOwnership())
        system.update(world, DELTA)

        assertEquals(1, opened.count)
        assertTrue(keymap.actions.isActive("attack"), "a held action stays polled")
    }

    @Test
    fun aFocusedTextFieldKeepsItsKeysFromTheKeymap() {
        val input = GameplayInput(snapshot(down = setOf(Key.I), pressed = setOf(Key.I)), InputOwnership(isTextInputFocused = true))

        KeymapSystem(keymap, { input }) { it }.update(world, DELTA)

        assertEquals(0, opened.count, "typing I into a field does not open the inventory")
    }

    @Test
    fun aSharedKeymapAndTheScenesActionsAreOneSet() {
        val holder = world.create()
        world.add(
            holder,
            InputActions(
                MovementActions.defaults + listOf(
                    ButtonAction("interact", keys = setOf(Key.E), trigger = ActionTrigger.Press),
                    ButtonAction("window.inventory", keys = setOf(Key.Tab), trigger = ActionTrigger.Press),
                ),
            ),
        )
        val shared = world.shareKeymap(
            keymapEntries<World> {
                axis("move", "Move", up = setOf(Key.W, Key.ArrowUp), down = setOf(Key.S), left = setOf(Key.A), right = setOf(Key.D))
                press("window.inventory", "Inventory", Key.I) { world -> world.query(Opened::class).forEach { world.get(it, Opened::class)!!.count++ } }
                hold("attack", "Attack", Key.F)
            },
        )
        var input = GameplayInput(snapshot(down = setOf(Key.Tab, Key.ArrowUp), pressed = setOf(Key.Tab, Key.ArrowUp)), InputOwnership())
        val players = PlayerInputSystem { input }
        val handlers = KeymapSystem(shared) { input }

        players.update(world, DELTA)
        handlers.update(world, DELTA)

        assertSame(shared.actions, world.inputActions(), "the keymap's actions are the world's")
        assertEquals(1, opened.count, "the scene's own binding, Tab, opens the inventory, read once by PlayerInputSystem")
        assertEquals(1f, shared.actions.axisY("move"), "the keymap's move, with ArrowUp, replaced the default")
        assertTrue("interact" in shared.actions, "the scene's own actions stay")

        input = GameplayInput(snapshot(down = emptySet()), InputOwnership())
        players.update(world, DELTA)
        world.inputActions()!!.push("move", 0.6f, 0.8f) // a canvas joystick, as CanvasActionSystem pushes it
        handlers.update(world, DELTA)
        assertEquals(0.8f, shared.actions.axisY("move"), "the joystick and the keys drive one move")

        val saved = InputActionsBinding.export(world, holder, shared.actions).actions.map { it.name }
        assertEquals(listOf("window.inventory", "interact"), saved, "a saved scene keeps its own actions, not the keymap's")
    }

    private fun snapshot(down: Set<Key>, pressed: Set<Key> = emptySet()) = InputSnapshot(
        pointerX = 0f,
        pointerY = 0f,
        pointerDown = false,
        scrollDeltaX = 0f,
        scrollDeltaY = 0f,
        keysDown = down,
        keysPressed = pressed,
        keysReleased = emptySet(),
        typedText = "",
        editActions = emptyList(),
    )

    private companion object {
        const val DELTA = 0.016f
    }
}
