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
import com.awakekt.awake.compose.foundation.text.maskedText
import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.ui.Modifier
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.unit.dp
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.math2d.sp
import com.awakekt.awake.core.text.font.UiFonts
import com.awakekt.awake.core.text.theme.TextStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Wide real glyphs under a narrow mask: any geometry still read from the real text lands visibly
// somewhere else than the masked glyphs.
private const val SECRET = "WWWWWWWW"
private const val MASK = '.'
private val MASKED = maskedText(SECRET, MASK)

/**
 * A masked field against a plain field that literally holds the mask characters.
 *
 * The plain field is the oracle: whatever the masked field paints, and wherever it puts the caret,
 * the selection or a click, must be exactly what a field showing those glyphs would. It is
 * single-line because a masked field always is.
 */
class MaskedTextFieldTest {

    private class Field(val state: TextFieldState, mask: Char?) {
        val host = ComposeHost()
        val content: context(Composer)
        () -> Unit = {
            BasicTextField(
                state,
                Modifier.size(160.dp, 20.dp),
                style = TextStyle(lineHeight = 20f.sp),
                singleLine = true,
                mask = mask,
            )
        }

        fun frame(input: FrameInput = FrameInput(200, 40, 5, 5)): FrameOutput = host.frame(input, content)

        fun focus() {
            frame()
            frame(FrameInput(200, 40, 5, 5, pointerDown = true))
            frame()
        }

        fun clickAt(x: Int) {
            frame(FrameInput(200, 40, x, 5))
            frame(FrameInput(200, 40, x, 5, pointerDown = true))
            frame(FrameInput(200, 40, x, 5))
        }
    }

    private fun masked(text: String = SECRET, cursor: Int = text.length) = Field(TextFieldState(text, cursor), MASK)

    private fun oracle(text: String = MASKED, cursor: Int = text.length) = Field(TextFieldState(text, cursor), null)

    private fun FrameOutput.glyphs() = primitives.filterIsInstance<DrawCommand.Glyph>()

    private fun FrameOutput.caret() = primitives.filterIsInstance<DrawCommand.Quad>().first { it.w == 1f }

    private fun FrameOutput.selection() = primitives.filterIsInstance<DrawCommand.Quad>().filter { it.w > 1f }

    @Test
    fun theDefaultMaskIsAGlyphTheBundledFontCanDraw() {
        // A mask the atlas lacks draws its fallback glyph, which reads as "????" in every field.
        val font = UiFonts.default()
        val fallback = font.uvFor('\uFFFF')

        assertNotEquals(fallback, font.uvFor(PasswordMask), "PasswordMask draws the font's fallback glyph")
    }

    @Test
    fun maskedTextIsOneMaskPerCharacter() {
        assertEquals("", maskedText("", '*'))
        assertEquals("****", maskedText("pä s", '*'))
        assertEquals(SECRET.length, MASKED.length)
    }

    @Test
    fun theFieldPaintsTheMaskAndKeepsTheRealText() {
        val field = masked()

        val painted = field.frame().glyphs()

        assertEquals(oracle().frame().glyphs(), painted)
        assertNotEquals(oracle(SECRET).frame().glyphs(), painted, "the real text was painted")
        assertEquals(SECRET, field.state.text)
    }

    @Test
    fun imePreeditIsMaskedToo() {
        val field = masked("WW", cursor = 1)
        field.state.setComposition(ImeComposition("WWW"))

        val painted = field.frame().glyphs()

        assertEquals(oracle(maskedText("WWWWW", MASK)).frame().glyphs(), painted)
        assertEquals("WW", field.state.text, "pre-edit text leaked into the committed value")
    }

    @Test
    fun theCaretSitsAfterTheMaskedGlyphs() {
        val field = masked(cursor = 3)
        field.focus()
        field.state.moveCursorTo(3)
        val expected = oracle(cursor = 3).run {
            focus()
            state.moveCursorTo(3)
            frame(FrameInput(200, 40, 5, 5, deltaSeconds = 0.1f)).caret()
        }

        val caret = field.frame(FrameInput(200, 40, 5, 5, deltaSeconds = 0.1f)).caret()

        assertEquals(expected.x, caret.x, 0.01f)
    }

    @Test
    fun theSelectionCoversTheMaskedGlyphs() {
        val field = masked()
        field.state.select(anchor = 2, focus = 5)
        val expected = oracle().run {
            state.select(anchor = 2, focus = 5)
            frame().selection()
        }

        val painted = field.frame().selection()

        assertTrue(painted.isNotEmpty(), "the selection was not painted")
        assertEquals(expected, painted)
    }

    @Test
    fun aClickHitTestsAgainstTheMaskedGlyphs() {
        val field = masked()
        val plain = oracle()
        val real = oracle(SECRET)

        field.clickAt(14)
        plain.clickAt(14)
        real.clickAt(14)

        assertEquals(plain.state.cursor, field.state.cursor)
        assertNotEquals(real.state.cursor, field.state.cursor, "the click was hit-tested against the real text")
    }

    @Test
    fun copyAndCutYieldNothingAndLeaveTheTextAlone() {
        val field = masked()
        field.focus()
        field.state.selectAll()

        assertNull(field.host.copyFocusedSelection())
        assertNull(field.host.cutFocusedSelection())
        assertEquals(SECRET, field.state.text, "cut removed text it did not hand over")
    }

    @Test
    fun aPlainFieldStillCopiesAndCuts() {
        val field = oracle("hello")
        field.focus()
        field.state.select(anchor = 1, focus = 3)

        assertEquals("el", field.host.copyFocusedSelection())
        assertEquals("el", field.host.cutFocusedSelection())
        assertEquals("hlo", field.state.text)
        assertNull(field.host.copyFocusedSelection(), "a collapsed selection copied something")
    }

    @Test
    fun theFrameAsksForAPasswordKeyboardOnlyWhileAMaskedFieldHasFocus() {
        val field = masked()
        assertFalse(field.frame().effects.passwordKeyboard, "an unfocused field asked for a keyboard")

        field.focus()
        val effects = field.frame().effects

        assertTrue(effects.requestKeyboard)
        assertTrue(effects.passwordKeyboard)

        val plain = oracle()
        plain.focus()
        assertFalse(plain.frame().effects.passwordKeyboard)
    }

    @Test
    fun anEmptyMaskedFieldShowsItsPlaceholderUnmasked() {
        val host = ComposeHost()
        val withPlaceholder = host.frame(FrameInput(200, 40)) {
            BasicTextField(TextFieldState(), Modifier.size(160.dp, 20.dp), placeholder = "Password", mask = MASK)
        }
        val plainPlaceholder = ComposeHost().frame(FrameInput(200, 40)) {
            BasicTextField(TextFieldState(), Modifier.size(160.dp, 20.dp), placeholder = "Password", singleLine = true)
        }

        assertEquals(plainPlaceholder.glyphs(), withPlaceholder.glyphs())
    }
}
