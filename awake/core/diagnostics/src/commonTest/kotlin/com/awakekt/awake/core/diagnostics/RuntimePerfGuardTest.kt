/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Unit tests for [RuntimePerfGuard]: rolling p90 evaluation, degrade/recover hysteresis,
 * post-transition cooldown + window flush, oscillation recovery backoff, invalid sample ignoring,
 * spike clamping, target-FPS presets, and reset.
 */
class RuntimePerfGuardTest {
    private fun compactConfig(
        windowFrames: Int = 10,
        evalInterval: Int = 5,
        degradeAfter: Int = 2,
        recoverAfter: Int = 2,
        cooldown: Int = 10,
    ) = RuntimePerfGuardConfig(
        windowFrames = windowFrames,
        evaluationIntervalFrames = evalInterval,
        quantile = 0.90,
        slowThresholdMs = 38.0,
        severeThresholdMs = 55.0,
        recoverToNormalMs = 30.0,
        recoverToSlowMs = 42.0,
        degradeAfterWindows = degradeAfter,
        recoverAfterWindows = recoverAfter,
        maxRecoverAfterWindows = 8,
        recoveryBackoffMultiplier = 2,
        cooldownFrames = cooldown,
    )

    @Test
    fun staysNormalUnderTargetAndIgnoresInvalidOrIsolatedSpikes() {
        val guard = RuntimePerfGuard(compactConfig())
        // Invalid samples do not fill the buffer or perturb state.
        assertEquals(RuntimePerfGuard.Tier.Normal, guard.recordFrame(Double.NaN))
        assertEquals(RuntimePerfGuard.Tier.Normal, guard.recordFrame(Double.POSITIVE_INFINITY))
        assertEquals(RuntimePerfGuard.Tier.Normal, guard.recordFrame(0.0))
        assertEquals(RuntimePerfGuard.Tier.Normal, guard.recordFrame(-16.0))
        assertEquals(0, guard.sampleCount)

        // 1 isolated hitch per 10 frames sits above p90 (index 8 of 0..9 = 9th smallest) -> stays Normal.
        repeat(50) { frame ->
            val ms = if (frame % 10 == 0) 900.0 else 25.0
            assertEquals(RuntimePerfGuard.Tier.Normal, guard.recordFrame(ms))
        }
        assertEquals(25.0, guard.lastQuantileMs)
        assertEquals(0, guard.transitions)
    }

    @Test
    fun degradesThroughSlowToSevereOnlyAfterConsecutiveWindowsAndCooldown() {
        val guard = RuntimePerfGuard(compactConfig())
        val log = mutableListOf<Pair<RuntimePerfGuard.Tier, RuntimePerfGuard.Tier>>()
        fun feed(frames: Int, ms: Double) {
            repeat(frames) {
                guard.recordFrame(ms) { from, to -> log += from to to }
            }
        }

        // First full window (10 frames) counts 1 slow window -> still Normal.
        feed(10, 44.0)
        assertEquals(RuntimePerfGuard.Tier.Normal, guard.tier)
        // Second slow evaluation (+5 frames = 15 total) triggers Normal -> Slow and flushes window.
        feed(5, 44.0)
        assertEquals(RuntimePerfGuard.Tier.Slow, guard.tier)
        assertEquals(listOf(RuntimePerfGuard.Tier.Normal to RuntimePerfGuard.Tier.Slow), log)
        assertEquals(0, guard.sampleCount, "transition must flush pre-transition samples")

        // Severe load must fill a fresh post-transition window and survive degradeAfterWindows=2.
        feed(10, 62.0)
        assertEquals(RuntimePerfGuard.Tier.Slow, guard.tier)
        feed(5, 62.0)
        assertEquals(RuntimePerfGuard.Tier.Severe, guard.tier)
        assertEquals(
            listOf(
                RuntimePerfGuard.Tier.Normal to RuntimePerfGuard.Tier.Slow,
                RuntimePerfGuard.Tier.Slow to RuntimePerfGuard.Tier.Severe,
            ),
            log,
        )
        assertEquals(2, guard.degradeCount)
        assertEquals(0, guard.recoverCount)
    }

