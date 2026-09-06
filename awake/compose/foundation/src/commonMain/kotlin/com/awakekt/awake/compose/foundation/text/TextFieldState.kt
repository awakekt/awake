/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.core.input.ImeComposition
import com.awakekt.awake.core.input.TextEditAction

/**
 * The text in a field and where the caret sits in it.
 *
 * Caller-owned and held with [rememberTextFieldState], for the reason `02-modifier.md` states: a
 * link is rebuilt every pass, so text kept inside one would be lost on the next frame -- which for
 * a text field means every keystroke vanishing.
 *
 * Plain fields, deliberately: nothing here is observable state. `BasicTextField` reads this during
 * layout and paint rather than capturing it at composition, so a keystroke reaches the glyphs
 * through the phase that already runs every frame -- the same discipline Compose's own
 * `BasicTextField` follows, and the reason typing there needs no recomposition.
 *
 * An earlier revision counter mirrored into `mutableStateOf` existed only because the text *was*
 * captured at composition, which left the glyphs stale until something unrelated re-ran the
 * composable. Fixing the phase removed the need for it.
 */
class TextFieldState(text: String = "", cursor: Int = text.length) {
    var text: String = text
        private set

    /** Character index the caret sits before. Always within `0..text.length`. */
    var cursor: Int = cursor.coerceIn(0, text.length)
        private set

    /** Fixed end of the current selection; equals [cursor] when the selection is collapsed. */
    var selectionAnchor: Int = cursor
        private set

    val selectionStart: Int get() = minOf(selectionAnchor, cursor)
    val selectionEnd: Int get() = maxOf(selectionAnchor, cursor)
    val hasSelection: Boolean get() = selectionStart != selectionEnd
    val selectedText: String get() = text.substring(selectionStart, selectionEnd)

    private var compositionRangeStart = cursor
    private var compositionRangeEnd = cursor
    private var compositionText = ""
    private var compositionSelectionStart = 0
    private var compositionSelectionEnd = 0

    /** Whether an IME is showing text that has not yet entered the caller-owned [text]. */
    val hasComposition: Boolean get() = compositionText.isNotEmpty() || compositionRangeStart != compositionRangeEnd

    /**
     * [text] with any in-progress IME composition spliced in.
     *
     * Gated on [hasComposition] rather than splicing unconditionally. The range is only meaningful
     * while a composition is live: [setText] parks it at the new cursor, and a later delete shortens
     * the text without moving it, so an inactive range routinely points past the end. Splicing with
     * it regardless threw out of the text field's own draw -- `setText` followed by a backspace was
     * enough, from any caller.
     */
    internal val displayedText: String
        get() = if (!hasComposition) {
            text
        } else {
            text.substring(0, compositionRangeStart) + compositionText + text.substring(compositionRangeEnd)
        }

    internal val displayedCursor: Int
        get() = if (hasComposition) compositionRangeStart + compositionSelectionEnd else cursor

    internal val displayedSelectionStart: Int
        get() = if (hasComposition) compositionRangeStart + compositionSelectionStart else selectionStart

    internal val displayedSelectionEnd: Int
        get() = if (hasComposition) compositionRangeStart + compositionSelectionEnd else selectionEnd

    private var preferredColumn: Int? = null

    fun setText(newText: String, newCursor: Int = newText.length) {
        text = newText
        cursor = newCursor.coerceIn(0, newText.length)
        selectionAnchor = cursor
        preferredColumn = null
        clearComposition()
    }

    fun moveCursorTo(index: Int) {
        clearComposition()
        cursor = index.coerceIn(0, text.length)
        selectionAnchor = cursor
        preferredColumn = null
    }

    /** Inserts at the caret and leaves the caret after what was inserted. */
    fun insert(inserted: String) {
        commitComposition()
        if (inserted.isEmpty()) return
        text = text.substring(0, selectionStart) + inserted + text.substring(selectionEnd)
        cursor = selectionStart + inserted.length
        selectionAnchor = cursor
        preferredColumn = null
    }

