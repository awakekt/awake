/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.input

import android.view.inputmethod.EditorInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/** The keyboard keeps the app visible: no full-screen extract editor unless the host asks for one (#1770). */
internal class AndroidImeOptionsTest {
    @Test
    fun byDefaultTheKeyboardOpensNoExtractEditorAndKeepsItsDoneAction() {
        val options = awakeImeOptions(extractEditor = Input().textInputExtractEditor)

        assertEquals(EditorInfo.IME_FLAG_NO_EXTRACT_UI, options and EditorInfo.IME_FLAG_NO_EXTRACT_UI, "no extract editor")
        assertEquals(EditorInfo.IME_ACTION_DONE, options and EditorInfo.IME_MASK_ACTION, "the Done action")
    }

    @Test
    fun aHostThatAsksForTheExtractEditorGetsIt() {
        val options = awakeImeOptions(extractEditor = true)

        assertFalse(options and EditorInfo.IME_FLAG_NO_EXTRACT_UI != 0, "the extract editor is allowed")
        assertEquals(EditorInfo.IME_ACTION_DONE, options and EditorInfo.IME_MASK_ACTION, "the Done action")
    }
}
