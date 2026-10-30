/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.core.graphics2d.UiDrawPrimitive
import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A TextField's `padding`: the room between its edge and its text, which text, caret, selection and a
 * press all measure from, and which the text scrolls inside.
 */
class CanvasTextFieldPaddingTest {
    private val host = ComposeHost()
    private val field = CanvasElement().apply {
        kind = CanvasElementKind.TextField
        offsetX = FIELD_X
        offsetY = 10f
        width = FIELD_WIDTH
        height = 40f
        fontSize = 14f
    }
    private val items = listOf(CanvasItem(0, field))

    private fun frame(
        typedText: String = "",
        editActions: List<TextEditAction> = emptyList(),
        pointer: Pair<Int, Int>? = null,
        down: Boolean = false,
        pressed: Boolean = false,
    ): FrameOutput = host.frame(
        FrameInput(
            WIDTH,
            HEIGHT,
            pointerX = pointer?.first ?: FrameInput.UNKNOWN_POINTER,
            pointerY = pointer?.second ?: FrameInput.UNKNOWN_POINTER,
            pointerDown = down,
            pointerPressed = pressed,
            typedText = typedText,
            editActions = editActions,
        ),
    ) { CanvasOverlay(items) }

    private fun FrameOutput.glyphs() = primitives.filterIsInstance<UiDrawPrimitive.Glyph>()

    /** The caret: the field's only one-pixel-wide quad. */
    private fun FrameOutput.caret() = primitives.filterIsInstance<UiDrawPrimitive.Quad>().single { it.w == 1f }

    /** The selection highlight: a field's translucent quad at 35% of its text colour. */
    private fun FrameOutput.selection() = primitives.filterIsInstance<UiDrawPrimitive.Quad>().first { it.color.a in 0.3f..0.4f && it.w > 1f }

    private fun focusAndType(text: String) {
        frame()
        field.focused = true
        frame()
        frame(typedText = text)
        frame()
    }

    @Test
    fun theDefaultPaddingIsShadcnsInputAcrossAndNothingAbove() {
        assertEquals(CanvasPadding(horizontal = 12f, vertical = 0f), field.padding)
        assertEquals(CanvasPadding(0f, 0f), CanvasPadding.None)
    }

    @Test
    fun paddingMovesTheTextByItsAmount() {
        field.text = "hello"
        field.padding = CanvasPadding.None
        val bare = frame().glyphs().first().x

        field.padding = CanvasPadding(horizontal = 20f, vertical = 0f)
        val padded = frame().glyphs().first().x

        assertEquals(20f, padded - bare, 0.01f)
    }

    @Test
    fun paddingMovesTheCaretWithTheText() {
        field.padding = CanvasPadding.None
        focusAndType("hello")
        val bare = frame().caret().x

        field.padding = CanvasPadding(horizontal = 20f, vertical = 0f)
        val padded = frame().caret().x

        assertEquals(20f, padded - bare, 0.01f)
    }

    @Test
    fun paddingMovesTheSelectionWithTheText() {
        field.padding = CanvasPadding.None
        focusAndType("hello")
        field.textFieldState.selectAll()
        val bare = frame().selection()

        field.padding = CanvasPadding(horizontal = 20f, vertical = 0f)
        val padded = frame().selection()

        assertEquals(20f, padded.x - bare.x, 0.01f)
        assertEquals(bare.w, padded.w, 0.01f, "the selection is as wide as the text, whatever the padding")
    }

    @Test
    fun aPressPutsTheCaretOnTheCharacterUnderThePointerInsideThePadding() {
        field.text = "hello"

        fun cursorAfterPressOnTheThirdCharacter(padding: CanvasPadding): Int {
            field.padding = padding
            val third = frame().glyphs()[2]
            val x = (third.x + third.w / 2f).toInt()
            frame(pointer = x to 30)
            frame(pointer = x to 30, down = true, pressed = true)
            return field.textFieldState.cursor
        }

        val bare = cursorAfterPressOnTheThirdCharacter(CanvasPadding.None)
        field.textFieldState.moveCursorTo(0)
        val padded = cursorAfterPressOnTheThirdCharacter(CanvasPadding(horizontal = 30f, vertical = 0f))

        assertTrue(bare in 1..4, "the press missed the text altogether: caret at $bare")
        assertEquals(bare, padded, "the caret lands on the same character wherever the padding puts the text")
    }

    @Test
    fun aPressInThePaddingStillFocusesTheField() {
        field.padding = CanvasPadding(horizontal = 30f, vertical = 0f)
        frame()
        val inThePadding = FIELD_X.toInt() + 10 to 30

        frame(pointer = inThePadding)
        val output = frame(pointer = inThePadding, down = true, pressed = true)

        assertTrue(output.ownership.isTextInputFocused)
        assertTrue(field.focused)
    }

    @Test
    fun theTextScrollsToKeepTheCaretInsideThePadding() {
        field.padding = CanvasPadding(horizontal = 25f, vertical = 0f)
        focusAndType("a line far longer than the field is wide")

        val caret = frame().caret()

        val inner = FIELD_X + FIELD_WIDTH - 25f
        assertTrue(caret.x <= inner + 1f, "the caret at ${caret.x} reaches past the padding, which starts at $inner")
        assertTrue(caret.x > FIELD_X + 25f, "the caret scrolled all the way back to the start")
    }

    @Test
    fun scrolledTextIsCutAtTheTextBoxNotShownInThePadding() {
        field.padding = CanvasPadding(horizontal = 25f, vertical = 0f)
        focusAndType("a line far longer than the field is wide")
        val output = frame()

        val clip = output.primitives.filterIsInstance<UiDrawPrimitive.ClipPush>().first { it.rect.width < WIDTH }.rect

        // Scrolled past the start, the clip keeps the text out of the left padding.
        assertEquals(FIELD_X + 25f, clip.x, 0.01f)
    }

    private companion object {
        const val WIDTH = 400
        const val HEIGHT = 80
        const val FIELD_X = 20f
        const val FIELD_WIDTH = 160f
    }
}
