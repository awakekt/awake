/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.runtime

import com.awakekt.awake.compose.ui.input.pointer.PointerModifiers
import com.awakekt.awake.core.input.Input
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SceneFrameInputTest {

    @Test
    fun sceneRuntimePreservesSecondaryPressAndPointerModifiers() {
        val input = Input()
        input.setKeyDown(Key.Shift, true)
        input.setSecondaryPointer(true)
        val snapshot = input.updateSnapshot()

        val frame = snapshot.toFrameInput(800, 600, 1f / 60f)

        assertTrue(frame.secondaryPointerPressed, "the scene adapter must forward a right-click edge")
        assertEquals(
            PointerModifiers(isCtrlPressed = false, isShiftPressed = true, isAltPressed = false, isMetaPressed = false),
            frame.pointerModifiers,
            "pointer modifiers must survive the scene adapter for shift-click consumers",
        )
        assertTrue(
            PointerButton.Secondary in snapshot.buttonsPressed,
            "the input snapshot should contain the source secondary edge",
        )
    }
}
