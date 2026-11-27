/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertTrue

/** Verifies zero steady-state allocations across all hot prediction and interpolation primitives. */
class PredictionAllocationTest {
    private data object CustomHold : SnapReason {
        override val label: String get() = "hold"
    }

    @Test
    fun reconcileIntoAndPredictedEntityAllocateNothingAcrossAllCorrectionKinds() {
        val config = ReconcileConfig()
        val history = PredictionHistory()
        val entity = PredictedEntity()
        val input = ReconcileInput()
        val out = MutableCorrection()
        val holdRule = ReconcileRules { CustomHold }
        var seq = 0L
        var sink = 0f

        fun step() {
            seq++
            history.record(seq, 1f, 0f, 0f, seq * 0.033)
            entity.position.set(1f, 0f, 0f)
            // Blend
            input.server(1.2f, 0f, 0f).drawn(1f, 0f, 0f)
            input.ackSeq = seq
            input.ackFresh = true
            input.ackUnread = true
            input.guessedTicks = 0
            input.lookUp(history, seq * 0.033)
            reconcileInto(input, config, out)
            entity.apply(out, history)
            entity.blend(0.2f, history)
            // Shift
            input.server(4f, 0f, 0f).drawn(1f, 0f, 0f)
            reconcileInto(input, config, out)
            entity.apply(out, history)
            entity.update(1f / 60f)
            // Snap
            reconcileInto(input, config, out, holdRule)
            entity.apply(out, history)
            // Easing Wait
            input.secondsSinceOldestUnheard = 0.625
            reconcileInto(input, config, out)
            entity.apply(out, history)
            sink += entity.drawnX + entity.speedScale
        }

        repeat(WARMUP) { step() }
        val reading = readAllocation(MEASURED) { repeat(MEASURED) { step() } }
        assertAllocatesNothing(reading, "reconcileInto + PredictedEntity")
        assertTrue(sink.isFinite())
    }

    @Test
    fun inputCadenceAllocatesNothing() {
        val cadence = InputCadence()
        cadence.ensureStepsPerInput(1f / 60f, 30)
        var completed = 0L

        fun step() {
            if (!cadence.open) cadence.onLatched(repayAllowed = true)
            if (cadence.onStepCompleted()) completed++
            cadence.tryRealignFromRest(canRealign = true)
            completed += cadence.droppedSteps(1f / 60f, 1, 1f / 60f, 5, reported = 0)
        }

        repeat(WARMUP) { step() }
        val reading = readAllocation(MEASURED) { repeat(MEASURED) { step() } }
        assertAllocatesNothing(reading, "InputCadence")
        assertTrue(completed > 0L)
    }

    @Test
    fun adaptiveTimelineAndRemotePoseBufferAllocateNothing() {
        val timeline = AdaptiveSnapshotTimeline(
            minDelaySeconds = 2.0 / 30.0,
            maxDelaySeconds = 0.25,
            initialIntervalSeconds = 1.0 / 15.0,
        ).apply { headroomSeconds = 1.0 / 30.0 }
        val buf = RemotePoseBuffer()
        var now = 0.0
        var lastPush = -1.0
        var sink = 0f

        fun step() {
            now += 1.0 / 60.0
            timeline.advance(1f / 60f)
            if (now - lastPush >= 1.0 / 15.0) {
                lastPush = now
                timeline.observe(now, now)
                buf.push(now, (now * 3.0).toFloat(), 0f, 0f, yaw = 0.5f, arrivedAtSeconds = now)
            } else {
                buf.noteSkipped(now)
            }
            timeline.hold(now, 0.1)
            val cadence = buf.cadenceDelay(timeline.intervalSeconds, 1f / 60f)
            buf.sample(timeline.playbackTime(now) - cadence)
            sink += buf.x + buf.yaw
        }

        repeat(WARMUP) { step() }
        val reading = readAllocation(MEASURED) { repeat(MEASURED) { step() } }
        assertAllocatesNothing(reading, "AdaptiveSnapshotTimeline + RemotePoseBuffer")
        assertTrue(sink.isFinite())
    }

    private companion object {
        const val WARMUP = 5_000
        const val MEASURED = 10_000
    }
}
