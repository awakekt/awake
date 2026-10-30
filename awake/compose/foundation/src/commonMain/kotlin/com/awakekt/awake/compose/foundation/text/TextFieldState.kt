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
import com.awakekt.awake.core.text.unicode.GraphemeClusters
import com.awakekt.awake.core.text.unicode.TextLengthUnit
import com.awakekt.awake.core.text.unicode.WordBoundaries

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
 *
 * Edits step by grapheme cluster ([GraphemeClusters]), what a reader sees as one character: an
 * arrow key moves over a whole flag or ZWJ emoji, Backspace and Delete remove one whole, and the
 * caret and both ends of the selection only ever sit between clusters. A caret given inside a
 * cluster moves back to the cluster's start, and a selection widens to whole clusters. Indices are
 * still `Char` indices into [text].
 */
class TextFieldState(text: String = "", cursor: Int = text.length) {
    /** The committed text, excluding any in-progress IME composition. */
    var text: String = text
        private set

    /** Character index the caret sits before: a cluster boundary within `0..text.length`. */
    var cursor: Int = GraphemeClusters.floorBoundary(text, cursor)
        private set

    /**
     * The most [text] that typing, pasting or an IME commit may leave, counted in [maxLengthUnit];
     * null, the default, for no limit.
     *
     * Input over the limit is cut at the last cluster that fits and the rest dropped, the way a
     * browser's `maxlength` treats a paste, so a flag or an emoji sequence is let in whole or not at
     * all. A character that joins the cluster before it, such as a skin tone after a thumbs-up, adds
     * no grapheme and is let in at the limit. [setText] is not limited: text a program sets is its
     * own to check.
     */
    var maxLength: Int? = null
        set(value) {
            require(value == null || value >= 0) { "maxLength must not be negative, was $value" }
            field = value
        }

    /**
     * What [maxLength] counts: grapheme clusters by default, the characters a person sees. Count
     * [TextLengthUnit.CodePoints] or [TextLengthUnit.Utf8Bytes] instead to match a server that
     * stores a line in a fixed number of either, so nothing the field accepts is refused there.
     */
    var maxLengthUnit: TextLengthUnit = TextLengthUnit.Graphemes

    /** Fixed end of the current selection; equals [cursor] when the selection is collapsed. */
    var selectionAnchor: Int = this.cursor
        private set

    /** The lower end of the selection, whichever of [selectionAnchor] and [cursor] comes first. */
    val selectionStart: Int get() = minOf(selectionAnchor, cursor)

    /** The upper end of the selection, whichever of [selectionAnchor] and [cursor] comes last. */
    val selectionEnd: Int get() = maxOf(selectionAnchor, cursor)

    /** Whether the selection covers at least one character. */
    val hasSelection: Boolean get() = selectionStart != selectionEnd

    /** The selected part of [text], empty when nothing is selected. */
    val selectedText: String get() = text.substring(selectionStart, selectionEnd)

    private var compositionRangeStart = this.cursor
    private var compositionRangeEnd = this.cursor
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

    /**
     * Replaces the whole [text] with [newText] and puts a collapsed caret at [newCursor], clamped to
     * the new text and moved back to a cluster boundary.
     *
     * Any in-progress IME composition is discarded. [maxLength] does not apply.
     */
    fun setText(newText: String, newCursor: Int = newText.length) {
        text = newText
        cursor = GraphemeClusters.floorBoundary(newText, newCursor)
        selectionAnchor = cursor
        preferredColumn = null
        clearComposition()
    }

    /**
     * Collapses the selection to a caret at [index], clamped to the text and moved back to a cluster
     * boundary.
     *
     * Any in-progress IME composition is discarded.
     */
    fun moveCursorTo(index: Int) {
        clearComposition()
        cursor = GraphemeClusters.floorBoundary(text, index)
        selectionAnchor = cursor
        preferredColumn = null
    }

    /**
     * Inserts at the caret, replacing the selection, and leaves the caret after what was inserted.
     * Only as much as [maxLength] allows goes in.
     */
    fun insert(inserted: String) {
        commitComposition()
        replace(selectionStart, selectionEnd, inserted)
    }

    /**
     * Puts as much of [inserted] as [maxLength] allows in place of `text[start until end]`, caret
     * after it. Inserting nothing over a selection still deletes it, as typing over a selection at
     * the limit does.
     */
    private fun replace(start: Int, end: Int, inserted: String) {
        val before = text.substring(0, start)
        val after = text.substring(end)
        val kept = inserted.substring(0, allowedLength(before, inserted, after))
        if (kept.isEmpty() && start == end) return
        text = before + kept + after
        cursor = GraphemeClusters.floorBoundary(text, start + kept.length)
        selectionAnchor = cursor
        preferredColumn = null
    }

    /** How many `Char`s of [inserted] fit between [before] and [after]: whole clusters only. */
    private fun allowedLength(before: String, inserted: String, after: String): Int {
        val limit = maxLength ?: return inserted.length
        val unit = maxLengthUnit
        fun fits(end: Int) = unit.count(before + inserted.substring(0, end) + after) <= limit
        // Counted alone first, then checked in place: a cluster can join its neighbours and count
        // for less there, or, rarely, split one and count for more.
        var end = unit.fit(inserted, (limit - unit.count(before + after)).coerceAtLeast(0))
        while (end < inserted.length) {
            val next = GraphemeClusters.nextBoundary(inserted, end)
            if (!fits(next)) break
            end = next
        }
        while (end > 0 && !fits(end)) end = GraphemeClusters.previousBoundary(inserted, end)
        return end
    }

