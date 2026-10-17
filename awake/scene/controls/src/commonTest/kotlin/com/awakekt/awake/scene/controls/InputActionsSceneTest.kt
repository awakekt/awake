/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.ActionTrigger
import com.awakekt.awake.core.input.ButtonBinding
import com.awakekt.awake.core.input.ExtraModifiers
import com.awakekt.awake.core.input.GamepadButton
import com.awakekt.awake.core.input.GamepadChord
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.KeyChord
import com.awakekt.awake.core.input.KeyModifier
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.binding.SceneComponentRegistry
import com.awakekt.awake.scene.binding.fromWorld
import com.awakekt.awake.scene.binding.instantiate
import com.awakekt.awake.scene.controls.input.SceneAxisAction
import com.awakekt.awake.scene.controls.input.SceneButtonAction
import com.awakekt.awake.scene.controls.input.SceneInputActions
import com.awakekt.awake.scene.controls.input.inputActions
import com.awakekt.awake.scene.controls.movement.MovementControl
import com.awakekt.awake.scene.controls.movement.PlayerInputSystem
import com.awakekt.awake.scene.controls.movement.registerControls
import com.awakekt.awake.scene.document.SceneDocument
import com.awakekt.awake.scene.document.SceneLoader
import com.awakekt.awake.scene.document.SceneNode
import com.awakekt.awake.scene.runtime.DefaultSceneComponentResolvers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A player moves, jumps and runs by the scene's input actions, which the scene binds. */
class InputActionsSceneTest {
    private val registry = SceneComponentRegistry().registerControls()

    init {
        DefaultSceneComponentResolvers.install()
    }

    @Test
    fun aSceneThatBindsNothingMovesJumpsAndRunsAsBefore() {
        val (world, control) = player()

        frame(world, down = setOf(Key.W, Key.Space, Key.Shift), pressed = setOf(Key.W, Key.Space, Key.Shift))
        assertEquals(1f, control.moveZ)
        assertTrue(control.jump)
        assertTrue(control.run, "Shift held runs")
        frame(world)
        assertFalse(control.run, "letting go walks")
    }

    @Test
    fun aSceneRebindsMoveJumpAndRun() {
        val (world, control) = player(
            """{ "component": "input_actions", "actions": [
                { "type": "axis", "name": "move", "up": ["I"], "down": ["K"], "left": ["J"], "right": ["L"] },
                { "type": "button", "name": "jump", "keys": ["F"] },
                { "type": "button", "name": "run", "keys": ["R"] }
            ] }""",
        )

        frame(world, down = setOf(Key.W, Key.Space, Key.Shift))
        assertEquals(0f, control.moveZ, "the default keys no longer move")
        assertFalse(control.jump)
        assertFalse(control.run)
        frame(world, down = setOf(Key.L, Key.F, Key.R))
        assertEquals(1f, control.moveX)
        assertTrue(control.jump)
        assertTrue(control.run)
    }

    @Test
    fun aToggledRunCanStartRunningAndEachPressSwitchesIt() {
        val (world, control) = player(
            """{ "component": "input_actions", "actions": [
                { "type": "button", "name": "run", "keys": ["X"], "trigger": "Toggle", "startsOn": true }
            ] }""",
        )
        frame(world)
        assertTrue(control.run, "startsOn runs before any press")

        frame(world, down = setOf(Key.X), pressed = setOf(Key.X))
        assertFalse(control.run, "a press walks")
        frame(world, down = setOf(Key.X))
        assertFalse(control.run, "holding the key does not switch it again")
        frame(world, down = setOf(Key.X), pressed = setOf(Key.X))
        assertTrue(control.run, "the next press runs again")
    }

    @Test
    fun typingStopsTheHeldActionsAndLeavesAToggleAsItWas() {
        val (world, control) = player(
            """{ "component": "input_actions", "actions": [
                { "type": "button", "name": "run", "keys": ["X"], "trigger": "Toggle", "startsOn": true }
            ] }""",
        )
        frame(world, down = setOf(Key.W, Key.Space))

        frame(world, down = setOf(Key.W, Key.Space, Key.X), pressed = setOf(Key.X), typing = true)

        assertEquals(0f, control.moveZ, "typing 'w' into a text field does not walk the player")
        assertFalse(control.jump)
        assertTrue(control.run, "an X typed into a text field does not switch the toggle")
    }

    @Test
    fun anAgentIsLeftToItsCode() {
        val (world, control) = player(control = """{ "component": "movement_control", "driver": "Agent" }""")
        control.moveX = 0.5f

        frame(world, down = setOf(Key.W, Key.Shift))

        assertEquals(0.5f, control.moveX)
        assertFalse(control.run)
    }

