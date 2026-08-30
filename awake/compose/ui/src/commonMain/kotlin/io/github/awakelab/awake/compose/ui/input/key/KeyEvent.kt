/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.input.key

import io.github.awakelab.awake.core.input.Key

// `Key` is `core.input.Key`, shared with gameplay rather than redeclared here. A host maps its
// platform's keycodes once; two enums would mean two mapping tables per host for one keyboard.

/** Down and up, kept apart so a handler can act on release -- or on hold. */
enum class KeyEventType { Down, Up }

/**
 * One key transition, plus the modifier state at the moment it happened.
 *
 * Separate from `FrameInput.typedText`, which reports the *characters* a keyboard produced. The two
 * answer different questions and folding them loses both: "S went down while Ctrl was held" produces
 * no character at all, and "€" arrives as a character with no single key behind it.
 *
 * Modifiers are captured on the event rather than read from a live keyboard state, because a frame
 * is a snapshot -- asking later would answer about a keyboard that has since moved on.
 */
class KeyEvent(
    val key: Key,
    val type: KeyEventType = KeyEventType.Down,
    val isCtrlPressed: Boolean = false,
    val isShiftPressed: Boolean = false,
    val isAltPressed: Boolean = false,
    val isMetaPressed: Boolean = false,
) {
    /**
     * True once a handler has taken this key, so later handlers can stand down.
     *
     * A flag rather than a return value, for the same reason `PointerEvent` uses one: a later pass
     * has to be able to see what an earlier one took, which a bool return cannot express.
     */
    var isConsumed: Boolean = false
        private set

    fun consume() {
        isConsumed = true
    }

    override fun toString(): String = buildString {
        if (isCtrlPressed) append("Ctrl+")
        if (isAltPressed) append("Alt+")
        if (isShiftPressed) append("Shift+")
        if (isMetaPressed) append("Meta+")
        append(key.name)
        append(if (type == KeyEventType.Down) "↓" else "↑")
    }
}

/**
 * Which direction the focus path is being walked.
 *
 * The same two-direction shape pointer input uses, and for the same reason: an ancestor has to be
 * able to take a key before a descendant sees it. A dialog swallowing Escape before the text field
 * inside it treats it as "clear the selection" is exactly that case.
 */
enum class KeyEventPass {
    /** Root to focused node. An ancestor claims first. */
    Preview,

    /** Focused node to root. Normal handling. */
    Main,
}
