// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.text.input

/**
 * A keyboard action that edits or moves within text, separated from the characters themselves.
 *
 * Typed characters arrive as a string and these as commands, because a platform reports them
 * separately: a keyboard sends "a" as text and Backspace as a key, and folding the two loses the
 * difference between typing "\b" and pressing the key.
 *
 * Deliberately small. Selection, word-wise motion and clipboard are absent rather than present and
 * inert -- an enum entry that silently does nothing is the failure this repo keeps hitting.
 */
enum class EditCommand {
    Backspace,
    Delete,
    MoveLeft,
    MoveRight,
    MoveHome,
    MoveEnd,
}
