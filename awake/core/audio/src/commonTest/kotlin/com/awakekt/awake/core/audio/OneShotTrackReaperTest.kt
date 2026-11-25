/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.audio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OneShotTrackReaperTest {

    private class FakeSoundHandle(
        var playing: Boolean = true,
    ) : SoundHandle {
        var stopCalls: Int = 0

        override val isPlaying: Boolean
            get() = playing

        override fun stop() {
            stopCalls++
            playing = false
        }
    }

    @Test
    fun reapsHandleAfterDurationPlusMarginEvenWhenIsPlayingStaysTrue() {
        var nowMs = 1_000L
        val reaper = OneShotTrackReaper(
            nowMs = { nowMs },
            maxConcurrent = 4,
            reapMarginMs = 150L,
        )
        val staticTrackHandle = FakeSoundHandle(playing = true)

        reaper.track(staticTrackHandle, durationMillis = 500L)
        assertEquals(1, reaper.activeCount)

        // Still within duration + reap margin (1000 + 500 + 150 = 1650).
        nowMs = 1_649L
        reaper.reap()
        assertEquals(1, reaper.activeCount)
        assertEquals(0, staticTrackHandle.stopCalls)
        assertTrue(staticTrackHandle.isPlaying)

        // At exact expiry boundary, handle is stopped and removed even though isPlaying stayed true.
        nowMs = 1_650L
        reaper.reap()
        assertEquals(0, reaper.activeCount)
        assertEquals(1, staticTrackHandle.stopCalls)
        assertFalse(staticTrackHandle.isPlaying)

        // Subsequent reap calls do not stop the handle again.
        nowMs = 2_000L
        reaper.reap()
        assertEquals(1, staticTrackHandle.stopCalls)
    }

    @Test
    fun reapsHandleEarlyWhenNoLongerPlayingAndCallsStop() {
        var nowMs = 500L
        val reaper = OneShotTrackReaper(
            nowMs = { nowMs },
            maxConcurrent = 4,
            reapMarginMs = 150L,
        )
        val finishedEarly = FakeSoundHandle(playing = true)
        val stillPlaying = FakeSoundHandle(playing = true)

        reaper.track(finishedEarly, durationMillis = 10_000L)
        reaper.track(stillPlaying, durationMillis = 10_000L)
        assertEquals(2, reaper.activeCount)

        // Simulate a backend handle whose playback ended early without stop() having been invoked.
        finishedEarly.playing = false
        nowMs = 550L
        reaper.reap()

        assertEquals(1, reaper.activeCount)
        assertEquals(1, finishedEarly.stopCalls)
        assertEquals(0, stillPlaying.stopCalls)
        assertTrue(stillPlaying.isPlaying)
    }

    @Test
    fun reserveSlotEvictsOldestHandleWhenMaxConcurrentReached() {
        var nowMs = 0L
        val reaper = OneShotTrackReaper(
            nowMs = { nowMs },
            maxConcurrent = 3,
            reapMarginMs = 150L,
        )
        val first = FakeSoundHandle()
        val second = FakeSoundHandle()
        val third = FakeSoundHandle()
        val fourth = FakeSoundHandle()

        reaper.reserveSlot()
        reaper.track(first, durationMillis = 1_000L)
        nowMs = 10L
        reaper.reserveSlot()
        reaper.track(second, durationMillis = 1_000L)
        nowMs = 20L
        reaper.reserveSlot()
        reaper.track(third, durationMillis = 1_000L)
        assertEquals(3, reaper.activeCount)
        assertEquals(0, first.stopCalls)

        // Reaching maxConcurrent evicts the oldest tracked handle (first).
        nowMs = 30L
        reaper.reserveSlot()
        assertEquals(2, reaper.activeCount)
        assertEquals(1, first.stopCalls)
        assertFalse(first.isPlaying)
        assertEquals(0, second.stopCalls)
        assertEquals(0, third.stopCalls)

        reaper.track(fourth, durationMillis = 1_000L)
        assertEquals(3, reaper.activeCount)

        // Next reservation evicts second (now the oldest).
        nowMs = 40L
        reaper.reserveSlot()
        assertEquals(2, reaper.activeCount)
        assertEquals(1, second.stopCalls)
        assertEquals(0, third.stopCalls)
        assertEquals(0, fourth.stopCalls)
    }

    @Test
    fun reserveSlotReapsExpiredAndFinishedHandlesBeforeEvictingLiveOnes() {
        var nowMs = 0L
        val reaper = OneShotTrackReaper(
            nowMs = { nowMs },
            maxConcurrent = 2,
            reapMarginMs = 150L,
        )
        val shortHandle = FakeSoundHandle()
        val longHandle = FakeSoundHandle()

        reaper.track(shortHandle, durationMillis = 100L) // expires at 250ms
        reaper.track(longHandle, durationMillis = 2_000L) // expires at 2150ms

        nowMs = 300L
        reaper.reserveSlot()
        assertEquals(1, reaper.activeCount)
        assertEquals(1, shortHandle.stopCalls)
        assertEquals(0, longHandle.stopCalls)

        // Now add a newer handle that finishes early; reserving a slot should reap the newer
        // finished handle instead of evicting the older still-playing handle.
        val earlyFinish = FakeSoundHandle()
        reaper.track(earlyFinish, durationMillis = 2_000L)
        earlyFinish.playing = false

        reaper.reserveSlot()
        assertEquals(1, reaper.activeCount)
        assertEquals(1, earlyFinish.stopCalls)
        assertEquals(0, longHandle.stopCalls)
    }

    @Test
    fun stopAllStopsAndRemovesEveryTrackedHandle() {
        val reaper = OneShotTrackReaper(nowMs = { 0L })
        val handles = List(4) { FakeSoundHandle() }
        handles.forEach { reaper.track(it, durationMillis = 500L) }
        assertEquals(4, reaper.activeCount)

        reaper.stopAll()
        assertEquals(0, reaper.activeCount)
        handles.forEach { handle ->
            assertEquals(1, handle.stopCalls)
            assertFalse(handle.isPlaying)
        }

        // Calling stopAll again is a safe no-op.
        reaper.stopAll()
        handles.forEach { handle ->
            assertEquals(1, handle.stopCalls)
        }
    }

    @Test
    fun negativeDurationCoercesToZeroBeforeApplyingReapMargin() {
        var nowMs = 1_000L
        val reaper = OneShotTrackReaper(
            nowMs = { nowMs },
            reapMarginMs = 150L,
        )
        val handle = FakeSoundHandle()

        reaper.track(handle, durationMillis = -500L)
        nowMs = 1_149L
        reaper.reap()
        assertEquals(1, reaper.activeCount)
        assertEquals(0, handle.stopCalls)

        nowMs = 1_150L
        reaper.reap()
        assertEquals(0, reaper.activeCount)
        assertEquals(1, handle.stopCalls)
    }

    @Test
    fun invalidConfigurationParametersAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            OneShotTrackReaper(nowMs = { 0L }, maxConcurrent = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            OneShotTrackReaper(nowMs = { 0L }, maxConcurrent = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            OneShotTrackReaper(nowMs = { 0L }, reapMarginMs = -1L)
        }
    }
}
