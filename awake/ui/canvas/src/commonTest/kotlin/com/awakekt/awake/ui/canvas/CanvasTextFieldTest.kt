/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.compose.ui.semantics.SemanticsNode
import com.awakekt.awake.compose.ui.semantics.SemanticsProperties
import com.awakekt.awake.core.graphics2d.DrawCommand
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A `TextField` element the player types into: `text` two ways, `focused` through the queued
 * `FocusRequester`, Enter submitting through `consumeSubmit()`, Escape freeing without submitting.
 */
class CanvasTextFieldTest {

    private val host = ComposeHost()
    private var nextId = 0
    private var items: List<CanvasItem> = emptyList()

    private fun textField(configure: CanvasElement.() -> Unit = {}): CanvasElement {
        val element = CanvasElement().apply { kind = CanvasElementKind.TextField }.apply(configure)
        items = items + CanvasItem(nextId++, element)
        return element
    }

    private fun frame(
        typedText: String = "",
        editActions: List<TextEditAction> = emptyList(),
        keyEvents: List<KeyEvent> = emptyList(),
    ): FrameOutput = host.frame(
        FrameInput(WIDTH, HEIGHT, typedText = typedText, editActions = editActions, keyEvents = keyEvents),
    ) { CanvasOverlay(items) }

    /** Composes the field once, so its modifier chain attaches, then takes its focus. */
    private fun CanvasElement.focus() {
        frame()
        focused = true
        frame() // the queued request is applied at the start of this frame.
    }

    @Test
    fun aHiddenFieldShownAndFocusedInOneStepTakesTheKeyboard() {
        val field = textField { visible = false }
        frame()

        field.visible = true
        field.focused = true
        frame() // the field composes and attaches
        val output = frame() // the waiting request is applied

        assertTrue(field.focused)
        assertTrue(output.ownership.isTextInputFocused)
    }

    @Test
    fun typingFillsTheFocusedFieldsText() {
        val field = textField()
        field.focus()

        frame(typedText = "hi") // typed text lands in the field's live state this frame...
        frame() // ...and reaches `text` the next time the element composes.

        assertEquals("hi", field.text)
    }

    @Test
    fun aSystemWritingTextReplacesTheFieldsDisplayedContentWithTheCaretAtTheEnd() {
        val field = textField()
        field.focus()
        frame(typedText = "hello")
        frame()
        assertEquals("hello", field.text, "typing must have reached text first, or clearing it proves nothing")

        field.text = "" // the system "sends" and clears it.
        frame()

        assertEquals("", field.textFieldState.text)
        assertEquals(0, field.textFieldState.cursor)
    }

    @Test
    fun focusedTrueFocusesTheFieldAndTheFrameReportsTextFocus() {
        val field = textField()
        frame()
        assertFalse(frame().ownership.isTextInputFocused, "nothing asked for focus yet")

        field.focused = true
        val output = frame()

        assertTrue(output.ownership.isTextInputFocused)
    }

    @Test
    fun enterSubmitsOnceAndReleasesFocusTheNextFrame() {
        val field = textField()
        field.focus()

        frame(editActions = listOf(TextEditAction.Enter))

        assertEquals("", field.consumeSubmit())
        assertNull(field.consumeSubmit(), "consumeSubmit must answer non-null only once per Enter")
        // Still focused for the rest of the frame Enter arrived on.
        val released = frame()
        assertFalse(released.ownership.isTextInputFocused, "focus must be released by the frame after Enter")
    }

    @Test
    fun theLineTypedAndSubmittedInOneFrameComesBackWholeFromConsumeSubmit() {
        val field = textField()
        field.focus()

        frame(typedText = "hi", editActions = listOf(TextEditAction.Enter))

        assertEquals("hi", field.consumeSubmit(), "this frame's typing must be in what Enter submits, not just `text`")
        assertEquals("hi", field.text, "the submit brings `text` up to date, so a write after it counts as one")
    }

