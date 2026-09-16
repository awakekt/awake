/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.controls

import com.awakekt.awake.compose.ui.platform.InputOwnership
import com.awakekt.awake.core.input.InputSnapshot
import com.awakekt.awake.core.input.Key
import com.awakekt.awake.core.input.PointerButton
import com.awakekt.awake.scene.controls.camera.CameraGesturePolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraGesturePolicyTest {

    @Test
    fun defaultPolicyActivatesOnPrimaryOrSecondaryDrag() {
        val policy = CameraGesturePolicy.Default

        val primaryInput = makeInput(pointerDown = true)
        assertTrue(policy.isOrbitDragging.isDragging(primaryInput))

        val secondaryInput = makeInput(pointerDown = false, buttons = setOf(PointerButton.Secondary))
        assertTrue(policy.isOrbitDragging.isDragging(secondaryInput))

        val idleInput = makeInput(pointerDown = false)
        assertFalse(policy.isOrbitDragging.isDragging(idleInput))
    }

    @Test
    fun customPolicyCanRequireAltModifier() {
        val policy = CameraGesturePolicy(
            isOrbitDragging = { input -> input.pointerDown && input.isDown(Key.Alt) },
        )

        val plainClick = makeInput(pointerDown = true)
        assertFalse(policy.isOrbitDragging.isDragging(plainClick))

        val altClick = makeInput(pointerDown = true, keys = setOf(Key.Alt))
        assertTrue(policy.isOrbitDragging.isDragging(altClick))
    }

    private fun makeInput(
        pointerDown: Boolean,
        buttons: Set<PointerButton> = emptySet(),
        keys: Set<Key> = emptySet(),
    ): GameplayInput {
        val snapshot = InputSnapshot(
            pointerX = 100f,
            pointerY = 100f,
            pointerDown = pointerDown,
            scrollDeltaX = 0f,
            scrollDeltaY = 0f,
            keysDown = keys,
            keysPressed = emptySet(),
            keysReleased = emptySet(),
            typedText = "",
            editActions = emptyList(),
            secondaryPointerDown = PointerButton.Secondary in buttons,
            buttonsDown = buttons,
        )
        return GameplayInput(snapshot, InputOwnership())
    }
}
