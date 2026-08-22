// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation

import io.github.ronjunevaldoz.awake.compose.foundation.layout.Column
import io.github.ronjunevaldoz.awake.compose.foundation.layout.Spacer
import io.github.ronjunevaldoz.awake.compose.foundation.layout.size
import io.github.ronjunevaldoz.awake.compose.foundation.text.BasicTextField
import io.github.ronjunevaldoz.awake.compose.foundation.text.TextFieldState
import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.ui.Modifier
import io.github.ronjunevaldoz.awake.compose.ui.platform.ComposeHost
import io.github.ronjunevaldoz.awake.compose.ui.platform.FrameInput
import io.github.ronjunevaldoz.awake.compose.ui.platform.blocksGameplayKeys
import io.github.ronjunevaldoz.awake.compose.ui.text.input.EditCommand
import io.github.ronjunevaldoz.awake.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val idle = FrameInput(viewportWidth = 200, viewportHeight = 200, pointerX = 5, pointerY = 5)

private fun typing(text: String = "", vararg commands: EditCommand) = FrameInput(
    viewportWidth = 200,
    viewportHeight = 200,
    pointerX = 5,
    pointerY = 5,
    typedText = text,
    editCommands = commands.toList(),
)

/** Editing arithmetic, independent of any tree. */
class TextFieldStateTest {

    @Test
    fun insertingGoesAtTheCaretAndMovesIt() {
        val state = TextFieldState("ac", cursor = 1)

        state.insert("b")

        assertEquals("abc", state.text)
        assertEquals(2, state.cursor)
    }

    @Test
    fun backspaceDeletesBeforeTheCaret() {
        val state = TextFieldState("abc", cursor = 2)

        state.apply(EditCommand.Backspace)

        assertEquals("ac", state.text)
        assertEquals(1, state.cursor)
    }

    @Test
    fun deleteRemovesAfterTheCaretAndLeavesItPut() {
        // The whole difference between the two keys, and the one people get backwards.
        val state = TextFieldState("abc", cursor = 1)

        state.apply(EditCommand.Delete)

        assertEquals("ac", state.text)
        assertEquals(1, state.cursor)
    }

    @Test
    fun editingAtTheEdgesIsANoOpNotAnError() {
        // Holding backspace at the start of a field is normal, not exceptional.
        val start = TextFieldState("ab", cursor = 0)
        start.apply(EditCommand.Backspace)
        start.apply(EditCommand.MoveLeft)
        assertEquals("ab", start.text)
        assertEquals(0, start.cursor)

        val end = TextFieldState("ab", cursor = 2)
        end.apply(EditCommand.Delete)
        end.apply(EditCommand.MoveRight)
        assertEquals("ab", end.text)
        assertEquals(2, end.cursor)
    }

    @Test
    fun homeAndEndJumpToTheEdges() {
        val state = TextFieldState("abcd", cursor = 2)

        state.apply(EditCommand.MoveHome)
        assertEquals(0, state.cursor)
        state.apply(EditCommand.MoveEnd)
        assertEquals(4, state.cursor)
    }

    @Test
    fun theCaretCannotLeaveTheText() {
        val state = TextFieldState("ab", cursor = 99)

        assertEquals(2, state.cursor)
        state.setText("x")
        assertEquals(1, state.cursor)
    }
}

/**
 * Typing through the real frame loop.
 *
 * Routed by focus, not by pointer position: a keyboard has no coordinates, and the point of a caret
 * is that typing goes where it is rather than where the mouse is.
 */
class TextFieldThroughFrameTest {

    private fun hostWith(state: TextFieldState): Pair<
        ComposeHost,
        context(Composer)
        () -> Unit,
        > {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            BasicTextField(state, Modifier.size(100.dp, 20.dp))
        }
        return host to content
    }

    private fun focused(state: TextFieldState): Pair<
        ComposeHost,
        context(Composer)
        () -> Unit,
        > {
        val (host, content) = hostWith(state)
        host.frame(idle, content)
        host.frame(FrameInput(200, 200, 5, 5, pointerDown = true), content)
        host.frame(idle, content)
        return host to content
    }

    @Test
    fun typingReachesAFocusedField() {
        val state = TextFieldState()
        val (host, content) = focused(state)

        host.frame(typing("hi"), content)

        assertEquals("hi", state.text)
    }

    @Test
    fun typingIsIgnoredWhileNothingIsFocused() {
        // A stray keystroke must not land in a field the user never clicked into.
        val state = TextFieldState()
        val (host, content) = hostWith(state)
        host.frame(idle, content)

        host.frame(typing("hi"), content)

        assertEquals("", state.text)
    }

    @Test
    fun editCommandsReachTheFieldToo() {
        val state = TextFieldState("abc")
        val (host, content) = focused(state)

        host.frame(typing(commands = arrayOf(EditCommand.Backspace)), content)

        assertEquals("ab", state.text)
    }

    @Test
    fun theFrameReportsThatTextHasFocus() {
        // The bug `ui-core` shipped: gating gameplay on pointer capture alone let W/A/S/D both
        // insert the letters and walk the player.
        val state = TextFieldState()
        val (host, content) = focused(state)

        val output = host.frame(idle, content)

        assertTrue(output.ownership.isTextInputFocused)
        assertTrue(output.ownership.blocksGameplayKeys)
        assertTrue(output.effects.requestKeyboard, "the platform was never asked for a keyboard")
    }

    @Test
    fun clickingAwayReleasesTheKeyboardAndTheKeys() {
        val state = TextFieldState()
        val host = ComposeHost()
        // Wrapped in a Column: the host root stacks its children at the origin, so two siblings
        // declared bare would overlap and the click would land on whichever painted last.
        val content: context(Composer)
        () -> Unit = {
            Column {
                BasicTextField(state, Modifier.size(50.dp, 20.dp))
                Spacer(Modifier.size(50.dp, 50.dp))
            }
        }
        host.frame(idle, content)
        host.frame(FrameInput(200, 200, 5, 5, pointerDown = true), content)
        assertTrue(host.frame(idle, content).ownership.isTextInputFocused)

        host.frame(FrameInput(200, 200, 5, 60, pointerDown = true), content)
        val output = host.frame(FrameInput(200, 200, 5, 60), content)

        assertFalse(output.ownership.isTextInputFocused)
        assertFalse(output.effects.requestKeyboard)
        host.frame(typing("x"), content)
        assertEquals("", state.text, "typing kept landing after the field lost focus")
    }

    @Test
    fun theFieldKeepsItsTextAcrossFrames() {
        // The state lives in a remembered object rather than in the link, which is rebuilt every
        // pass -- keeping it in the link would drop every keystroke one frame later.
        val state = TextFieldState()
        val (host, content) = focused(state)

        host.frame(typing("a"), content)
        host.frame(idle, content)
        host.frame(typing("b"), content)

        assertEquals("ab", state.text)
    }
}
