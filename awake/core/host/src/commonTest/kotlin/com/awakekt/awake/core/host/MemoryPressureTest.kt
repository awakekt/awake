/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MemoryPressureTest {
    @BeforeTest
    fun setUp() {
        MemoryPressure.resetForTesting()
    }

    @AfterTest
    fun tearDown() {
        MemoryPressure.resetForTesting()
    }

    @Test
    fun trimDefaultsToLowAndIncrementsVersion() {
        val received = mutableListOf<MemoryPressureLevel>()
        val handle = MemoryPressure.subscribe { received += it }
        try {
            assertEquals(0, MemoryPressure.version)

            MemoryPressure.trim()
            assertEquals(1, MemoryPressure.version)

            MemoryPressure.trim(MemoryPressureLevel.Moderate)
            assertEquals(2, MemoryPressure.version)

            MemoryPressure.trim(MemoryPressureLevel.Critical)
            assertEquals(3, MemoryPressure.version)

            assertEquals(
                listOf(
                    MemoryPressureLevel.Low,
                    MemoryPressureLevel.Moderate,
                    MemoryPressureLevel.Critical,
                ),
                received,
            )
        } finally {
            handle.close()
        }
    }

    @Test
    fun unsubscribeHandleIsIdempotentAndIndependentPerSubscription() {
        var calls = 0
        val listener: (MemoryPressureLevel) -> Unit = { calls++ }
        val first = MemoryPressure.subscribe(listener)
        val second = MemoryPressure.subscribe(listener)

        MemoryPressure.trim(MemoryPressureLevel.Low)
        assertEquals(2, calls)

        first.close()
        first.close()

        MemoryPressure.trim(MemoryPressureLevel.Low)
        assertEquals(3, calls)

        second.close()
        MemoryPressure.trim(MemoryPressureLevel.Low)
        assertEquals(3, calls)
    }

    @Test
    fun throwingListenerDoesNotBlockSubsequentListeners() {
        val log = mutableListOf<String>()
        val h1 = MemoryPressure.subscribe { log += "first:$it" }
        val h2 = MemoryPressure.subscribe { error("listener failure") }
        val h3 = MemoryPressure.subscribe { log += "third:$it" }
        try {
            MemoryPressure.trim(MemoryPressureLevel.Critical)
            assertEquals(1, MemoryPressure.version)
            assertEquals(listOf("first:Critical", "third:Critical"), log)
        } finally {
            h1.close()
            h2.close()
            h3.close()
        }
    }

    @Test
    fun trimIsSnapshotSafeWhenListenersMutateSubscriptions() {
        val log = mutableListOf<String>()
        lateinit var selfHandle: AutoCloseable
        var lateHandle: AutoCloseable? = null
        var addedHandle: AutoCloseable? = null

        selfHandle = MemoryPressure.subscribe { level ->
            log += "self:$level"
            selfHandle.close()
            lateHandle?.close()
            addedHandle = MemoryPressure.subscribe { nextLevel -> log += "added:$nextLevel" }
        }
        lateHandle = MemoryPressure.subscribe { level ->
            log += "late:$level"
        }

        try {
            MemoryPressure.trim(MemoryPressureLevel.Moderate)
            assertEquals(listOf("self:Moderate"), log)

            MemoryPressure.trim(MemoryPressureLevel.Low)
            assertEquals(listOf("self:Moderate", "added:Low"), log)
        } finally {
            selfHandle.close()
            lateHandle.close()
            addedHandle?.close()
        }
    }

    @Test
    fun androidTrimLevelMappingMatchesComponentCallbacks2Contract() {
        assertEquals(MemoryPressureLevel.Moderate, mapAndroidTrimLevel(ANDROID_TRIM_MEMORY_RUNNING_MODERATE))
        assertEquals(MemoryPressureLevel.Low, mapAndroidTrimLevel(ANDROID_TRIM_MEMORY_RUNNING_LOW))
        assertEquals(MemoryPressureLevel.Low, mapAndroidTrimLevel(ANDROID_TRIM_MEMORY_UI_HIDDEN))
        assertEquals(MemoryPressureLevel.Critical, mapAndroidTrimLevel(ANDROID_TRIM_MEMORY_RUNNING_CRITICAL))
        assertEquals(MemoryPressureLevel.Critical, mapAndroidTrimLevel(ANDROID_TRIM_MEMORY_BACKGROUND))
        assertEquals(MemoryPressureLevel.Critical, mapAndroidTrimLevel(ANDROID_TRIM_MEMORY_MODERATE))
        assertEquals(MemoryPressureLevel.Critical, mapAndroidTrimLevel(ANDROID_TRIM_MEMORY_COMPLETE))
        assertEquals(MemoryPressureLevel.Critical, mapAndroidTrimLevel(100))

        assertNull(mapAndroidTrimLevel(0))
        assertNull(mapAndroidTrimLevel(-1))
        assertNull(mapAndroidTrimLevel(3))
        assertNull(mapAndroidTrimLevel(12))
        assertNull(mapAndroidTrimLevel(30))
    }

    @Test
    fun watcherDrainsOncePerSignalAndClosesImmediateSubscriptionIdempotently() {
        val immediate = mutableListOf<MemoryPressureLevel>()
        var frameDrains = 0
        val watcher = MemoryPressure.watch { immediate += it }

        assertFalse(watcher.check())
        watcher.drain { frameDrains++ }
        assertEquals(0, frameDrains)

        MemoryPressure.trim(MemoryPressureLevel.Moderate)
        assertEquals(listOf(MemoryPressureLevel.Moderate), immediate)
        watcher.drain { frameDrains++ }
        watcher.drain { frameDrains++ }
        assertEquals(1, frameDrains)

        MemoryPressure.trim(MemoryPressureLevel.Critical)
        assertEquals(listOf(MemoryPressureLevel.Moderate, MemoryPressureLevel.Critical), immediate)
        assertTrue(watcher.check())
        assertFalse(watcher.check())

        watcher.close()
        watcher.close()
        MemoryPressure.trim(MemoryPressureLevel.Low)
        assertEquals(listOf(MemoryPressureLevel.Moderate, MemoryPressureLevel.Critical), immediate)
    }

    @Test
    fun markFrameDrainedOnlyAcknowledgesCapturedVersion() {
        MemoryPressure.trim(MemoryPressureLevel.Low)
        val beforeMidFrameTrim = MemoryPressure.version
        assertFalse(MemoryPressure.hasFrameDrained())

        // A second trim arrives mid-frame: marking the earlier version must not acknowledge the new one.
        MemoryPressure.trim(MemoryPressureLevel.Critical)
        MemoryPressure.markFrameDrained(beforeMidFrameTrim)
        assertEquals(beforeMidFrameTrim, MemoryPressure.drainedVersion)
        assertTrue(MemoryPressure.hasFrameDrained(beforeMidFrameTrim))
        assertFalse(MemoryPressure.hasFrameDrained())

        // Next full frame drains the latest version.
        MemoryPressure.markFrameDrained()
        assertTrue(MemoryPressure.hasFrameDrained())
    }

    @Test
    fun trimAndAwaitDrainReturnsTrueWhenFrameDrainsAndFalseOnTimeout() = runTest {
        val waited = async {
            MemoryPressure.trimAndAwaitDrain(
                level = MemoryPressureLevel.Low,
                timeoutMillis = 100L,
                pollMillis = 10L,
            )
        }
        delay(25L)
        MemoryPressure.markFrameDrained()
        assertTrue(waited.await())

        val timedOut = MemoryPressure.trimAndAwaitDrain(
            level = MemoryPressureLevel.Critical,
            timeoutMillis = 30L,
            pollMillis = 10L,
        )
        assertFalse(timedOut)
    }
}
