/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import platform.Foundation.NSProcessInfo
import platform.Foundation.NSProcessInfoThermalState
import platform.Foundation.lowPowerModeEnabled
import platform.Foundation.thermalState
import platform.UIKit.UIDevice
import platform.UIKit.UIScreen

/**
 * Reads an iOS [DeviceProfile] from `NSProcessInfo`, `UIScreen.mainScreen`, and
 * `UIDevice.currentDevice`.
 *
 * `batteryLevel` is populated only when battery monitoring is already enabled on `UIDevice`
 * (`batteryLevel in 0f..1f`), avoiding unintended mutation of global device state.
 *
 * @return iOS [DeviceProfile] snapshot.
 */
actual fun readDeviceProfile(): DeviceProfile {
    val processInfo = NSProcessInfo.processInfo
    val totalMemoryBytes = processInfo.physicalMemory.toLong().takeIf { it > 0L }
    val displayRefreshHz = UIScreen.mainScreen.maximumFramesPerSecond.toInt().takeIf { it > 0 }
    val thermalState = when (processInfo.thermalState) {
        NSProcessInfoThermalState.NSProcessInfoThermalStateNominal -> ThermalState.Nominal
        NSProcessInfoThermalState.NSProcessInfoThermalStateFair -> ThermalState.Fair
        NSProcessInfoThermalState.NSProcessInfoThermalStateSerious -> ThermalState.Serious
        NSProcessInfoThermalState.NSProcessInfoThermalStateCritical -> ThermalState.Critical
        else -> ThermalState.Unknown
    }
    val batteryLevel = UIDevice.currentDevice.batteryLevel.takeIf { it in 0f..1f }
    val lowPowerMode = processInfo.lowPowerModeEnabled

    return DeviceProfile(
        totalMemoryBytes = totalMemoryBytes,
        displayRefreshHz = displayRefreshHz,
        thermalState = thermalState,
        batteryLevel = batteryLevel,
        lowPowerMode = lowPowerMode,
        isTouchPrimary = true,
    )
}