    /**
     * Applies a key-driven edit or caret move, committing any IME composition first.
     *
     * Backspace and Delete remove the selection if there is one, otherwise one whole grapheme
     * cluster; the arrow keys step over one.
     * [TextEditAction.Enter] is ignored here: `BasicTextField` reads it itself, to call its own
     * `onSubmit` in a single-line field or insert a newline in a multi-line one.
     */
    fun apply(action: TextEditAction) {
        commitComposition()
        when (action) {
            // Deletes before the caret and moves back; a backspace at index 0 is a no-op, not an
            // exception -- holding the key at the start of a field is normal.
            TextEditAction.Backspace -> if (deleteSelection()) {
                Unit
            } else if (cursor > 0) {
                val start = GraphemeClusters.previousBoundary(text, cursor)
                text = text.substring(0, start) + text.substring(cursor)
                cursor = start
                selectionAnchor = cursor
                preferredColumn = null
            }
            // Deletes after the caret and leaves it put, which is what makes Delete different.
            TextEditAction.Delete -> if (deleteSelection()) {
                Unit
            } else if (cursor < text.length) {
                text = text.substring(0, cursor) + text.substring(GraphemeClusters.nextBoundary(text, cursor))
                preferredColumn = null
            }
            TextEditAction.ArrowLeft -> if (hasSelection) {
                moveCursorTo(selectionStart)
            } else if (cursor > 0) {
                cursor = GraphemeClusters.previousBoundary(text, cursor)
                selectionAnchor = cursor
                preferredColumn = null
            }
            TextEditAction.ArrowRight -> if (hasSelection) {
                moveCursorTo(selectionEnd)
            } else if (cursor < text.length) {
                cursor = GraphemeClusters.nextBoundary(text, cursor)
                selectionAnchor = cursor
                preferredColumn = null
            }
            TextEditAction.WordLeft -> {
                val from = if (hasSelection) selectionStart else cursor
                moveCursorTo(WordBoundaries.previousWordBoundary(text, from))
            }
            TextEditAction.WordRight -> {
                val from = if (hasSelection) selectionEnd else cursor
                moveCursorTo(WordBoundaries.nextWordBoundary(text, from))
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
            // `BasicTextField.onEditAction` intercepts Enter before it ever reaches `apply`, to
            // call `onSubmit` or insert a newline. Never swallowed here.
            // Vertical motion has no meaning until there is more than one line.
            TextEditAction.Enter -> Unit
            TextEditAction.ArrowUp -> moveVertical(-1)
            TextEditAction.ArrowDown -> moveVertical(1)
            TextEditAction.SelectAll -> selectAll()
        }
    }

    /**
     * Selects from [anchor] to [focus], both clamped to the text and widened to cluster boundaries,
     * so a selection never holds part of a character.
     *
     * The caret ends at [focus] and [anchor] stays fixed, so the selection can run in either
     * direction. Any in-progress IME composition is discarded.
     */
    fun select(anchor: Int, focus: Int) {
        clearComposition()
        val forward = anchor <= focus
        selectionAnchor = if (forward) GraphemeClusters.floorBoundary(text, anchor) else GraphemeClusters.ceilBoundary(text, anchor)
        cursor = if (forward) GraphemeClusters.ceilBoundary(text, focus) else GraphemeClusters.floorBoundary(text, focus)
        preferredColumn = null
    }

    /** Selects the full current value, as a Ctrl/Cmd+A adapter needs. */
    fun selectAll() = select(0, text.length)

    /** Selects the word or token at [index], committing any IME composition first. */
    internal fun selectWordAt(index: Int) {
        commitComposition()
        select(WordBoundaries.wordStart(text, index), WordBoundaries.wordEnd(text, index))
    }

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

    /**
     * Finalizes [committed] over the range captured when composition began, as much of it as
     * [maxLength] allows. The pre-edit itself is shown whole while it is being composed.
     */
    fun commitComposition(committed: String = compositionText) {
        if (!hasComposition) {
            if (committed.isNotEmpty()) insert(committed)
            return
        }
        val start = compositionRangeStart
        val end = compositionRangeEnd
        clearComposition()
        replace(start, end, committed)
    }

    /** Drops the in-progress IME pre-edit text without committing it into [text]. */
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
        cursor = GraphemeClusters.floorBoundary(text, target)
        selectionAnchor = cursor
    }

    private fun lineStart(index: Int): Int =
        if (index <= 0) 0 else text.lastIndexOf('\n', index - 1) + 1

    private fun lineEnd(index: Int): Int = text.indexOf('\n', index).let { if (it < 0) text.length else it }

    override fun toString(): String = "TextFieldState(\"$text\", cursor=$cursor)"
}

context(_: Composer)
/** A [TextFieldState] that survives the next pass. [initial] is its text only on the pass that creates it. */
fun rememberTextFieldState(initial: String = ""): TextFieldState =
    remember { TextFieldState(initial) }
