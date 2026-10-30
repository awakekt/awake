/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The launch environment as a value, and the web's mapping from `(pointer: coarse)` to a primary input. */
class LaunchEnvironmentTest {
    @Test
    fun aCoarsePrimaryPointerIsTouch() {
        assertEquals(PrimaryInput.Touch, primaryInputFromCoarsePointer(coarse = true))
        assertEquals(PrimaryInput.Pointer, primaryInputFromCoarsePointer(coarse = false))
    }

    @Test
    fun environmentsAreValues() {
        val origin = LaunchOrigin.fromUrl("https://game.example")

        assertEquals(LaunchEnvironment(origin, PrimaryInput.Touch), LaunchEnvironment(origin, PrimaryInput.Touch))
        assertEquals(
            LaunchEnvironment(origin, PrimaryInput.Touch).hashCode(),
            LaunchEnvironment(origin, PrimaryInput.Touch).hashCode(),
        )
        assertNotEquals(LaunchEnvironment(origin, PrimaryInput.Touch), LaunchEnvironment(null, PrimaryInput.Touch))
        assertNotEquals(LaunchEnvironment(null, PrimaryInput.Touch), LaunchEnvironment(null, PrimaryInput.Pointer))
        assertEquals(
            "LaunchEnvironment(origin=https://game.example, primaryInput=Touch)",
            LaunchEnvironment(origin, PrimaryInput.Touch).toString(),
        )
    }
}
