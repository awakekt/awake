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
 * @property isLowRamDevice Whether the OS classifies this device as low-RAM (e.g. Android Go).
 * @property availableMemoryBytes Currently available physical device RAM in bytes, or `null` when unavailable.
 * @property isLowMemory Whether the OS currently reports a system-wide low-memory condition.
 * @property socModel System-on-chip identifier reported by the OS (e.g. Android `Build.SOC_MODEL`), or `null`.
 */
@Suppress("LongParameterList") // Flat host snapshot; adding fields keeps existing defaults binary-compatible.
data class DeviceProfile(
    val totalMemoryBytes: Long? = null,
    val displayRefreshHz: Int? = null,
    val thermalState: ThermalState = ThermalState.Unknown,
    val batteryLevel: Float? = null,
    val lowPowerMode: Boolean = false,
    val isTouchPrimary: Boolean = false,
    val isLowRamDevice: Boolean = false,
    val availableMemoryBytes: Long? = null,
    val isLowMemory: Boolean = false,
    val socModel: String? = null,
) {
    /** Constructor as released before low-RAM, available-memory, low-memory, and SoC fields. */
    @Deprecated(
        "Use the constructor with isLowRamDevice, availableMemoryBytes, isLowMemory, and socModel.",
        level = DeprecationLevel.HIDDEN,
    )
    constructor(
        totalMemoryBytes: Long? = null,
        displayRefreshHz: Int? = null,
        thermalState: ThermalState = ThermalState.Unknown,
        batteryLevel: Float? = null,
        lowPowerMode: Boolean = false,
        isTouchPrimary: Boolean = false,
    ) : this(
        totalMemoryBytes = totalMemoryBytes,
        displayRefreshHz = displayRefreshHz,
        thermalState = thermalState,
        batteryLevel = batteryLevel,
        lowPowerMode = lowPowerMode,
        isTouchPrimary = isTouchPrimary,
        isLowRamDevice = false,
        availableMemoryBytes = null,
        isLowMemory = false,
        socModel = null,
    )

    /** [copy] as released before low-RAM, available-memory, low-memory, and SoC fields. */
    @Deprecated(
        "Use copy with isLowRamDevice, availableMemoryBytes, isLowMemory, and socModel.",
        level = DeprecationLevel.HIDDEN,
    )
    fun copy(
        totalMemoryBytes: Long? = this.totalMemoryBytes,
        displayRefreshHz: Int? = this.displayRefreshHz,
        thermalState: ThermalState = this.thermalState,
        batteryLevel: Float? = this.batteryLevel,
        lowPowerMode: Boolean = this.lowPowerMode,
        isTouchPrimary: Boolean = this.isTouchPrimary,
    ): DeviceProfile = copy(
        totalMemoryBytes = totalMemoryBytes,
        displayRefreshHz = displayRefreshHz,
        thermalState = thermalState,
        batteryLevel = batteryLevel,
        lowPowerMode = lowPowerMode,
        isTouchPrimary = isTouchPrimary,
        isLowRamDevice = this.isLowRamDevice,
        availableMemoryBytes = this.availableMemoryBytes,
        isLowMemory = this.isLowMemory,
        socModel = this.socModel,
    )
}

/**
 * Reads a best-effort [DeviceProfile] snapshot from the current host platform.
 *
 * @return A [DeviceProfile] containing the host signals available synchronously on this target.
 */
expect fun readDeviceProfile(): DeviceProfile