    fun apply(action: TextEditAction) {
        commitComposition()
        when (action) {
            // Deletes before the caret and moves back; a backspace at index 0 is a no-op, not an
            // exception -- holding the key at the start of a field is normal.
            TextEditAction.Backspace -> if (deleteSelection()) {
                Unit
            } else if (cursor > 0) {
                text = text.substring(0, cursor - 1) + text.substring(cursor)
                cursor--
                selectionAnchor = cursor
                preferredColumn = null
            }
            // Deletes after the caret and leaves it put, which is what makes Delete different.
            TextEditAction.Delete -> if (deleteSelection()) {
                Unit
            } else if (cursor < text.length) {
                text = text.substring(0, cursor) + text.substring(cursor + 1)
                preferredColumn = null
            }
            TextEditAction.ArrowLeft -> if (hasSelection) {
                moveCursorTo(selectionStart)
            } else if (cursor > 0) {
                cursor--
                selectionAnchor = cursor
                preferredColumn = null
            }
            TextEditAction.ArrowRight -> if (hasSelection) {
                moveCursorTo(selectionEnd)
            } else if (cursor < text.length) {
                cursor++
                selectionAnchor = cursor
                preferredColumn = null
            }
            TextEditAction.Home -> {
                moveCursorTo(lineStart(cursor))
            }
            TextEditAction.End -> {
                moveCursorTo(lineEnd(cursor))
            }
            // Listed rather than swept into an `else`: a new action must break the build here and
            // force a decision, which is what an `else` would silently absorb.
            //
            // Enter has nowhere to go in a single-line field -- a caller wanting submit reads it
            // through `onKeyEvent`, where it is visible, rather than having it swallowed here.
            // Vertical motion has no meaning until there is more than one line.
            TextEditAction.Enter -> Unit
            TextEditAction.ArrowUp -> moveVertical(-1)
            TextEditAction.ArrowDown -> moveVertical(1)
        }
    }

    fun select(anchor: Int, focus: Int) {
        clearComposition()
        selectionAnchor = anchor.coerceIn(0, text.length)
        cursor = focus.coerceIn(0, text.length)
        preferredColumn = null
    }

    /** Selects the full current value, as a Ctrl/Cmd+A adapter needs. */
    fun selectAll() = select(0, text.length)

    /** Updates the visible pre-edit range without changing the caller-owned committed [text]. */
    fun setComposition(composition: ImeComposition) {
        if (!hasComposition) {
            compositionRangeStart = selectionStart
            compositionRangeEnd = selectionEnd
        }
        compositionText = composition.text
        compositionSelectionStart = composition.selectionStart.coerceIn(0, composition.text.length)
        compositionSelectionEnd = composition.selectionEnd.coerceIn(0, composition.text.length)
    }

    /** Finalizes [committed] over the range captured when composition began. */
    fun commitComposition(committed: String = compositionText) {
        if (!hasComposition) {
            if (committed.isNotEmpty()) insert(committed)
            return
        }
        text = text.substring(0, compositionRangeStart) + committed + text.substring(compositionRangeEnd)
        cursor = compositionRangeStart + committed.length
        selectionAnchor = cursor
        preferredColumn = null
        clearComposition()
    }

    fun cancelComposition() = clearComposition()

    private fun clearComposition() {
        compositionRangeStart = cursor
        compositionRangeEnd = cursor
        compositionText = ""
        compositionSelectionStart = 0
        compositionSelectionEnd = 0
    }

    private fun deleteSelection(): Boolean {
        if (!hasSelection) return false
        text = text.removeRange(selectionStart, selectionEnd)
        cursor = selectionStart
        selectionAnchor = cursor
        preferredColumn = null
        return true
    }

    private fun moveVertical(direction: Int) {
        val currentStart = lineStart(cursor)
        val currentEnd = lineEnd(cursor)
        val column = preferredColumn ?: (cursor - currentStart).also { preferredColumn = it }
        val target = if (direction < 0) {
            if (currentStart == 0) return
            val targetEnd = currentStart - 1
            val targetStart = lineStart(targetEnd)
            targetStart + column.coerceAtMost(targetEnd - targetStart)
        } else {
            if (currentEnd == text.length) return
            val targetStart = currentEnd + 1
            val targetEnd = lineEnd(targetStart)
            targetStart + column.coerceAtMost(targetEnd - targetStart)
        }
        cursor = target
        selectionAnchor = cursor
    }

    private fun lineStart(index: Int): Int =
        if (index <= 0) 0 else text.lastIndexOf('\n', index - 1) + 1

    private fun lineEnd(index: Int): Int = text.indexOf('\n', index).let { if (it < 0) text.length else it }

    override fun toString(): String = "TextFieldState(\"$text\", cursor=$cursor)"
}

/** A [TextFieldState] that survives the next pass. */
context(_: Composer)
fun rememberTextFieldState(initial: String = ""): TextFieldState =
    remember { TextFieldState(initial) }
