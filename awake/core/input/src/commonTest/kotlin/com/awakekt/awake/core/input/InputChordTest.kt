/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InputChordTest {
    private val actions = InputActions(
        listOf(
            ButtonAction("slot1", keys = setOf(Key.Digit1)),
            ButtonAction("altSlot1", chords = setOf(KeyChord(Key.Digit1, setOf(KeyModifier.Shift)))),
            ButtonAction("save", chords = setOf(KeyChord(Key.S, setOf(KeyModifier.Ctrl))), trigger = ActionTrigger.Press),
            ButtonAction("saveAs", chords = setOf(KeyChord(Key.S, setOf(KeyModifier.Ctrl, KeyModifier.Shift))), trigger = ActionTrigger.Press),
            ButtonAction("inspect", chords = setOf(KeyChord(Key.K, setOf(KeyModifier.Ctrl), ExtraModifiers.Allow))),
            AxisAction("move", up = setOf(Key.W), down = setOf(Key.S)),
        ),
    )

    @Test
    fun aChordFiresWithItsModifiersAndThePlainKeyWithout() {
        frame(down = setOf(Key.Digit1), pressed = setOf(Key.Digit1))
        assertTrue(actions.isActive("slot1"))
        assertFalse(actions.isActive("altSlot1"), "Shift is not held")

        frame()
        frame(down = setOf(Key.Shift, Key.Digit1), pressed = setOf(Key.Shift, Key.Digit1))
        assertTrue(actions.isActive("altSlot1"))
        assertFalse(actions.isActive("slot1"), "Shift+1 is more specific, so it does not also fire 1")
    }

    @Test
    fun aPlainKeyStillFiresUnderAModifierNothingMoreSpecificUses() {
        frame(down = setOf(Key.Ctrl, Key.Digit1), pressed = setOf(Key.Ctrl, Key.Digit1))

        assertTrue(actions.isActive("slot1"), "a plain key allows extra modifiers, and no Ctrl+1 is bound")
        assertFalse(actions.isActive("altSlot1"))
    }

    @Test
    fun theMostSpecificChordWinsAndABlockingChordIgnoresExtraModifiers() {
        frame(down = setOf(Key.Ctrl, Key.Shift, Key.S), pressed = setOf(Key.S))
        assertTrue(actions.wasPressed("saveAs"))
        assertFalse(actions.wasPressed("save"), "Ctrl+S blocks the extra Shift, and Ctrl+Shift+S is more specific anyway")

        frame()
        frame(down = setOf(Key.Ctrl, Key.S), pressed = setOf(Key.S))
        assertTrue(actions.wasPressed("save"))
        assertFalse(actions.wasPressed("saveAs"))
    }

    @Test
    fun aChordThatAllowsExtraModifiersStillMatchesWithThem() {
        frame(down = setOf(Key.Ctrl, Key.Alt, Key.K), pressed = setOf(Key.K))
        assertTrue(actions.isActive("inspect"))

        frame()
        frame(down = setOf(Key.Alt, Key.K), pressed = setOf(Key.K))
        assertFalse(actions.isActive("inspect"), "its own modifier is still required")
    }

    @Test
    fun aBlockingChordWithNoModifiersFiresOnlyOnTheBareKey() {
        val bare = InputActions(listOf(ButtonAction("bare", chords = setOf(KeyChord(Key.E)))))

        frame(bare, down = setOf(Key.Shift, Key.E), pressed = setOf(Key.E))
        assertFalse(bare.isActive("bare"))
        frame(bare)
        frame(bare, down = setOf(Key.E), pressed = setOf(Key.E))
        assertTrue(bare.isActive("bare"))
    }

    @Test
    fun modifiersHeldInEitherOrderMakeTheSameChord() {
        frame(down = setOf(Key.Ctrl), pressed = setOf(Key.Ctrl))
        frame(down = setOf(Key.Ctrl, Key.Shift), pressed = setOf(Key.Shift))
        frame(down = setOf(Key.Ctrl, Key.Shift, Key.S), pressed = setOf(Key.S))
        assertTrue(actions.wasPressed("saveAs"), "Ctrl then Shift")

        frame()
        frame(down = setOf(Key.Shift), pressed = setOf(Key.Shift))
        frame(down = setOf(Key.Shift, Key.Ctrl), pressed = setOf(Key.Ctrl))
        frame(down = setOf(Key.Shift, Key.Ctrl, Key.S), pressed = setOf(Key.S))
        assertTrue(actions.wasPressed("saveAs"), "Shift then Ctrl")
    }

    @Test
    fun aModifierPressedAfterTheKeyDoesNotTurnItIntoAChord() {
        frame(down = setOf(Key.Digit1), pressed = setOf(Key.Digit1))
        frame(down = setOf(Key.Digit1, Key.Shift), pressed = setOf(Key.Shift))

        assertTrue(actions.isActive("slot1"), "1 went down first and keeps the key")
        assertFalse(actions.isActive("altSlot1"))
    }

    @Test
    fun releasingTheModifierEndsTheChordWithoutStartingThePlainKey() {
        frame(down = setOf(Key.Shift, Key.Digit1), pressed = setOf(Key.Shift, Key.Digit1))
        frame(down = setOf(Key.Digit1))

        assertFalse(actions.isActive("altSlot1"), "Shift is up")
        assertFalse(actions.isActive("slot1"), "Shift+1 took the key until it is released")

        frame()
        frame(down = setOf(Key.Digit1), pressed = setOf(Key.Digit1))
        assertTrue(actions.isActive("slot1"), "a fresh press is resolved afresh")
    }

    @Test
    fun eitherShiftIsTheSameModifier() {
        // Key has one Shift: a host maps the left and the right one to it, so a chord cannot tell them apart.
        val input = Input()
        input.setKeyDown(Key.Shift, true) // the right Shift, say
        input.setKeyDown(Key.Digit1, true)
        actions.beginFrame()
        actions.read(input.updateSnapshot())

        assertTrue(actions.isActive("altSlot1"))
        assertFalse(actions.isActive("slot1"))
    }

    @Test
    fun aModifierCanBeTheChordsOwnKey() {
        val chord = InputActions(listOf(ButtonAction("mode", chords = setOf(KeyChord(Key.Shift, setOf(KeyModifier.Ctrl))))))

        frame(chord, down = setOf(Key.Ctrl, Key.Shift), pressed = setOf(Key.Ctrl, Key.Shift))

        assertTrue(chord.isActive("mode"), "the held Shift is the key, not an extra modifier")
    }

    @Test
    fun aChordTakesItsKeyFromAnAxis() {
        frame(down = setOf(Key.Ctrl, Key.S), pressed = setOf(Key.Ctrl, Key.S))
        assertTrue(actions.wasPressed("save"))
        assertEquals(0f, actions.axisY("move"), "Ctrl+S saves without walking back")

        frame()
        frame(down = setOf(Key.S), pressed = setOf(Key.S))
        assertEquals(-1f, actions.axisY("move"))
    }

    @Test
    fun actionsBoundToTheSameChordAllFire() {
        val tied = InputActions(
            listOf(
                ButtonAction("a", chords = setOf(KeyChord(Key.Q, setOf(KeyModifier.Alt)))),
                ButtonAction("b", chords = setOf(KeyChord(Key.Q, setOf(KeyModifier.Alt)))),
            ),
        )

        frame(tied, down = setOf(Key.Alt, Key.Q), pressed = setOf(Key.Alt, Key.Q))

        assertTrue(tied.isActive("a"))
        assertTrue(tied.isActive("b"))
    }

    @Test
    fun aChordSwitchesAToggleOnce() {
        val toggle = InputActions(
            listOf(
                ButtonAction(
                    "map",
                    keys = setOf(Key.M),
                    chords = setOf(KeyChord(Key.M, setOf(KeyModifier.Shift), ExtraModifiers.Allow)),
                    trigger = ActionTrigger.Toggle,
                ),
            ),
        )

        frame(toggle, down = setOf(Key.Shift, Key.M), pressed = setOf(Key.Shift, Key.M))

        assertTrue(toggle.isActive("map"), "the chord wins the key, and the toggle switches once")
    }

    @Test
    fun aHeldShoulderAndAFaceButtonMakeAControllerChord() {
        val pad = InputActions(
            listOf(
                ButtonAction("attack", chords = setOf(GamepadChord(GamepadButton.South))),
                ButtonAction("skill1", chords = setOf(GamepadChord(GamepadButton.South, setOf(GamepadButton.LeftShoulder)))),
            ),
        )

        frame(pad, padDown = setOf(GamepadButton.South), padPressed = setOf(GamepadButton.South))
        assertTrue(pad.isActive("attack"))
        assertFalse(pad.isActive("skill1"))

        frame(pad)
        frame(pad, padDown = setOf(GamepadButton.LeftShoulder), padPressed = setOf(GamepadButton.LeftShoulder))
        frame(pad, padDown = setOf(GamepadButton.LeftShoulder, GamepadButton.South), padPressed = setOf(GamepadButton.South))
        assertTrue(pad.isActive("skill1"))
        assertFalse(pad.isActive("attack"), "the shoulder chord is more specific")
    }

    @Test
    fun anotherHeldShoulderBlocksAControllerChord() {
        val pad = InputActions(listOf(ButtonAction("skill1", chords = setOf(GamepadChord(GamepadButton.South, setOf(GamepadButton.LeftShoulder))))))
        val held = setOf(GamepadButton.LeftShoulder, GamepadButton.RightTrigger, GamepadButton.South)

        frame(pad, padDown = held, padPressed = held)
        assertFalse(pad.isActive("skill1"), "RightTrigger is an extra modifier")

        frame(pad)
        val withFace = setOf(GamepadButton.LeftShoulder, GamepadButton.North, GamepadButton.South)
        frame(pad, padDown = withFace, padPressed = withFace)
        assertTrue(pad.isActive("skill1"), "a face button held as well is not a modifier")
    }

    @Test
    fun controllerChordsAreReadFromTheSnapshotAndWhileKeysAreWithheld() {
        val pad = InputActions(listOf(ButtonAction("skill1", chords = setOf(GamepadChord(GamepadButton.East, setOf(GamepadButton.RightShoulder))))))
        val input = Input()
        input.setGamepadButton(GamepadButton.RightShoulder, true)
        input.setGamepadButton(GamepadButton.East, true)
        val snapshot = input.updateSnapshot()

        assertEquals(setOf(GamepadButton.RightShoulder, GamepadButton.East), snapshot.gamepadButtonsPressed)
        pad.beginFrame()
        pad.read(snapshot)
        assertTrue(pad.wasPressed("skill1"))

        input.setGamepadButton(GamepadButton.East, false)
        assertTrue(input.updateSnapshot().wasReleased(GamepadButton.East))

        frame(pad, padDown = setOf(GamepadButton.RightShoulder, GamepadButton.East), padPressed = setOf(GamepadButton.East), withholdsKeys = true)
        assertTrue(pad.isActive("skill1"), "a text field owns the keys, not the controller")
    }

    @Test
    fun aChordIsCheckedWhenMade() {
        assertFailsWith<IllegalArgumentException> { KeyChord(Key.Unknown) }
        assertFailsWith<IllegalArgumentException> { KeyChord(Key.Shift, setOf(KeyModifier.Shift)) }
        assertFailsWith<IllegalArgumentException> { GamepadChord(GamepadButton.South, setOf(GamepadButton.South)) }
    }

    @Test
    fun aBindingDescribesItsPartsInAFixedOrder() {
        val binding = ButtonBinding(
            keys = setOf(Key.E),
            buttons = setOf(PointerButton.Secondary),
            chords = setOf(
                KeyChord(Key.K, setOf(KeyModifier.Shift, KeyModifier.Alt, KeyModifier.Ctrl)),
                GamepadChord(GamepadButton.South, setOf(GamepadButton.RightShoulder, GamepadButton.LeftShoulder)),
            ),
        )

        assertEquals(
            listOf(
                BindingDescription(emptyList(), InputControl.Keyboard(Key.E)),
                BindingDescription(emptyList(), InputControl.Pointer(PointerButton.Secondary)),
                BindingDescription(
                    listOf(InputControl.Keyboard(Key.Ctrl), InputControl.Keyboard(Key.Alt), InputControl.Keyboard(Key.Shift)),
                    InputControl.Keyboard(Key.K),
                ),
                BindingDescription(
                    listOf(InputControl.Gamepad(GamepadButton.LeftShoulder), InputControl.Gamepad(GamepadButton.RightShoulder)),
                    InputControl.Gamepad(GamepadButton.South),
                ),
            ),
            binding.describe(),
        )
    }

    private fun frame(
        target: InputActions = actions,
        down: Set<Key> = emptySet(),
        pressed: Set<Key> = emptySet(),
        padDown: Set<GamepadButton> = emptySet(),
        padPressed: Set<GamepadButton> = emptySet(),
        withholdsKeys: Boolean = false,
    ) {
        target.beginFrame()
        target.read(ChordSource(down, pressed, padDown, padPressed, withholdsKeys))
    }
}

internal class ChordSource(
    private val down: Set<Key> = emptySet(),
    private val pressed: Set<Key> = emptySet(),
    private val padDown: Set<GamepadButton> = emptySet(),
    private val padPressed: Set<GamepadButton> = emptySet(),
    private val withholdsKeys: Boolean = false,
) : ActionInputSource {
    override fun isDown(key: Key) = key in down
    override fun wasPressed(key: Key) = key in pressed
    override fun isDown(button: PointerButton) = false
    override fun wasPressed(button: PointerButton) = false
    override fun isDown(button: GamepadButton) = button in padDown
    override fun wasPressed(button: GamepadButton) = button in padPressed
    override val keysWithheldFromActions: Boolean get() = withholdsKeys
}
