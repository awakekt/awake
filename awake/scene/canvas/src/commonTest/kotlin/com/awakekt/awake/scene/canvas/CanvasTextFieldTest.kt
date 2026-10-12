/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.canvas

import com.awakekt.awake.compose.ui.input.key.KeyEvent
import com.awakekt.awake.compose.ui.input.key.KeyEventType
import com.awakekt.awake.compose.ui.platform.ComposeHost
import com.awakekt.awake.compose.ui.platform.FrameInput
import com.awakekt.awake.compose.ui.platform.FrameOutput
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.TextEditAction
import com.awakekt.awake.ecs.World
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A `TextField` element the player types into: `text` two ways, `focused` through the queued
 * `FocusRequester`, Enter submitting through `consumeSubmit()`, Escape freeing without submitting.
 */
class CanvasTextFieldTest {

    private val world = World()
    private val host = ComposeHost()

    private fun textField(configure: CanvasElement.() -> Unit = {}): CanvasElement {
        val element = CanvasElement().apply { kind = CanvasElementKind.TextField }.apply(configure)
        world.add(world.create(), element)
        return element
    }

    private fun frame(
        typedText: String = "",
        editActions: List<TextEditAction> = emptyList(),
        keyEvents: List<KeyEvent> = emptyList(),
    ): FrameOutput = host.frame(
        FrameInput(WIDTH, HEIGHT, typedText = typedText, editActions = editActions, keyEvents = keyEvents),
    ) { SceneCanvas(world) }

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

        assertTrue(field.consumeSubmit())
        assertFalse(field.consumeSubmit(), "consumeSubmit must answer true only once per Enter")
        // Still focused for the rest of the frame Enter arrived on.
        val released = frame()
        assertFalse(released.ownership.isTextInputFocused, "focus must be released by the frame after Enter")
    }

    @Test
    fun escapeReleasesFocusWithoutSubmitting() {
        val field = textField()
        field.focus()

        frame(keyEvents = listOf(KeyEvent(Key.Escape, KeyEventType.Down)))

        assertFalse(field.consumeSubmit(), "Escape must not submit")
        val released = frame()
        assertFalse(released.ownership.isTextInputFocused, "focus must be released by the frame after Escape")
    }

    private companion object {
        const val WIDTH = 200
        const val HEIGHT = 200
    }
}
