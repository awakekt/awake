/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopMemoryAndProfileTest {
    @Test
    fun runtimeHeapQueriesReturnConsistentBounds() {
        val used = runtimeHeapUsedBytes()
        val limit = runtimeHeapLimitBytes()

        assertTrue(used >= 0L, "Expected non-negative heap used, got $used")
        assertTrue(limit > 0L, "Expected positive heap limit, got $limit")
        assertTrue(used <= limit, "Expected used ($used) <= limit ($limit)")
    }

    @Test
    fun desktopDeviceProfileReportsNonTouchAndPhysicalMemory() {
        val profile = readDeviceProfile()

        assertFalse(profile.isTouchPrimary)
        assertNull(profile.displayRefreshHz)
        assertEquals(ThermalState.Unknown, profile.thermalState)
        assertNull(profile.batteryLevel)
        assertFalse(profile.lowPowerMode)
        profile.totalMemoryBytes?.let { assertTrue(it > 0L) }
    }
}
