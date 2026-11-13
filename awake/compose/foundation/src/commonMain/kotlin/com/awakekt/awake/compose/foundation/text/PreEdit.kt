/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.foundation.text

/**
 * The IME pre-edit of one [TextFieldState]: [text] shown in place of `text[start until end]` of the
 * committed text, with its own selection ([selectionStart] to [selectionEnd]) inside [text].
 *
 * Active only while there is pre-edit text or a replaced range. Kept by the state once created and
 * cleared, not dropped, so a second composition allocates nothing.
 */
internal class PreEdit {
    var start = 0
    var end = 0
    var text = ""
    var selectionStart = 0
    var selectionEnd = 0

    val isActive: Boolean get() = text.isNotEmpty() || start != end

    fun clear() {
        start = 0
        end = 0
        text = ""
        selectionStart = 0
        selectionEnd = 0
    }
}
