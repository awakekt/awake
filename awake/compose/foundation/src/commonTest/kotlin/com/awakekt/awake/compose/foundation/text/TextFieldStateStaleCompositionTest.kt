/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The composition range outlives the text it indexes into.
 *
 * `setText` parks the range at the new cursor, and a later delete shortens the text without moving
 * it, so the range points past the end. Nothing reports a composition at that point -- the range is
 * collapsed -- but the displayed text was substringing with it regardless.
 */
class TextFieldStateStaleCompositionTest {

    @Test
    fun deletingAfterSetTextDoesNotReadPastTheEnd() {
        val state = TextFieldState()
        state.setText("123456")
        state.apply(TextEditAction.Backspace)
        assertEquals("12345", state.text)
        assertEquals("12345", state.displayedText)
    }

    @Test
    fun deletingEverythingAfterSetTextIsSafe() {
        val state = TextFieldState()
        state.setText("12")
        repeat(2) { state.apply(TextEditAction.Backspace) }
        assertEquals("", state.displayedText)
    }
}
