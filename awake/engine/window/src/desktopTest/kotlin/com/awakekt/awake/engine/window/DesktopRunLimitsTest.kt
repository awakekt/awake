/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.window

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DesktopRunLimitsTest {
    @Test
    fun noPropertiesMeanAnUnlimitedRun() {
        assertEquals(DesktopRunLimits(), DesktopRunLimits.fromSystemProperties { null })
    }

    @Test
    fun theFramesAndCaptureComeFromTheirProperties() {
        val properties = mapOf("awake.frames" to " 120 ", "awake.capture" to "build/frame.png")

        assertEquals(DesktopRunLimits(frames = 120, capture = "build/frame.png"), DesktopRunLimits.fromSystemProperties(properties::get))
    }

    @Test
    fun aBlankCaptureCapturesNothing() {
        assertEquals(DesktopRunLimits(), DesktopRunLimits.fromSystemProperties(mapOf("awake.capture" to " ")::get))
    }

    @Test
    fun framesThatAreNotAPositiveNumberAreRefusedByName() {
        for (value in listOf("0", "-3", "many")) {
            val error = assertFailsWith<IllegalArgumentException> { DesktopRunLimits.fromSystemProperties(mapOf("awake.frames" to value)::get) }
            assertTrue("awake.frames" in error.message.orEmpty() && value in error.message.orEmpty(), "got ${error.message}")
        }
    }
}
