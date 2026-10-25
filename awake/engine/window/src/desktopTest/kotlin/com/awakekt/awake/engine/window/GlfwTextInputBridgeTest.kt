/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val GLFW_KEY_A = 65
private const val GLFW_KEY_LEFT_SHIFT = 340
private const val GLFW_KEY_BACKSPACE = 259
private const val GLFW_KEY_DOWN = 264
private const val GLFW_KEY_UP = 265

// Must match GlfwTextInputBridge.kt's private REPEAT_INITIAL_DELAY_SECONDS -- there's no seam to
// read it from a test, so this asserts the observable cadence, not the constant.
private const val REPEAT_INITIAL_DELAY_SECONDS = 0.5

/** Typed text comes from the platform's character callback through [GlfwWindowInput.consumeTypedText];
 * the key map only drives edit keys and shortcuts. [Input] is a per-session instance now (no longer a global object) -- each test
 * constructs its own and reads it back via [Input.updateSnapshot], which drains the typed
 * text/edit action buffers the same way the old static `consumeTypedText`/`consumeEditActions`
 * did. */
class GlfwTextInputBridgeTest {

    @BeforeTest
    fun reset() {
        resetGlfwTextInputRepeatStateForTest()
    }

    @AfterTest
    fun cleanup() {
        resetGlfwTextInputRepeatStateForTest()
    }

    @Test
    fun typedTextReachesTheInputOnce() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { pendingTypedText = "name@example.com" }
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        assertEquals("name@example.com", input.updateSnapshot().typedText, "an email address must type as the platform sent it")

        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        assertEquals("", input.updateSnapshot().typedText, "typed text must be drained, not typed again next frame")
    }

    @Test
    fun shiftedSymbolsAltGrAndOtherLayoutsTypeWhatThePlatformSent() {
        val input = Input()
        // US Shift symbols, a German AltGr+Q, a French AZERTY row and a dead-key accent, an emoji.
        val typed = "!@#\$%^&*()_+{}|:\"<>?~ @ azerty é 😀"
        val reader = FakeGlfwWindowInput().apply {
            pendingTypedText = typed
            keysDown += GLFW_KEY_LEFT_SHIFT
        }
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        assertEquals(typed, input.updateSnapshot().typedText)
    }

    @Test
    fun aHeldLetterKeyTypesNothingWithoutACharacter() {
        val input = Input()
        // The key map no longer types: a key with no character from the platform (as on a layout
        // where that position is a dead key) adds nothing, shifted or not.
        val reader = FakeGlfwWindowInput().apply {
            keysDown += GLFW_KEY_A
            keysDown += GLFW_KEY_LEFT_SHIFT
        }
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        assertEquals("", input.updateSnapshot().typedText)
    }

    @Test
    fun editKeyFiresExactlyOnceOnTheRisingEdge() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { keysDown += GLFW_KEY_BACKSPACE }
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        assertEquals(listOf(TextEditAction.Backspace), input.updateSnapshot().editActions, "the first frame a key is down must fire exactly once")

        pollGlfwTextInput(reader, input, deltaSeconds = 0.05)
        assertEquals(emptyList(), input.updateSnapshot().editActions, "holding the key without crossing the repeat threshold must not fire again")
    }

    @Test
    fun holdingPastTheInitialDelayRepeats() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { keysDown += GLFW_KEY_BACKSPACE }
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        input.updateSnapshot()

        pollGlfwTextInput(reader, input, deltaSeconds = REPEAT_INITIAL_DELAY_SECONDS)
        assertEquals(listOf(TextEditAction.Backspace), input.updateSnapshot().editActions, "holding past the initial repeat delay must fire again")
    }

    @Test
    fun releasingResetsTheRepeatCadence() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { keysDown += GLFW_KEY_BACKSPACE }
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        input.updateSnapshot()

        reader.keysDown -= GLFW_KEY_BACKSPACE
        pollGlfwTextInput(reader, input, deltaSeconds = 1.0)
        assertEquals(emptyList(), input.updateSnapshot().editActions, "releasing the key must stop it even after a long delta")

        reader.keysDown += GLFW_KEY_BACKSPACE
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        assertEquals(listOf(TextEditAction.Backspace), input.updateSnapshot().editActions, "pressing again after a release must fire a fresh rising edge, not resume mid-repeat")
    }

    @Test
    fun verticalArrowKeysReachTheSharedEditActionStream() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { keysDown += GLFW_KEY_UP }
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        assertEquals(listOf(TextEditAction.ArrowUp), input.updateSnapshot().editActions)

        reader.keysDown -= GLFW_KEY_UP
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        input.updateSnapshot()
        reader.keysDown += GLFW_KEY_DOWN
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0)
        assertEquals(listOf(TextEditAction.ArrowDown), input.updateSnapshot().editActions)
    }
}
