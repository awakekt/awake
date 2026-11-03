/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls.input

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.keymap
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.controls.GameplayInput
import kotlin.test.Test
import kotlin.test.assertEquals
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
