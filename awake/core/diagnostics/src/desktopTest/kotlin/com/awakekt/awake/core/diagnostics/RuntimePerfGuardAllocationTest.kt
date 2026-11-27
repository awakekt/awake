/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that [RuntimePerfGuard.recordFrame] allocates zero heap bytes per frame under
 * `-XX:-DoEscapeAnalysis`, including quantile sorts and tier transitions.
 */
class RuntimePerfGuardAllocationTest {
    @Test
    fun recordFrameAllocatesNothingAcrossEvaluationsAndTransitions() {
        val guard = RuntimePerfGuard(
            RuntimePerfGuardConfig(
                windowFrames = 60,
                evaluationIntervalFrames = 30,
                degradeAfterWindows = 2,
                recoverAfterWindows = 2,
                cooldownFrames = 30,
            ),
        )
        // Warm up JIT across both degrade and recover paths.
        repeat(240) { guard.recordFrame(if (it < 120) 48.0 else 22.0) }
        guard.reset()

        var transitionSink = 0
        var lastTier = RuntimePerfGuard.Tier.Normal
        val reading = readAllocation(FRAMES) {
            for (frame in 0 until FRAMES) {
                val phase = (frame / 180) % 2
                val ms = if (phase == 0) 46.0 else 22.0
                lastTier = guard.recordFrame(ms) { _, _ -> transitionSink++ }
            }
        }
        println("RuntimePerfGuard.recordFrame: $reading (transitions=$transitionSink, lastTier=$lastTier)")
        assertAllocatesNothing(reading, "RuntimePerfGuard.recordFrame")
        assertTrue(transitionSink > 0, "expected transitions during allocation sweep")
    }

    private companion object {
        const val FRAMES = 1_200
    }
}
