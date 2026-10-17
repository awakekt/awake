/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.ActionTrigger
import com.awakekt.awake.core.input.ButtonAction
import com.awakekt.awake.core.input.ButtonBinding
import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.GamepadChord
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.InputActions
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Controller buttons a host reads reach a scene's input actions through [GameplayInput]. */
class GamepadActionsTest {
    private val input = Input()

    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun gameplayInputPassesControllerButtonsThroughWhateverTheUiHolds() {
        input.setGamepadButton(GamepadButton.South, true)
        val snapshot = input.snapshot()

        listOf(
            InputOwnership(),
            InputOwnership(isTextInputFocused = true),
            InputOwnership(isCaptured = true, isModalOpen = true),
        ).forEach { claimed ->
            val gameplay = GameplayInput(snapshot, claimed)
            assertTrue(gameplay.isDown(GamepadButton.South), "$claimed")
            assertTrue(gameplay.wasPressed(GamepadButton.South), "$claimed")
            assertFalse(gameplay.isDown(GamepadButton.East))
        }
    }

    @Test
    fun aControllerChordTakesItsButtonFromThePlainOne() {
        val actions = InputActions(
            listOf(
                ButtonAction("confirm", chords = setOf(GamepadChord(GamepadButton.South)), trigger = ActionTrigger.Press),
                ButtonAction(
                    "slot1",
                    chords = setOf(GamepadChord(GamepadButton.South, setOf(GamepadButton.LeftShoulder))),
                    trigger = ActionTrigger.Press,
                ),
            ),
        )

        input.setGamepadButton(GamepadButton.LeftShoulder, true)
        input.setGamepadButton(GamepadButton.South, true)
        read(actions)
        assertTrue(actions.isActive("slot1"))
        assertFalse(actions.isActive("confirm"), "the shoulder held makes South the chord, not South alone")

        input.setGamepadButton(GamepadButton.LeftShoulder, false)
        input.setGamepadButton(GamepadButton.South, false)
        read(actions)
        input.setGamepadButton(GamepadButton.South, true)
        read(actions)
        assertTrue(actions.isActive("confirm"))
        assertFalse(actions.isActive("slot1"))
    }

    @Test
    fun aPlayerReboundToTheControllerJumpsWithIt() {
        val world = World()
        val scene = """{ "version": 1, "name": "x", "nodes": [ { "name": "Player", "components": [ { "component": "movement_control" } ] } ] }"""
        SceneLoader.decode(scene).instantiate(world = world, componentRegistry = SceneComponentRegistry().registerControls())
        var control: MovementControl? = null
        world.queryEach(MovementControl::class) { _, found -> control = found }
        val system = PlayerInputSystem { GameplayInput(input.snapshot(), InputOwnership()) }
        system.update(world, 0.016f)
        assertNotNull(world.inputActions())
            .rebind("jump", ButtonBinding(keys = setOf(Key.Space), chords = setOf(GamepadChord(GamepadButton.South))))

        input.setGamepadButton(GamepadButton.South, true)
        system.update(world, 0.016f)

        assertTrue(assertNotNull(control).jump)
        input.setGamepadButton(GamepadButton.South, false)
        system.update(world, 0.016f)
        assertFalse(assertNotNull(control).jump)
    }

    private fun read(actions: InputActions) {
        actions.beginFrame()
        actions.read(GameplayInput(input.snapshot(), InputOwnership()))
    }
}