    @Test
    fun aLineTypedAndSubmittedInOneFrameThenClearedLeavesTheFieldEmpty() {
        val field = textField()
        field.focus()
        frame(typedText = "hi", editActions = listOf(TextEditAction.Enter))
        assertEquals("hi", field.consumeSubmit())

        field.text = "" // the game sends the line and empties the field.
        frame()

        assertEquals("", field.text, "the sent line must not come back")
        assertEquals("", field.textFieldState.text)
    }

    @Test
    fun enterThatFocusesAFieldOnThisFrameDoesNotAlsoSubmitIt() {
        val field = textField()
        frame() // compose once so the field's modifier chain attaches

        // A system reacting to this frame's Enter going down, the way the bug report's chat
        // window does -- the same Enter also reaches this frame's dispatch below.
        field.focused = true
        val focusFrame = frame(editActions = listOf(TextEditAction.Enter))

        assertNull(field.consumeSubmit(), "the Enter that opened the field must not also submit it")
        assertTrue(focusFrame.ownership.isTextInputFocused, "the field must still take this frame's focus request")

        frame(editActions = listOf(TextEditAction.Enter)) // a later, real Enter

        assertEquals("", field.consumeSubmit(), "a later Enter submits normally")
    }

    @Test
    fun escapeReleasesFocusWithoutSubmitting() {
        val field = textField()
        field.focus()

        frame(keyEvents = listOf(KeyEvent(Key.Escape, KeyEventType.Down)))

        assertNull(field.consumeSubmit(), "Escape must not submit")
        val released = frame()
        assertFalse(released.ownership.isTextInputFocused, "focus must be released by the frame after Escape")
    }

    @Test
    fun aMaskedFieldDrawsTheSameForAnyTextOfOneLength() {
        fun glyphs(text: String, mask: String) = CanvasTextFieldTest().run {
            textField { this.text = text; this.mask = mask }
            frame().primitives.filterIsInstance<DrawCommand.Glyph>()
        }

        assertTrue(glyphs("abcd", "*").isNotEmpty(), "the masked field drew nothing")
        assertEquals(glyphs("abcd", "*"), glyphs("wxyz", "*"))
        assertNotEquals(glyphs("abcd", ""), glyphs("wxyz", ""), "without a mask the two texts must draw apart")
    }

    @Test
    fun clearingTheMaskShowsThePassword() {
        val field = textField { text = "abcd"; mask = "*" }
        val masked = frame().primitives.filterIsInstance<DrawCommand.Glyph>()

        field.mask = ""
        val shown = frame().primitives.filterIsInstance<DrawCommand.Glyph>()

        assertNotEquals(masked, shown)
        assertEquals("abcd", field.text)
    }

    @Test
    fun aMaskedFieldIsAPasswordToAccessibility() {
        fun flatten(nodes: List<SemanticsNode>): List<SemanticsNode> = nodes.flatMap { listOf(it) + flatten(it.children) }
        val field = textField { mask = "*" }

        fun passwords() = flatten(frame().semantics).count { it.config[SemanticsProperties.Password] == true }

        assertEquals(1, passwords())
        field.mask = ""
        assertEquals(0, passwords(), "a plain field was announced as a password")
    }

    @Test
    fun copyFromAMaskedFieldYieldsNothing() {
        val field = textField { text = "secret"; mask = "*" }
        field.focus()
        field.textFieldState.selectAll()

        assertNull(host.copyFocusedSelection())
        field.mask = ""
        frame()
        assertEquals("secret", host.copyFocusedSelection(), "a plain field must still copy")
    }

    @Test
    fun theArrowKeysMoveTheCaretInAMaskedField() {
        val field = textField { mask = "*" }
        field.focus()
        frame(typedText = "abcd")

        frame(editActions = listOf(TextEditAction.ArrowLeft, TextEditAction.ArrowLeft))

        assertEquals(2, field.textFieldState.cursor)
    }

    private companion object {
        const val WIDTH = 200
        const val HEIGHT = 200
    }
}
