/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.MovementDriver
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.controls.movement.RunMode
import com.awakekt.awake.scene.controls.movement.SceneMovementControl
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Running is held on a key, or toggled by one, as the scene's movement_control says. */
class RunModeTest {
    private val registry = SceneComponentRegistry().registerControls()

    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun heldRunsOnlyWhileTheKeyIsDown() {
        val (world, control) = player("""{ "component": "movement_control" }""")

        frame(world, down = setOf(Key.Shift), pressed = setOf(Key.Shift))
        assertTrue(control.run, "Shift held runs")
        frame(world)
        assertFalse(control.run, "letting go walks")
    }

    @Test
    fun aToggleStartsRunningAndEachPressSwitches() {
        val (world, control) = player("""{ "component": "movement_control", "runMode": "Toggle", "runKey": "X" }""")
        assertTrue(control.run, "a toggle starts running")

        frame(world, down = setOf(Key.X), pressed = setOf(Key.X))
        assertFalse(control.run, "a press walks")
        frame(world, down = setOf(Key.X))
        assertFalse(control.run, "holding the key does not switch it again")
        frame(world)
        assertFalse(control.run, "walking stays after the key is up")
        frame(world, down = setOf(Key.X), pressed = setOf(Key.X))
        assertTrue(control.run, "the next press runs again")
        frame(world, down = setOf(Key.Shift), pressed = setOf(Key.Shift))
        assertTrue(control.run, "Shift is not this player's run key")
    }

    @Test
    fun typingLeavesTheToggleAsItWas() {
        val (world, control) = player("""{ "component": "movement_control", "runMode": "Toggle", "runKey": "X" }""")

        frame(world, down = setOf(Key.X), pressed = setOf(Key.X), typing = true)

        assertTrue(control.run, "an X typed into a text field neither switches to walking nor stops the run")
    }

    @Test
    fun typingStopsAHeldRun() {
        val (world, control) = player("""{ "component": "movement_control" }""")
        frame(world, down = setOf(Key.Shift), pressed = setOf(Key.Shift))

        frame(world, down = setOf(Key.Shift), typing = true)

        assertFalse(control.run, "a held run stops while the UI has the keys, as the movement does")
    }

    /** Only the player's input switches a toggle back, so an agent's control never starts running. */
    @Test
    fun anAgentWithAToggleDoesNotStartRunning() {
        val (world, control) = player("""{ "component": "movement_control", "driver": "Agent", "runMode": "Toggle" }""")
        assertFalse(control.run)

        frame(world, down = setOf(Key.Shift), pressed = setOf(Key.Shift))

        assertFalse(control.run, "player input leaves an agent alone")
    }

    @Test
    fun aRunKeyThePlayerMovesOrJumpsWithIsRefused() {
        listOf(Key.Space, Key.W, Key.ArrowUp, Key.Unknown).forEach { key ->
            assertTrue(SceneMovementControl(runKey = key).validate("p").isNotEmpty(), "$key")
        }
        assertEquals(emptyList(), SceneMovementControl(runKey = Key.X).validate("p"))
        assertEquals(emptyList(), SceneMovementControl(runKey = Key.Space, driver = MovementDriver.Agent).validate("p"), "an agent has no run key")
    }

    @Test
    fun theRunModeAndKeyRoundTrip() {
        val authored = SceneMovementControl(speed = 1.5f, runSpeed = 6f, runMode = RunMode.Toggle, runKey = Key.X)
        val (world, _) = player("""{ "component": "movement_control", "speed": 1.5, "runSpeed": 6.0, "runMode": "Toggle", "runKey": "X" }""")

        val exported = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
            .nodes.flatMap { it.components }.filterIsInstance<SceneMovementControl>()

        assertEquals(listOf(authored), exported)
    }

    private fun player(component: String): Pair<World, MovementControl> {
        val world = World()
        val scene = """{ "version": 1, "name": "x", "nodes": [ { "name": "Player", "components": [ $component ] } ] }"""
        SceneLoader.decode(scene).instantiate(world = world, componentRegistry = registry)
        var control: MovementControl? = null
        world.queryEach(MovementControl::class) { _, found -> control = found }
        return world to control!!
    }

    private fun frame(world: World, down: Set<Key> = emptySet(), pressed: Set<Key> = emptySet(), typing: Boolean = false) {
        val snapshot = InputSnapshot(
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
        PlayerInputSystem { GameplayInput(snapshot, InputOwnership(isTextInputFocused = typing)) }.update(world, 0.016f)
    }
}
