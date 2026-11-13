/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation

import com.awakekt.awake.compose.foundation.layout.size
import com.awakekt.awake.compose.foundation.text.BasicTextField
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.getValue
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.setValue
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.focus.FocusRequester
import com.awakekt.awake.compose.ui.focus.focusRequester
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.compose.ui.semantics.testTag
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.ClipboardCommand
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Disabled contract for [BasicTextField]: blocks focus and edits while preserving [TextFieldState]. */
class BasicTextFieldDisabledTest {

    private fun List<SemanticsNode>.byTag(tag: String): SemanticsNode =
        firstNotNullOfOrNull { findTag(it, tag) } ?: error("no semantics node with tag '$tag'")

    private fun findTag(node: SemanticsNode, tag: String): SemanticsNode? =
        if (node.testTag == tag) node else node.children.firstNotNullOfOrNull { findTag(it, tag) }

    @Test
    fun disabledFieldPublishesSemanticsAndBlocksFocusPointerTypingPasteImeAndSubmit() {
        val state = TextFieldState("seed", cursor = 4)
        val requester = FocusRequester()
        var clicks = 0
        var submits = 0
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            BasicTextField(
                state = state,
                modifier = Modifier.size(120.dp, 24.dp).testTag("field").focusRequester(requester),
                singleLine = true,
                onClick = { clicks++ },
                onSubmit = { submits++ },
                enabled = false,
            )
        }

        val initial = host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10), content)
        assertEquals(true, initial.semantics.byTag("field").config[SemanticsProperties.Disabled])
        assertFalse(initial.ownership.isTextInputFocused)
        assertFalse(initial.effects.requestKeyboard)

        requester.requestFocus()
        host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10, pointerDown = true), content)
        host.frame(FrameInput(200, 80, pointerX = 60, pointerY = 10, pointerDown = true), content)
        val afterEditAttempts = host.frame(
            FrameInput(
                viewportWidth = 200,
                viewportHeight = 80,
                pointerX = 60,
                pointerY = 10,
                typedText = "pasted\nvalue",
                imeComposition = ImeComposition("pre", 0, 3),
                imeCommit = "commit",
                editActions = listOf(TextEditAction.Backspace, TextEditAction.Enter),
                clipboardCommands = listOf(ClipboardCommand.Copy, ClipboardCommand.Cut),
            ),
            content,
        )

        assertEquals("seed", state.text)
        assertEquals(4, state.cursor)
        assertFalse(state.hasSelection)
        assertFalse(state.hasComposition)
        assertEquals(0, clicks)
        assertEquals(0, submits)
        assertFalse(afterEditAttempts.ownership.isTextInputFocused)
        assertFalse(afterEditAttempts.effects.requestKeyboard)
        assertNull(afterEditAttempts.effects.clipboardText)
    }

    @Test
    fun disablingAFocusedFieldDropsFocusAndPreservesCallerBufferAcrossReEnable() {
        val state = TextFieldState()
        // State, not a plain var: a host that skips unchanged passes only sees reads it can track.
        var enabled by mutableStateOf(true)
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            BasicTextField(
                state = state,
                modifier = Modifier.size(120.dp, 24.dp).testTag("field"),
                singleLine = true,
                enabled = enabled,
            )
        }

        host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10), content)
        host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10, pointerDown = true), content)
        val focused = host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10, typedText = "alpha"), content)
        assertTrue(focused.ownership.isTextInputFocused)
        assertEquals("alpha", state.text)
        state.select(1, 4)

        enabled = false
        val disabled = host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10, typedText = "ignored"), content)
        assertFalse(disabled.ownership.isTextInputFocused)
        assertFalse(disabled.effects.requestKeyboard)
        assertEquals(true, disabled.semantics.byTag("field").config[SemanticsProperties.Disabled])
        assertEquals("alpha", state.text)
        assertEquals(1, state.selectionStart)
        assertEquals(4, state.selectionEnd)

        enabled = true
        val reEnabled = host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10), content)
        assertNull(reEnabled.semantics.byTag("field").config[SemanticsProperties.Disabled])
        assertEquals("alpha", state.text)
        assertEquals(1, state.selectionStart)
        assertEquals(4, state.selectionEnd)

        host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10, pointerDown = true), content)
        host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10), content)
        state.select(1, 4)
        host.frame(FrameInput(200, 80, pointerX = 10, pointerY = 10, typedText = "!"), content)
        assertEquals("a!a", state.text)
    }
}
