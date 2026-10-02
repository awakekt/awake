/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.PasswordMask
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.ClipboardCommand
import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Copy, cut, paste and select-all through the frame, the way every platform bridge drives them:
 * commands in [FrameInput.clipboardCommands], the answer out in `PlatformEffects.clipboardText`,
 * paste as typed text.
 */
class TextFieldClipboardTest {

    private class Field(text: String, mask: Char? = null, singleLine: Boolean = true) {
        val state = TextFieldState(text)
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            BasicTextField(state, Modifier.size(160.dp, 20.dp), singleLine = singleLine, mask = mask)
        }

        fun frame(input: FrameInput = FrameInput(200, 40, 5, 5)): FrameOutput = host.frame(input, content)

        fun focus(): Field = apply {
            frame()
            frame(FrameInput(200, 40, 5, 5, pointerDown = true))
            frame()
        }

        fun commands(vararg commands: ClipboardCommand, edits: List<TextEditAction> = emptyList()) =
            frame(FrameInput(200, 40, 5, 5, editActions = edits, clipboardCommands = commands.toList()))
    }

    @Test
    fun selectAllThenCopyInOneFrameCopiesEverything() {
        // Ctrl+A and Ctrl+C can land in the same frame; the edit must apply before the copy reads.
        val field = Field("hello").focus()

        val output = field.commands(ClipboardCommand.Copy, edits = listOf(TextEditAction.SelectAll))

        assertEquals("hello", output.effects.clipboardText)
        assertEquals("hello", field.state.text)
    }

    @Test
    fun cutHandsOverTheSelectionAndRemovesIt() {
        val field = Field("hello").focus()
        field.state.select(anchor = 1, focus = 4)

        val output = field.commands(ClipboardCommand.Cut)

        assertEquals("ell", output.effects.clipboardText)
        assertEquals("ho", field.state.text)
    }

    @Test
    fun nothingIsWrittenWithoutASelectionOrAFocusedField() {
        val unfocused = Field("hello")
        unfocused.frame()
        unfocused.state.selectAll()
        assertNull(unfocused.commands(ClipboardCommand.Copy).effects.clipboardText, "an unfocused field answered")

        val collapsed = Field("hello").focus()
        val copied = collapsed.commands(ClipboardCommand.Copy).effects.clipboardText
        assertNull(copied, "an empty selection was copied")
    }

    @Test
    fun aPasswordFieldNeverReachesTheClipboard() {
        val field = Field("hunter2", mask = PasswordMask).focus()

        val output = field.commands(
            ClipboardCommand.Copy,
            ClipboardCommand.Cut,
            edits = listOf(TextEditAction.SelectAll),
        )

        assertNull(output.effects.clipboardText)
        assertEquals("hunter2", field.state.text, "cut removed text from a password field")
    }

    @Test
    fun pasteArrivesAsTypedTextAndKeepsASingleLineFieldOnOneLine() {
        val field = Field("ab").focus()
        field.state.moveCursorTo(1)

        field.frame(FrameInput(200, 40, 5, 5, typedText = "x\ny"))

        assertEquals("ax yb", field.state.text)
    }

    @Test
    fun nothingIsWrittenOnAFrameWithoutCommands() {
        val field = Field("hello").focus()
        field.state.selectAll()

        assertNull(field.frame().effects.clipboardText)
    }
}
