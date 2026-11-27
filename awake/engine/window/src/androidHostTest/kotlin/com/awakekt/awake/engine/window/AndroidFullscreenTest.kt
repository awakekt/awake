/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies Android's system-bar visibility check ([areSystemBarsHidden]): both status and
 * navigation bars must be hidden to treat fullscreen as active, independent of `captionBar()`.
 */
internal class AndroidFullscreenTest {
    @Test
    fun bothBarsHiddenReadsAsFullscreen() {
        assertTrue(areSystemBarsHidden(statusBarVisible = false, navigationBarVisible = false))
    }

    @Test
    fun eitherVisibleBarReadsAsNotFullscreen() {
        assertFalse(areSystemBarsHidden(statusBarVisible = true, navigationBarVisible = true))
        assertFalse(areSystemBarsHidden(statusBarVisible = true, navigationBarVisible = false))
        assertFalse(areSystemBarsHidden(statusBarVisible = false, navigationBarVisible = true))
    }
}
