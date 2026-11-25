/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Coarse thermal-throttling state reported by the host operating system.
 */
enum class ThermalState {
    /** Thermal state is unavailable on this host or could not be queried. */
    Unknown,

    /** Device temperature is within normal operating limits. */
    Nominal,

    /** Device is mildly elevated; background or non-essential work may begin scaling back. */
    Fair,

    /** Device is hot and likely throttling CPU/GPU clocks; workloads should reduce fidelity. */
    Serious,

    /** Device is at a critical thermal limit and requires immediate workload reduction. */
    Critical,
}

/**
 * Snapshot of hardware and operating-environment characteristics reported by the host platform.
 *
 * Fields that a target cannot observe synchronously are left `null` (or [ThermalState.Unknown] /
 * `false`) rather than fabricated.
 *
 * @property totalMemoryBytes Total physical device RAM in bytes, or `null` when unavailable.
 * @property displayRefreshHz Primary display refresh rate in hertz, or `null` when unavailable.
 * @property thermalState Current coarse thermal state reported by the OS.
 * @property batteryLevel Battery charge fraction in `0f..1f`, or `null` when unavailable.
 * @property lowPowerMode Whether the OS is currently in low-power or battery-saver mode.
 * @property isTouchPrimary Whether touch is the device's primary pointer input mechanism.
 */
data class DeviceProfile(
    val totalMemoryBytes: Long? = null,
    val displayRefreshHz: Int? = null,
    val thermalState: ThermalState = ThermalState.Unknown,
    val batteryLevel: Float? = null,
    val lowPowerMode: Boolean = false,
    val isTouchPrimary: Boolean = false,
)

/**
 * Reads a best-effort [DeviceProfile] snapshot from the current host platform.
 *
 * @return A [DeviceProfile] containing the host signals available synchronously on this target.
 */
expect fun readDeviceProfile(): DeviceProfile
