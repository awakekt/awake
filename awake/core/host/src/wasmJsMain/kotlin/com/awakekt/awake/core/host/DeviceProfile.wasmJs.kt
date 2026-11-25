/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.host

private const val BYTES_PER_MEGABYTE: Long = 1024L * 1024L

/**
 * Reads a browser [DeviceProfile] using `navigator.deviceMemory` and `matchMedia('(pointer: coarse)')`,
 * leaving unavailable signals as `null` / [ThermalState.Unknown] / `false`.
 *
 * @return Browser [DeviceProfile] snapshot.
 */
actual fun readDeviceProfile(): DeviceProfile = DeviceProfile(
    totalMemoryBytes = browserDeviceMemoryMegabytes()?.let { it * BYTES_PER_MEGABYTE },
    isTouchPrimary = hasCoarsePrimaryPointer(),
)

private fun hasCoarsePrimaryPointer(): Boolean = js(
    "(() => { try { return typeof window !== 'undefined' && typeof window.matchMedia === 'function' && Boolean(window.matchMedia('(pointer: coarse)').matches); } catch (_) { return false; } })()",
)