    @Test
    fun hysteresisDeadbandPreventsPrematureRecoveryAndBackoffStopsOscillation() {
        val guard = RuntimePerfGuard(compactConfig(recoverAfter = 2))

        // Degrade Normal -> Slow.
        repeat(15) { guard.recordFrame(45.0) }
        assertEquals(RuntimePerfGuard.Tier.Slow, guard.tier)

        // Frame time of 34 ms sits in the deadband (recoverToNormalMs=30 < 34 < severe=55): never recovers.
        repeat(40) { guard.recordFrame(34.0) }
        assertEquals(RuntimePerfGuard.Tier.Slow, guard.tier)

        // Calm frames (24 ms <= 30 ms): 2 windows (10 + 5 = 15 frames) recover Slow -> Normal.
        repeat(10) { guard.recordFrame(24.0) }
        assertEquals(RuntimePerfGuard.Tier.Slow, guard.tier)
        repeat(5) { guard.recordFrame(24.0) }
        assertEquals(RuntimePerfGuard.Tier.Normal, guard.tier)
        assertEquals(1, guard.recoverCount)
        assertEquals(2, guard.requiredRecoverWindows)

        // Oscillate: degrade again after a recovery -> requiredRecoverWindows doubles (2 -> 4).
        repeat(15) { guard.recordFrame(45.0) }
        assertEquals(RuntimePerfGuard.Tier.Slow, guard.tier)
        assertEquals(4, guard.requiredRecoverWindows)

        // 2 calm windows (15 frames) are no longer enough after backoff.
        repeat(15) { guard.recordFrame(22.0) }
        assertEquals(RuntimePerfGuard.Tier.Slow, guard.tier)
        // 2 more calm windows (+10 frames = 4 windows total) recover cleanly.
        repeat(10) { guard.recordFrame(22.0) }
        assertEquals(RuntimePerfGuard.Tier.Normal, guard.tier)
        assertEquals(2, guard.recoverCount)
    }

    @Test
    fun directJumpFromNormalToSevereAndStepwiseRecovery() {
        val guard = RuntimePerfGuard(compactConfig())
        repeat(15) { guard.recordFrame(75.0) }
        assertEquals(RuntimePerfGuard.Tier.Severe, guard.tier)

        // Recover one tier at a time: Severe -> Slow at <= 42 ms, then Slow -> Normal at <= 30 ms.
        repeat(15) { guard.recordFrame(36.0) }
        assertEquals(RuntimePerfGuard.Tier.Slow, guard.tier)
        repeat(15) { guard.recordFrame(24.0) }
        assertEquals(RuntimePerfGuard.Tier.Normal, guard.tier)
    }

    @Test
    fun resetRestoresInitialStateAndTargetFpsPresetScalesThresholds() {
        val guard60 = RuntimePerfGuard.forTargetFps(targetFps = 60, windowFrames = 20)
        assertTrue(guard60.config.slowThresholdMs in 18.0..20.5)
        assertTrue(guard60.config.recoverToNormalMs < guard60.config.slowThresholdMs)
        assertTrue(guard60.config.severeThresholdMs > guard60.config.slowThresholdMs)

        repeat(40) { guard60.recordFrame(24.0) }
        assertEquals(RuntimePerfGuard.Tier.Slow, guard60.tier)

        guard60.reset()
        assertEquals(RuntimePerfGuard.Tier.Normal, guard60.tier)
        assertEquals(0, guard60.sampleCount)
        assertEquals(0, guard60.transitions)
        assertEquals(0.0, guard60.lastQuantileMs)
        assertEquals(guard60.config.recoverAfterWindows, guard60.requiredRecoverWindows)
    }

    @Test
    fun configValidatesInvariants() {
        assertFailsWith<IllegalArgumentException> { RuntimePerfGuardConfig(windowFrames = 0) }
        assertFailsWith<IllegalArgumentException> { RuntimePerfGuardConfig(windowFrames = 10, evaluationIntervalFrames = 11) }
        assertFailsWith<IllegalArgumentException> { RuntimePerfGuardConfig(quantile = 0.0) }
        assertFailsWith<IllegalArgumentException> { RuntimePerfGuardConfig(slowThresholdMs = 40.0, recoverToNormalMs = 40.0) }
        assertFailsWith<IllegalArgumentException> { RuntimePerfGuardConfig(slowThresholdMs = 50.0, severeThresholdMs = 45.0) }
        assertFailsWith<IllegalArgumentException> { RuntimePerfGuardConfig.forTargetFps(0) }
    }
}
