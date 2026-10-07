/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import com.awakekt.awake.core.input.ClipboardCommand
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.TextEditAction

/**
 * An edit action a platform's own text system sends to whatever has keyboard focus: from its edit
 * menu, or Cmd+C, Cmd+X, Cmd+V and Cmd+A on a hardware keyboard.
 */
internal enum class StandardEditAction { Copy, Cut, Paste, SelectAll }

/** The [StandardEditAction] an Objective-C selector name such as `"copy:"` stands for, or null for any other. */
internal fun standardEditAction(selectorName: String?): StandardEditAction? = when (selectorName) {
    "copy:" -> StandardEditAction.Copy
    "cut:" -> StandardEditAction.Cut
    "paste:" -> StandardEditAction.Paste
    "selectAll:" -> StandardEditAction.SelectAll
    else -> null
}

/**
 * Delivers [action] to the focused Awake text field and returns true, or returns false when none has
 * focus, so the platform keeps its own handling.
 *
 * Copy and cut are requests: the UI answers through [Input.clipboardWrite], and a password field
 * answers nothing. Paste types [pasteboardText] through [Input.pushTypedText] when it is not empty,
 * so pasted text follows the same focus and single-line rules as typed text.
 */
internal fun Input.applyStandardEditAction(action: StandardEditAction, pasteboardText: () -> String?): Boolean {
    if (!textInputFocused) return false
    when (action) {
        StandardEditAction.Copy -> pushClipboardCommand(ClipboardCommand.Copy)
        StandardEditAction.Cut -> pushClipboardCommand(ClipboardCommand.Cut)
        StandardEditAction.Paste -> pasteboardText()?.takeIf { it.isNotEmpty() }?.let { pushTypedText(it) }
        StandardEditAction.SelectAll -> pushEditAction(TextEditAction.SelectAll)
    }
    return true
}

/** Hands the UI's answer to a copy or cut to [write], once. An empty answer writes nothing. */
internal fun Input.syncClipboardWrite(write: (String) -> Unit) {
    takeClipboardWrite()?.takeIf { it.isNotEmpty() }?.let(write)
}
