/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.ClipboardCommand
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private const val KEY_A = 65
private const val KEY_C = 67
private const val KEY_S = 83
private const val KEY_V = 86
private const val KEY_X = 88
private const val KEY_LEFT_CONTROL = 341
private const val KEY_LEFT_SUPER = 343

class GlfwClipboardShortcutTest {

    @BeforeTest
    fun reset() = resetGlfwTextInputRepeatStateForTest()

    @AfterTest
    fun cleanup() = resetGlfwTextInputRepeatStateForTest()

    private fun press(reader: FakeGlfwWindowInput, input: Input, vararg keys: Int, superShortcut: Boolean = false) {
        reader.keysDown.clear()
        reader.keysDown += keys.toList()
        pollGlfwTextInput(reader, input, deltaSeconds = 0.0, shortcutUsesSuper = superShortcut)
    }

    @Test
    fun ctrlCAndCtrlXRequestCopyAndCutWithoutTypingTheLetter() {
        val input = Input()
        val reader = FakeGlfwWindowInput()

        press(reader, input, KEY_LEFT_CONTROL, KEY_C)
        press(reader, input, KEY_LEFT_CONTROL)
        press(reader, input, KEY_LEFT_CONTROL, KEY_X)
        val snapshot = input.updateSnapshot()

        assertEquals(listOf(ClipboardCommand.Copy, ClipboardCommand.Cut), snapshot.clipboardCommands)
        assertEquals("", snapshot.typedText, "a shortcut typed its letter")
    }

    @Test
    fun ctrlVTypesTheClipboardText() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { clipboardText = "pasted 😀" }

        press(reader, input, KEY_LEFT_CONTROL, KEY_V)

        assertEquals("pasted 😀", input.updateSnapshot().typedText)
    }

    @Test
    fun ctrlASelectsAll() {
        val input = Input()

        press(FakeGlfwWindowInput(), input, KEY_LEFT_CONTROL, KEY_A)

        assertEquals(listOf(TextEditAction.SelectAll), input.updateSnapshot().editActions)
    }

    @Test
    fun theUisAnswerIsWrittenToTheClipboardOnce() {
        val input = Input()
        val reader = FakeGlfwWindowInput()
        input.clipboardWrite = "copied"

        press(reader, input)
        reader.clipboardText = "changed elsewhere"
        press(reader, input)

        assertEquals("changed elsewhere", reader.clipboardText, "the same answer was written twice")
    }

    @Test
    fun onMacOsCmdIsTheShortcutAndCtrlTypesNothing() {
        val input = Input()
        val reader = FakeGlfwWindowInput().apply { clipboardText = "mac" }

        press(reader, input, KEY_LEFT_SUPER, KEY_V, superShortcut = true)
        press(reader, input, KEY_LEFT_CONTROL, KEY_C, superShortcut = true)
        val snapshot = input.updateSnapshot()

        assertEquals("mac", snapshot.typedText)
        assertEquals(emptyList(), snapshot.clipboardCommands, "Ctrl+C copied on macOS")
    }

    @Test
    fun anAppShortcutLetterIsNotTyped() {
        val input = Input()

        press(FakeGlfwWindowInput(), input, KEY_LEFT_CONTROL, KEY_S)

        assertEquals("", input.updateSnapshot().typedText)
    }
}
