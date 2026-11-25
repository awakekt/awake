/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Maps an Android `ComponentCallbacks2.TRIM_MEMORY_*` [trimLevel] to the corresponding
 * [MemoryPressureLevel], or `null` when [trimLevel] does not match a known trim signal.
 *
 * Mapping:
 * - `TRIM_MEMORY_RUNNING_MODERATE` (5) -> [MemoryPressureLevel.Moderate]
 * - `TRIM_MEMORY_RUNNING_LOW` (10) / `TRIM_MEMORY_UI_HIDDEN` (20) -> [MemoryPressureLevel.Low]
 * - `TRIM_MEMORY_RUNNING_CRITICAL` (15) / `TRIM_MEMORY_BACKGROUND` (40) and above ->
 *   [MemoryPressureLevel.Critical]
 *
 * @param trimLevel Android `ComponentCallbacks2.onTrimMemory` level constant.
 * @return The corresponding [MemoryPressureLevel], or `null` if unrecognized.
 */
fun androidMemoryPressureLevel(trimLevel: Int): MemoryPressureLevel? =
    mapAndroidTrimLevel(trimLevel)

/**
 * Maps an Android `ComponentCallbacks2.TRIM_MEMORY_*` [trimLevel] with
 * [androidMemoryPressureLevel] and, when recognized, dispatches [MemoryPressure.trim]
 * synchronously.
 *
 * @param trimLevel Android `ComponentCallbacks2.onTrimMemory` level constant.
 * @return The dispatched [MemoryPressureLevel], or `null` when [trimLevel] was ignored.
 */
fun dispatchAndroidMemoryTrim(trimLevel: Int): MemoryPressureLevel? =
    androidMemoryPressureLevel(trimLevel)?.also { MemoryPressure.trim(it) }
