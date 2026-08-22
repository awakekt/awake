// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.foundation.text

import io.github.ronjunevaldoz.awake.compose.runtime.Composer
import io.github.ronjunevaldoz.awake.compose.runtime.remember
import io.github.ronjunevaldoz.awake.compose.ui.text.input.EditCommand

/**
 * The text in a field and where the caret sits in it.
 *
 * Caller-owned and held with [rememberTextFieldState], for the reason `02-modifier.md` states: a
 * link is rebuilt every pass, so text kept inside one would be lost on the next frame -- which for
 * a text field means every keystroke vanishing.
 *
 * Caret only; no selection yet. Selection needs anchor plus focus and a decision about shift-drag,
 * and shipping half of it would leave `selectionStart` present and inert.
 */
class TextFieldState(text: String = "", cursor: Int = text.length) {

    var text: String = text
        private set

    /** Character index the caret sits before. Always within `0..text.length`. */
    var cursor: Int = cursor.coerceIn(0, text.length)
        private set

    fun setText(newText: String, newCursor: Int = newText.length) {
        text = newText
        cursor = newCursor.coerceIn(0, newText.length)
    }

    fun moveCursorTo(index: Int) {
        cursor = index.coerceIn(0, text.length)
    }

    /** Inserts at the caret and leaves the caret after what was inserted. */
    fun insert(inserted: String) {
        if (inserted.isEmpty()) return
        text = text.substring(0, cursor) + inserted + text.substring(cursor)
        cursor += inserted.length
    }

    fun apply(command: EditCommand) {
        when (command) {
            // Deletes before the caret and moves back; a backspace at index 0 is a no-op, not an
            // exception -- holding the key at the start of a field is normal.
            EditCommand.Backspace -> if (cursor > 0) {
                text = text.substring(0, cursor - 1) + text.substring(cursor)
                cursor--
            }
            // Deletes after the caret and leaves it put, which is what makes Delete different.
            EditCommand.Delete -> if (cursor < text.length) {
                text = text.substring(0, cursor) + text.substring(cursor + 1)
            }
            EditCommand.MoveLeft -> if (cursor > 0) cursor--
            EditCommand.MoveRight -> if (cursor < text.length) cursor++
            EditCommand.MoveHome -> cursor = 0
            EditCommand.MoveEnd -> cursor = text.length
        }
    }

    override fun toString(): String = "TextFieldState(\"$text\", cursor=$cursor)"
}

/** A [TextFieldState] that survives the next pass. */
context(_: Composer)
fun rememberTextFieldState(initial: String = ""): TextFieldState =
    remember { TextFieldState(initial) }
