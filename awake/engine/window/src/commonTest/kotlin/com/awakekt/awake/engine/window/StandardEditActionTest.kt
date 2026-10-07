/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.ClipboardCommand
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.TextEditAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StandardEditActionTest {
    private fun focusedInput() = Input().apply { textInputFocused = true }

    @Test
    fun theFourEditSelectorsNameTheirActionsAndNothingElseDoes() {
        assertEquals(StandardEditAction.Copy, standardEditAction("copy:"))
        assertEquals(StandardEditAction.Cut, standardEditAction("cut:"))
        assertEquals(StandardEditAction.Paste, standardEditAction("paste:"))
        assertEquals(StandardEditAction.SelectAll, standardEditAction("selectAll:"))
        assertNull(standardEditAction("select:"))
        assertNull(standardEditAction("copy"))
        assertNull(standardEditAction(null))
    }

    @Test
    fun withoutAFocusedFieldTheActionIsLeftToThePlatform() {
        val input = Input()
        StandardEditAction.entries.forEach { action ->
            assertFalse(input.applyStandardEditAction(action) { "pasted" }, "$action was taken without focus")
        }
        val snapshot = input.updateSnapshot()
        assertEquals(emptyList(), snapshot.clipboardCommands)
        assertEquals(emptyList(), snapshot.editActions)
        assertEquals("", snapshot.typedText)
    }

    @Test
    fun copyAndCutAskTheFieldForItsAnswerAndTypeNothing() {
        val input = focusedInput()
        assertTrue(input.applyStandardEditAction(StandardEditAction.Copy) { error("copy read the pasteboard") })
        assertTrue(input.applyStandardEditAction(StandardEditAction.Cut) { error("cut read the pasteboard") })
        val snapshot = input.updateSnapshot()
        assertEquals(listOf(ClipboardCommand.Copy, ClipboardCommand.Cut), snapshot.clipboardCommands)
        assertEquals("", snapshot.typedText)
    }

    @Test
    fun pasteTypesThePasteboardTextAndAnEmptyPasteboardTypesNothing() {
        val input = focusedInput()
        assertTrue(input.applyStandardEditAction(StandardEditAction.Paste) { "pasted 😀" })
        assertEquals("pasted 😀", input.updateSnapshot().typedText)
        assertTrue(input.applyStandardEditAction(StandardEditAction.Paste) { "" })
        assertTrue(input.applyStandardEditAction(StandardEditAction.Paste) { null })
        assertEquals("", input.updateSnapshot().typedText)
    }

    @Test
    fun selectAllSelectsTheWholeField() {
        val input = focusedInput()
        assertTrue(input.applyStandardEditAction(StandardEditAction.SelectAll) { null })
        assertEquals(listOf(TextEditAction.SelectAll), input.updateSnapshot().editActions)
    }

    @Test
    fun theFieldsAnswerIsWrittenOnceAndAnEmptyAnswerIsNot() {
        val input = focusedInput()
        val written = mutableListOf<String>()
        input.clipboardWrite = "selected text"
        input.syncClipboardWrite { written += it }
        input.syncClipboardWrite { written += it }
        input.clipboardWrite = ""
        input.syncClipboardWrite { written += it }
        assertEquals(listOf("selected text"), written)
    }
}
