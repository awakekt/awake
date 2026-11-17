/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.camera

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraOrbitButtonsTest {
    @Test
    fun primaryOrSecondaryAllowsEitherButton() {
        val buttons = CameraOrbitButtons.PrimaryOrSecondary
        assertTrue(buttons.allows(primaryDown = true, secondaryDown = false, touch = false))
        assertTrue(buttons.allows(primaryDown = false, secondaryDown = true, touch = false))
        assertTrue(buttons.allows(primaryDown = true, secondaryDown = false, touch = true))
        assertFalse(buttons.allows(primaryDown = false, secondaryDown = false, touch = false))
    }

    @Test
    fun secondaryOnlyLeavesAPrimaryMouseDragToTheGameButNotAFinger() {
        val buttons = CameraOrbitButtons.SecondaryOnly
        assertFalse(buttons.allows(primaryDown = true, secondaryDown = false, touch = false))
        assertTrue(buttons.allows(primaryDown = false, secondaryDown = true, touch = false))
        assertTrue(buttons.allows(primaryDown = true, secondaryDown = false, touch = true))
        assertFalse(buttons.allows(primaryDown = false, secondaryDown = false, touch = true))
    }
}