    @Test
    fun aGameReadsAnActionOfItsOwn() {
        val (world, _) = player(
            """{ "component": "input_actions", "actions": [ { "type": "button", "name": "interact", "keys": ["E"], "trigger": "Press" } ] }""",
        )

        frame(world, down = setOf(Key.E), pressed = setOf(Key.E))
        val actions = assertNotNull(world.inputActions())
        assertTrue(actions.isActive("interact"))
        assertTrue("jump" in actions, "the defaults it does not rebind stay")
        frame(world, down = setOf(Key.E))
        assertFalse(actions.isActive("interact"), "a Press is active for one frame")
    }

    @Test
    fun aSceneWithoutInputActionsGetsTheDefaultsInTheWorld() {
        val (world, _) = player()
        frame(world)

        val actions = assertNotNull(world.inputActions())
        assertTrue("move" in actions && "jump" in actions && "run" in actions)
    }

    @Test
    fun aKeyBoundToTwoActionsIsRefused() {
        val runOnSpace = SceneInputActions(listOf(SceneButtonAction("run", keys = setOf(Key.Space))))
        assertEquals(listOf("input_actions binds Space to both jump and run"), runOnSpace.validate("p").map { it.message })

        val rebound = SceneInputActions(
            listOf(SceneButtonAction("jump", keys = setOf(Key.F)), SceneButtonAction("run", keys = setOf(Key.Space))),
        )
        assertEquals(emptyList(), rebound.validate("p"), "Space is free once jump moves off it")
    }

    @Test
    fun badActionsAreRefused() {
        val issues = SceneInputActions(
            listOf(
                SceneButtonAction("move"),
                SceneAxisAction("jump"),
                SceneButtonAction("open door"),
                SceneButtonAction("dash", startsOn = true),
                SceneButtonAction("dash", keys = setOf(Key.Unknown)),
                SceneAxisAction("look", up = setOf(Key.I), down = setOf(Key.I)),
            ),
        ).validate("p").map { it.message }

        listOf(
            "input_actions.move steers the player, so its type is axis",
            "input_actions.jump is a button, so its type is button",
            "input_actions: \"open door\" is not an action name",
            "input_actions.dash.startsOn needs \"trigger\": \"Toggle\"",
            "input_actions.dash: Unknown is not a key that can be pressed",
            "input_actions names \"dash\" more than once",
            "input_actions.look binds I twice",
        ).forEach { expected -> assertTrue(issues.any { it.startsWith(expected) }, "$expected in $issues") }
    }

    @Test
    fun theSceneActionsRoundTripWithoutTheDefaults() {
        val authored = SceneInputActions(
            listOf(
                SceneButtonAction("run", keys = setOf(Key.X), trigger = ActionTrigger.Toggle, startsOn = true),
                SceneButtonAction("interact", keys = setOf(Key.E), trigger = ActionTrigger.Press),
            ),
        )
        val (world, _) = player(
            """{ "component": "input_actions", "actions": [
                { "type": "button", "name": "run", "keys": ["X"], "trigger": "Toggle", "startsOn": true },
                { "type": "button", "name": "interact", "keys": ["E"], "trigger": "Press" }
            ] }""",
        )

        val exported = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
            .nodes.flatMap { it.components }.filterIsInstance<SceneInputActions>()

        assertEquals(listOf(authored), exported)
    }

    @Test
    fun aSceneBindsAChordThatTakesItsKeyFromAPlainOne() {
        val (world, _) = player(
            """{ "component": "input_actions", "actions": [
                { "type": "button", "name": "slot1", "keys": ["Digit1"], "trigger": "Press" },
                { "type": "button", "name": "slot11", "trigger": "Press",
                  "chords": [ { "type": "key", "key": "Digit1", "modifiers": ["Ctrl"] } ] }
            ] }""",
        )
        val actions = assertNotNull(world.inputActions())

        frame(world, down = setOf(Key.Ctrl, Key.Digit1), pressed = setOf(Key.Ctrl, Key.Digit1))
        assertTrue(actions.isActive("slot11"))
        assertFalse(actions.isActive("slot1"), "Ctrl+1 does not also fire 1")
        frame(world, down = setOf(Key.Digit1), pressed = setOf(Key.Digit1))
        assertTrue(actions.isActive("slot1"))
        assertFalse(actions.isActive("slot11"))
    }

