/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:JvmName("DeviceProfileKt")

package com.awakekt.awake.core.host

import java.lang.management.ManagementFactory

/**
 * Reads a desktop JVM [DeviceProfile], querying total physical memory via the platform
 * `OperatingSystemMXBean` when available.
 *
 * Display refresh rate is intentionally left `null` so reading the profile never initializes AWT on
 * headless hosts or macOS `-XstartOnFirstThread` render threads.
 *
 * @return Desktop [DeviceProfile] snapshot.
 */
actual fun readDeviceProfile(): DeviceProfile = DeviceProfile(
    totalMemoryBytes = readDesktopPhysicalMemoryBytes(),
    isTouchPrimary = false,
)

private fun readDesktopPhysicalMemoryBytes(): Long? = runCatching {
    val bean = ManagementFactory.getOperatingSystemMXBean()
    (bean as? com.sun.management.OperatingSystemMXBean)?.totalMemorySize?.takeIf { it > 0L }
}.getOrNull()
