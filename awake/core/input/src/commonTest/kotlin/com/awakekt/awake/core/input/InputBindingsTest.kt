/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputBindingsTest {
    private fun actions() = InputActions(
        listOf(
            AxisAction("move", up = setOf(Key.W), down = setOf(Key.S), left = setOf(Key.A), right = setOf(Key.D)),
            ButtonAction("jump", keys = setOf(Key.Space)),
            ButtonAction("map", keys = setOf(Key.M), trigger = ActionTrigger.Toggle, startsOn = true),
            ButtonAction(
                "slot1",
                keys = setOf(Key.Digit1),
                buttons = setOf(PointerButton.Back),
                chords = setOf(
                    KeyChord(Key.Digit1, setOf(KeyModifier.Shift, KeyModifier.Ctrl), ExtraModifiers.Allow),
                    GamepadChord(GamepadButton.West, setOf(GamepadButton.LeftTrigger)),
                ),
            ),
        ),
    )

    @Test
    fun bindingsRoundTripThroughJson() {
        val bindings = actions().bindings()

        val text = Json.encodeToString(InputBindings.serializer(), bindings)

        assertEquals(bindings, Json.decodeFromString(InputBindings.serializer(), text))
    }

    @Test
    fun bindingsReadFromTypedJson() {
        val text = """
            { "actions": {
              "jump": { "type": "button", "keys": ["Space"] },
              "move": { "type": "axis", "up": ["ArrowUp"], "down": ["ArrowDown"] },
              "slot1": { "type": "button", "chords": [
                { "type": "key", "key": "Digit1", "modifiers": ["Shift"] },
                { "type": "gamepad", "button": "South", "modifiers": ["LeftShoulder"], "extraModifiers": "Allow" }
              ] }
            } }
        """.trimIndent()

        val bindings = Json.decodeFromString(InputBindings.serializer(), text)

        assertEquals(ButtonBinding(keys = setOf(Key.Space)), bindings.actions["jump"], "a single-key binding reads as it always has")
        assertEquals(AxisBinding(up = setOf(Key.ArrowUp), down = setOf(Key.ArrowDown)), bindings.actions["move"])
        assertEquals(
            ButtonBinding(
                chords = setOf(
                    KeyChord(Key.Digit1, setOf(KeyModifier.Shift)),
                    GamepadChord(GamepadButton.South, setOf(GamepadButton.LeftShoulder), ExtraModifiers.Allow),
                ),
            ),
            bindings.actions["slot1"],
        )
    }

    @Test
    fun aChordWritesOnlyWhatDiffersFromTheDefaults() {
        val text = Json.encodeToString(InputChord.serializer(), KeyChord(Key.K, setOf(KeyModifier.Ctrl)))

        assertEquals("""{"type":"key","key":"K","modifiers":["Ctrl"]}""", text)
    }

    @Test
    fun aChordThatCannotBePressedIsRefusedWhenRead() {
        assertFailsWith<IllegalArgumentException> {
            Json.decodeFromString(InputChord.serializer(), """{"type":"key","key":"Shift","modifiers":["Shift"]}""")
        }
        assertFailsWith<SerializationException> {
            Json.decodeFromString(InputChord.serializer(), """{"type":"key","key":"Digit1","modifiers":["Space"]}""")
        }
    }

    @Test
    fun rebindingChangesWhatTriggersAnActionAndKeepsItsToggle() {
        val actions = actions()
        assertTrue(actions.isActive("map"), "startsOn")

        actions.rebind("map", ButtonBinding(chords = setOf(KeyChord(Key.M, setOf(KeyModifier.Alt)))))

        frame(actions, down = setOf(Key.M), pressed = setOf(Key.M))
        assertTrue(actions.isActive("map"), "M alone no longer switches it")
        frame(actions)
        frame(actions, down = setOf(Key.Alt, Key.M), pressed = setOf(Key.Alt, Key.M))
        assertFalse(actions.isActive("map"))
        assertEquals(
            ButtonAction("map", trigger = ActionTrigger.Toggle, startsOn = true, chords = setOf(KeyChord(Key.M, setOf(KeyModifier.Alt)))),
            actions.definitions.single { it.name == "map" },
            "the definitions show the new binding and keep the trigger",
        )
    }

    @Test
    fun rebindingAnAxisMovesItWithTheNewKeys() {
        val actions = actions()

        actions.rebind("move", AxisBinding(up = setOf(Key.ArrowUp)))

        frame(actions, down = setOf(Key.W), pressed = setOf(Key.W))
        assertEquals(0f, actions.axisY("move"))
        frame(actions, down = setOf(Key.ArrowUp), pressed = setOf(Key.ArrowUp))
        assertEquals(1f, actions.axisY("move"))
    }

    @Test
    fun rebindingRefusesAnUnknownActionOrTheOtherKindOfBinding() {
        val actions = actions()

        assertFailsWith<IllegalArgumentException> { actions.rebind("fly", ButtonBinding(keys = setOf(Key.F))) }
        assertFailsWith<IllegalArgumentException> { actions.rebind("jump", AxisBinding(up = setOf(Key.W))) }
        assertEquals(ButtonBinding(keys = setOf(Key.Space)), actions.binding("jump"), "a refused rebind changes nothing")
    }

    @Test
    fun savedBindingsLoadIntoFreshActionsAndSkipWhatNoLongerFits() {
        val played = actions()
        played.rebind("jump", ButtonBinding(keys = setOf(Key.J)))
        val saved = Json.encodeToString(InputBindings.serializer(), played.bindings())
        val stale = InputBindings(
            Json.decodeFromString(InputBindings.serializer(), saved).actions +
                ("dash" to ButtonBinding(keys = setOf(Key.Q))) +
                ("map" to AxisBinding(up = setOf(Key.M))),
        )

        val fresh = actions()
        val skipped = fresh.rebindAll(stale)

        assertEquals(setOf("dash", "map"), skipped.toSet())
        assertEquals(played.bindings().actions - "map", fresh.bindings().actions - "map")
        assertEquals(ButtonBinding(keys = setOf(Key.M)), fresh.binding("map"), "a binding of the wrong kind is skipped")
        frame(fresh, down = setOf(Key.J), pressed = setOf(Key.J))
        assertTrue(fresh.isActive("jump"))
    }

    @Test
    fun actionsBoundToFindsWhatANewChordWouldClashWith() {
        val actions = actions()

        assertEquals(listOf("slot1"), actions.actionsBoundTo(KeyChord(Key.Digit1)), "the plain key")
        assertEquals(listOf("slot1"), actions.actionsBoundTo(KeyChord(Key.Digit1, setOf(KeyModifier.Ctrl, KeyModifier.Shift))))
        assertEquals(emptyList(), actions.actionsBoundTo(KeyChord(Key.Digit1, setOf(KeyModifier.Shift))))
        assertEquals(listOf("move"), actions.actionsBoundTo(KeyChord(Key.W)))
        assertEquals(emptyList(), actions.actionsBoundTo(KeyChord(Key.W, setOf(KeyModifier.Ctrl))), "Ctrl+W is free: it would take W from the axis")
        assertEquals(listOf("slot1"), actions.actionsBoundTo(GamepadChord(GamepadButton.West, setOf(GamepadButton.LeftTrigger))))
    }

    private fun frame(actions: InputActions, down: Set<Key> = emptySet(), pressed: Set<Key> = emptySet()) {
        actions.beginFrame()
        actions.read(ChordSource(down, pressed))
    }
}