    @Test
    fun aChordBoundTwiceIsRefusedLikeAKey() {
        val ctrl1 = KeyChord(Key.Digit1, setOf(KeyModifier.Ctrl))
        fun issues(vararg actions: SceneButtonAction) = SceneInputActions(actions.toList()).validate("p").map { it.message }

        assertEquals(
            listOf("input_actions binds Ctrl+Digit1 to both slot1 and slot2"),
            issues(SceneButtonAction("slot1", chords = setOf(ctrl1)), SceneButtonAction("slot2", chords = setOf(ctrl1))),
        )
        assertEquals(
            listOf("input_actions binds Space to both jump and dash"),
            issues(SceneButtonAction("dash", chords = setOf(KeyChord(Key.Space)))),
            "a chord with no modifiers is the plain key",
        )
        assertEquals(
            listOf("input_actions.slot1 binds Ctrl+Digit1 twice"),
            issues(SceneButtonAction("slot1", chords = setOf(ctrl1, ctrl1.copy(extraModifiers = ExtraModifiers.Allow)))),
        )
        assertEquals(
            listOf("input_actions binds LeftShoulder+South to both a and b"),
            issues(
                SceneButtonAction("a", chords = setOf(GamepadChord(GamepadButton.South, setOf(GamepadButton.LeftShoulder)))),
                SceneButtonAction("b", chords = setOf(GamepadChord(GamepadButton.South, setOf(GamepadButton.LeftShoulder)))),
            ),
        )
        assertEquals(
            emptyList(),
            issues(
                SceneButtonAction("slot1", keys = setOf(Key.Digit1), chords = setOf(GamepadChord(GamepadButton.South))),
                SceneButtonAction("slot11", chords = setOf(ctrl1, KeyChord(Key.Space, setOf(KeyModifier.Ctrl)))),
            ),
            "a chord is free of the plain key it adds modifiers to",
        )
    }

    @Test
    fun exportingKeepsTheChordsAPlayerReboundAtRunTime() {
        val (world, _) = player(
            """{ "component": "input_actions", "actions": [ { "type": "button", "name": "interact", "keys": ["E"] } ] }""",
        )
        val actions = assertNotNull(world.inputActions())
        val ctrlE = KeyChord(Key.E, setOf(KeyModifier.Ctrl))
        val padJump = GamepadChord(GamepadButton.South)
        actions.rebind("interact", ButtonBinding(chords = setOf(ctrlE)))
        actions.rebind("jump", ButtonBinding(keys = setOf(Key.Space), chords = setOf(padJump)))

        val exported = SceneLoader.fromWorld(world, name = "x", componentRegistry = registry)
            .nodes.flatMap { it.components }.filterIsInstance<SceneInputActions>().single()

        assertEquals(
            listOf(
                SceneButtonAction("jump", keys = setOf(Key.Space), chords = setOf(padJump)),
                SceneButtonAction("interact", chords = setOf(ctrlE)),
            ),
            exported.actions.sortedBy { it.name != "jump" },
            "a rebound default is the scene's own, so it is kept",
        )
    }

    @Test
    fun chordsRoundTripThroughTheSceneJson() {
        val actions = SceneInputActions(
            listOf(
                SceneButtonAction(
                    "slot11",
                    trigger = ActionTrigger.Press,
                    chords = setOf(
                        KeyChord(Key.Digit1, setOf(KeyModifier.Ctrl, KeyModifier.Shift), ExtraModifiers.Allow),
                        GamepadChord(GamepadButton.South, setOf(GamepadButton.LeftShoulder)),
                    ),
                ),
            ),
        )
        val document = SceneDocument(name = "x", nodes = listOf(SceneNode(name = "Controls", components = listOf(actions))))

        val text = SceneLoader.encode(document)

        assertTrue("\"type\":\"gamepad\"" in text, text)
        assertEquals(document, SceneLoader.decode(text))
    }

    @Test
    fun aSceneWrittenBeforeChordsReadsWithNone() {
        val text = """{ "version": 1, "name": "x", "nodes": [ { "name": "Controls", "components": [
            { "component": "input_actions", "actions": [ { "type": "button", "name": "interact", "keys": ["E"] } ] }
        ] } ] }"""

        val decoded = SceneLoader.decode(text).nodes.single().components.single()

        assertEquals(SceneInputActions(listOf(SceneButtonAction("interact", keys = setOf(Key.E)))), decoded)
    }

    private fun player(
        vararg components: String,
        control: String = """{ "component": "movement_control" }""",
    ): Pair<World, MovementControl> {
        val world = World()
        val all = (listOf(control) + components).joinToString(", ")
        val scene = """{ "version": 1, "name": "x", "nodes": [ { "name": "Player", "components": [ $all ] } ] }"""
        SceneLoader.decode(scene).instantiate(world = world, componentRegistry = registry)
        var found: MovementControl? = null
        world.queryEach(MovementControl::class) { _, control -> found = control }
        return world to found!!
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
