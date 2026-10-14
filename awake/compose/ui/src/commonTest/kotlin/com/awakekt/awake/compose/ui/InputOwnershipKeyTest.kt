/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.compose.ui.platform.blocksGameplayKey
import com.awakekt.awake.compose.ui.platform.blocksGameplayKeys
import com.awakekt.awake.core.input.Key
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [InputOwnership.blocksGameplayKey] is the per-key refinement of [InputOwnership.blocksGameplayKeys]:
 * a focused field now takes only the keys it uses, rather than every key.
 */
class InputOwnershipKeyTest {
    @Test
    fun idleOwnershipBlocksNoKey() {
        assertFalse(InputOwnership().blocksGameplayKey(Key.W))
        assertFalse(InputOwnership().blocksGameplayKey(Key.ArrowUp))
    }

    @Test
    fun aSingleLineFocusedFieldTakesTypingAndEditingButNotArrowUpDown() {
        val ownership = InputOwnership(isTextInputFocused = true, isTextInputSingleLine = true)

        assertTrue(ownership.blocksGameplayKey(Key.W), "a letter is typed text")
        assertTrue(ownership.blocksGameplayKey(Key.Space), "space is typed text")
        assertTrue(ownership.blocksGameplayKey(Key.Backspace))
        assertTrue(ownership.blocksGameplayKey(Key.Delete))
        assertTrue(ownership.blocksGameplayKey(Key.ArrowLeft))
        assertTrue(ownership.blocksGameplayKey(Key.ArrowRight))
        assertTrue(ownership.blocksGameplayKey(Key.Home))
        assertTrue(ownership.blocksGameplayKey(Key.End))
        assertTrue(ownership.blocksGameplayKey(Key.Enter))
        assertTrue(ownership.blocksGameplayKey(Key.Escape))
        assertTrue(ownership.blocksGameplayKey(Key.Tab))

        assertFalse(ownership.blocksGameplayKey(Key.ArrowUp), "a single-line field has no use for Up")
        assertFalse(ownership.blocksGameplayKey(Key.ArrowDown), "a single-line field has no use for Down")
        assertFalse(ownership.blocksGameplayKey(Key.F1), "a field never handles function keys")
        assertFalse(ownership.blocksGameplayKey(Key.PageUp), "no field handles PageUp today")
    }

    @Test
    fun aMultilineFocusedFieldAlsoTakesArrowUpDown() {
        val ownership = InputOwnership(isTextInputFocused = true, isTextInputSingleLine = false)

        assertTrue(ownership.blocksGameplayKey(Key.ArrowUp), "a multi-line field moves its caret with Up")
        assertTrue(ownership.blocksGameplayKey(Key.ArrowDown), "a multi-line field moves its caret with Down")
        // PageUp/PageDown stay free either way: no TextEditAction maps them, and the fly camera
        // already reads them -- see textInputUsedKeys's doc.
        assertFalse(ownership.blocksGameplayKey(Key.PageUp))
        assertFalse(ownership.blocksGameplayKey(Key.PageDown))
    }

    @Test
    fun aDeclaredPassThroughKeyIsLetThroughEvenWhenOtherwiseTaken() {
        val ownership = InputOwnership(
            isTextInputFocused = true,
            isTextInputSingleLine = true,
            textInputPassThroughKeys = setOf(Key.Escape),
        )

        assertFalse(ownership.blocksGameplayKey(Key.Escape), "the field declared it does not want Escape")
        assertTrue(ownership.blocksGameplayKey(Key.W), "typing is still taken")
    }

    @Test
    fun captureAndModalStillBlockEveryKeyRegardlessOfTextInput() {
        assertTrue(InputOwnership(isCaptured = true).blocksGameplayKey(Key.ArrowUp))
        assertTrue(InputOwnership(isCaptured = true).blocksGameplayKey(Key.F1))
        assertTrue(InputOwnership(isModalOpen = true).blocksGameplayKey(Key.ArrowUp))
        assertTrue(InputOwnership(isModalOpen = true).blocksGameplayKey(Key.F1))
    }
}
