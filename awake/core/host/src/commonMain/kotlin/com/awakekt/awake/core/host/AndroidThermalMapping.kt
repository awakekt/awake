/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

internal const val ANDROID_THERMAL_STATUS_NONE: Int = 0
internal const val ANDROID_THERMAL_STATUS_LIGHT: Int = 1
internal const val ANDROID_THERMAL_STATUS_MODERATE: Int = 2
internal const val ANDROID_THERMAL_STATUS_SEVERE: Int = 3
internal const val ANDROID_THERMAL_STATUS_CRITICAL: Int = 4

internal fun mapAndroidThermalStatus(status: Int): ThermalState = when {
    status == ANDROID_THERMAL_STATUS_NONE -> ThermalState.Nominal
    status == ANDROID_THERMAL_STATUS_LIGHT ||
        status == ANDROID_THERMAL_STATUS_MODERATE -> ThermalState.Fair
    status == ANDROID_THERMAL_STATUS_SEVERE -> ThermalState.Serious
    status >= ANDROID_THERMAL_STATUS_CRITICAL -> ThermalState.Critical
    else -> ThermalState.Unknown
}
