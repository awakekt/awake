/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn

import com.awakekt.awake.compose.foundation.text.PasswordMask
import com.awakekt.awake.compose.foundation.text.TextFieldState
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.ui.shadcn.components.ShadcnInput
import com.awakekt.awake.ui.shadcn.theme.provideShadcnTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShadcnInputMaskTest {

    private class Input(val state: TextFieldState, mask: Char?) {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            provideShadcnTheme(shadcnThemeValues(dark = false)) {
                ShadcnInput(state = state, mask = mask)
            }
        }

        fun frame(input: FrameInput = FrameInput(320, 50, 20, 18)): FrameOutput = host.frame(input, content)

        fun focus() {
            frame()
            frame(FrameInput(320, 50, 20, 18, pointerDown = true))
            frame()
        }
    }

    private fun FrameOutput.glyphs() = primitives.filterIsInstance<DrawCommand.Glyph>()

    @Test
    fun aMaskedInputDrawsTheMaskInsteadOfTheText() {
        val masked = Input(TextFieldState("hunter2"), mask = PasswordMask).frame().glyphs()

        assertEquals(Input(TextFieldState(PasswordMask.toString().repeat(7)), mask = null).frame().glyphs(), masked)
        assertNotEquals(Input(TextFieldState("hunter2"), mask = null).frame().glyphs(), masked)
    }

    @Test
    fun aMaskedInputIsAPasswordFieldToThePlatformAndTheClipboard() {
        val input = Input(TextFieldState("hunter2"), mask = PasswordMask)
        input.focus()
        input.state.selectAll()

        val effects = input.frame().effects

        assertTrue(effects.requestKeyboard)
        assertTrue(effects.passwordKeyboard, "the platform was not told this is a password field")
        assertNull(input.host.copyFocusedSelection())
        assertNull(input.host.cutFocusedSelection())
        assertEquals("hunter2", input.state.text)
    }

    @Test
    fun anUnmaskedInputIsUnchanged() {
        val input = Input(TextFieldState("hello"), mask = null)
        input.focus()
        input.state.selectAll()

        assertFalse(input.frame().effects.passwordKeyboard)
        assertEquals("hello", input.host.copyFocusedSelection())
    }
}
