/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import android.content.ComponentCallbacks2
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Host JVM unit tests for Android memory-pressure trim level mapping and dispatch. */
class AndroidMemoryPressureTest {
    /** Resets [MemoryPressure] state before each test. */
    @BeforeTest
    fun setUp() {
        MemoryPressure.resetForTesting()
    }

    /** Resets [MemoryPressure] state after each test. */
    @AfterTest
    fun tearDown() {
        MemoryPressure.resetForTesting()
    }

    /** Verifies [androidMemoryPressureLevel] matches every Android `ComponentCallbacks2` constant. */
    @Suppress("DEPRECATION")
    @Test
    fun androidMemoryPressureLevelMatchesSdkConstants() {
        assertEquals(
            MemoryPressureLevel.Moderate,
            androidMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE),
        )
        assertEquals(
            MemoryPressureLevel.Low,
            androidMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW),
        )
        assertEquals(
            MemoryPressureLevel.Low,
            androidMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN),
        )
        assertEquals(
            MemoryPressureLevel.Critical,
            androidMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL),
        )
        assertEquals(
            MemoryPressureLevel.Critical,
            androidMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND),
        )
        assertEquals(
            MemoryPressureLevel.Critical,
            androidMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_MODERATE),
        )
        assertEquals(
            MemoryPressureLevel.Critical,
            androidMemoryPressureLevel(ComponentCallbacks2.TRIM_MEMORY_COMPLETE),
        )
        assertNull(androidMemoryPressureLevel(0))
    }

    /** Verifies [dispatchAndroidMemoryTrim] notifies subscribers only for recognized trim levels. */
    @Test
    fun dispatchAndroidMemoryTrimNotifiesOnlyForRecognizedLevels() {
        val received = mutableListOf<MemoryPressureLevel>()
        val handle = MemoryPressure.subscribe { received += it }
        try {
            assertNull(dispatchAndroidMemoryTrim(0))
            assertEquals(0, MemoryPressure.version)
            assertEquals(emptyList(), received)

            assertEquals(
                MemoryPressureLevel.Low,
                dispatchAndroidMemoryTrim(ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN),
            )
            assertEquals(1, MemoryPressure.version)
            assertEquals(listOf(MemoryPressureLevel.Low), received)

            assertEquals(
                MemoryPressureLevel.Critical,
                dispatchAndroidMemoryTrim(ComponentCallbacks2.TRIM_MEMORY_BACKGROUND),
            )
            assertEquals(2, MemoryPressure.version)
            assertEquals(listOf(MemoryPressureLevel.Low, MemoryPressureLevel.Critical), received)
        } finally {
            handle.close()
        }
    }
}
