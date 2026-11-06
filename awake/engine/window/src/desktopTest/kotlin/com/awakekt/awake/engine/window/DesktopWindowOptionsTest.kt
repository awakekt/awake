/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopWindowOptionsTest {
    @Test
    fun theDefaultsOpenTheWindowAsTheOverloadWithoutOptionsDoes() {
        assertEquals(WindowDecoration.System, DesktopWindowOptions().decoration)
    }

    @Test
    fun theBuilderAppliesItsChangesToTheDefaults() {
        assertEquals(WindowDecoration.None, desktopWindowOptions { decoration = WindowDecoration.None }.decoration)
    }
}
