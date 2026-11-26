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

class DeviceProfileTest {
    @Test
    fun defaultProfileLeavesUnknownSignalsUnset() {
        val profile = DeviceProfile()
        assertNull(profile.totalMemoryBytes)
        assertNull(profile.displayRefreshHz)
        assertEquals(ThermalState.Unknown, profile.thermalState)
        assertNull(profile.batteryLevel)
        assertFalse(profile.lowPowerMode)
        assertFalse(profile.isTouchPrimary)
        assertFalse(profile.isLowRamDevice)
        assertNull(profile.availableMemoryBytes)
        assertFalse(profile.isLowMemory)
        assertNull(profile.socModel)
    }

    @Test
    fun sanitizeSocModelNormalizesBlankAndUnknownValues() {
        assertNull(sanitizeSocModel(null))
        assertNull(sanitizeSocModel(""))
        assertNull(sanitizeSocModel("   "))
        assertNull(sanitizeSocModel("unknown"))
        assertNull(sanitizeSocModel("UNKNOWN"))
        assertEquals("MT6768", sanitizeSocModel(" MT6768 "))
    }

    @Test
    fun readDeviceProfileReturnsValidSnapshot() {
        val profile = readDeviceProfile()
        profile.totalMemoryBytes?.let { assertTrue(it > 0L) }
        profile.displayRefreshHz?.let { assertTrue(it > 0) }
        profile.batteryLevel?.let { assertTrue(it in 0f..1f) }
    }

    @Test
    fun androidThermalStatusMappingCoversAllLevels() {
        assertEquals(ThermalState.Nominal, mapAndroidThermalStatus(ANDROID_THERMAL_STATUS_NONE))
        assertEquals(ThermalState.Fair, mapAndroidThermalStatus(ANDROID_THERMAL_STATUS_LIGHT))
        assertEquals(ThermalState.Fair, mapAndroidThermalStatus(ANDROID_THERMAL_STATUS_MODERATE))
        assertEquals(ThermalState.Serious, mapAndroidThermalStatus(ANDROID_THERMAL_STATUS_SEVERE))
        assertEquals(ThermalState.Critical, mapAndroidThermalStatus(ANDROID_THERMAL_STATUS_CRITICAL))
        assertEquals(ThermalState.Critical, mapAndroidThermalStatus(5))
        assertEquals(ThermalState.Critical, mapAndroidThermalStatus(6))
        assertEquals(ThermalState.Unknown, mapAndroidThermalStatus(-1))
    }
}
