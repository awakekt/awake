/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("DeviceProfileKt")

package com.awakekt.awake.core.host

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.view.Display
import kotlin.concurrent.Volatile
import kotlin.math.roundToInt

private const val BATTERY_PERCENT_SCALE = 100f

@Volatile
private var boundAndroidDeviceContext: Context? = null

/**
 * Registers an Android [Context] for parameterless [readDeviceProfile] lookups.
 *
 * Stores the application context (when available) so retaining an [android.app.Activity] instance
 * is avoided.
 *
 * @param context Android application or activity context.
 */
fun bindAndroidDeviceContext(context: Context) {
    boundAndroidDeviceContext = context.applicationContext ?: context
}

internal fun resetAndroidDeviceContextForTesting() {
    boundAndroidDeviceContext = null
}

/**
 * Reads an Android [DeviceProfile] using the context bound via [bindAndroidDeviceContext] (or
 * [AndroidFrameLoop.pacingActivity] when attached), falling back to a touch-primary default when
 * no context has been bound yet.
 *
 * @return Android [DeviceProfile] snapshot.
 */
actual fun readDeviceProfile(): DeviceProfile {
    val context = boundAndroidDeviceContext ?: AndroidFrameLoop.pacingActivity?.get()
    return if (context != null) {
        androidDeviceProfile(context)
    } else {
        DeviceProfile(isTouchPrimary = true)
    }
}

/**
 * Reads an Android [DeviceProfile] directly from [context], querying [ActivityManager],
 * [DisplayManager], [PowerManager], [BatteryManager], and [PackageManager].
 *
 * Each system-service read is guarded so restricted or stubbed test contexts fall back cleanly to
 * unknown/null values rather than throwing.
 *
 * @param context Android context used to query system services.
 * @return Android [DeviceProfile] snapshot.
 */
fun androidDeviceProfile(context: Context): DeviceProfile {
    val appContext = context.applicationContext ?: context
    val activityManager = runCatching {
        appContext.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }.getOrNull()
    val memoryInfo = activityManager?.let { am ->
        runCatching {
            ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        }.getOrNull()
    }
    val totalMemoryBytes = memoryInfo?.totalMem?.takeIf { it > 0L }
    val availableMemoryBytes = memoryInfo?.availMem?.takeIf { it >= 0L }
    val isLowMemory = memoryInfo?.lowMemory == true
    val isLowRamDevice = runCatching { activityManager?.isLowRamDevice == true }.getOrDefault(false)
    val socModel = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) sanitizeSocModel(Build.SOC_MODEL) else null
    }.getOrNull()

    val displayRefreshHz = runCatching {
        val manager = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val rate = manager?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate
        rate?.takeIf { it.isFinite() && it > 0f }?.roundToInt()
    }.getOrNull()

    val powerManager = runCatching {
        appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
    }.getOrNull()

    val thermalState = runCatching {
        powerManager?.currentThermalStatus?.let(::mapAndroidThermalStatus)
    }.getOrNull() ?: ThermalState.Unknown

    val lowPowerMode = runCatching {
        powerManager?.isPowerSaveMode == true
    }.getOrDefault(false)

    val batteryLevel = runCatching {
        val manager = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val capacity = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        capacity?.takeIf { it in 0..100 }?.let { (it / BATTERY_PERCENT_SCALE).coerceIn(0f, 1f) }
    }.getOrNull()

    val isTouchPrimary = runCatching {
        appContext.packageManager?.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }.getOrNull() ?: true

    return DeviceProfile(
        totalMemoryBytes = totalMemoryBytes,
        displayRefreshHz = displayRefreshHz,
        thermalState = thermalState,
        batteryLevel = batteryLevel,
        lowPowerMode = lowPowerMode,
        isTouchPrimary = isTouchPrimary,
        isLowRamDevice = isLowRamDevice,
        availableMemoryBytes = availableMemoryBytes,
        isLowMemory = isLowMemory,
        socModel = socModel,
    )
}
