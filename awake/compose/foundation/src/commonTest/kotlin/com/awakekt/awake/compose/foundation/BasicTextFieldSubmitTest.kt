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
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.Test
import kotlin.test.assertEquals

/** Enter in a focused [BasicTextField]: `onSubmit` in a single-line field, a newline in any other. */
class BasicTextFieldSubmitTest {

    private val idle = FrameInput(viewportWidth = 200, viewportHeight = 200, pointerX = 5, pointerY = 5)

    private fun enterPress() = FrameInput(
        viewportWidth = 200,
        viewportHeight = 200,
        pointerX = 5,
        pointerY = 5,
        editActions = listOf(TextEditAction.Enter),
    )

    private fun focusedField(
        state: TextFieldState,
        singleLine: Boolean,
        onSubmit: () -> Unit,
    ): Pair<ComposeHost, context(Composer) () -> Unit> {
        val host = ComposeHost()
        val content: context(Composer) () -> Unit = {
            BasicTextField(state, Modifier.size(100.dp, 20.dp), singleLine = singleLine, onSubmit = onSubmit)
        }
        host.frame(idle, content)
        host.frame(FrameInput(200, 200, 5, 5, pointerDown = true), content) // a real press focuses it.
        host.frame(idle, content)
        return host to content
    }

    @Test
    fun enterCallsOnSubmitInASingleLineFieldInsteadOfInsertingANewline() {
        val state = TextFieldState("hi")
        var submitted = 0
        val (host, content) = focusedField(state, singleLine = true) { submitted++ }
        // The focusing click above also placed the caret; put it back at the end for this test.
        state.moveCursorTo(state.text.length)

        host.frame(enterPress(), content)

        assertEquals(1, submitted)
        assertEquals("hi", state.text, "Enter in a single-line field must not insert a newline")
    }

    @Test
    fun enterInsertsANewlineInAMultilineFieldInsteadOfCallingOnSubmit() {
        val state = TextFieldState("hi")
        var submitted = 0
        val (host, content) = focusedField(state, singleLine = false) { submitted++ }
        state.moveCursorTo(state.text.length)

        host.frame(enterPress(), content)

        assertEquals(0, submitted, "a multi-line field has somewhere for Enter to go, so it must not submit")
        assertEquals("hi\n", state.text)
    }
}
