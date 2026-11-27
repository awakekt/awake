/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.prediction.AdaptiveSnapshotTimeline
import com.awakekt.awake.net.prediction.CorrectionKind
import com.awakekt.awake.net.prediction.PredictionClock
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PredictedBodyAndRemotePoseTest {
    @Test
    fun predictedTransformResetAndPredictionPausedTagWorkCleanly() {
        val world = World()
        val entity = world.create()
        val transform = Transform()
        val predicted = PredictedTransform()
        world.add(entity, transform)
        world.add(entity, predicted)

        var now = 0.0
        val system = PredictionSystem(PredictionClock { now })
        system.update(world, DT)
        predicted.recordInput(transform, 1L, now)
        predicted.snapshot(1L, 0, 5f, 0f, 0f)

        // Tag pauses reconciliation at the FamilySpec storage level.
        world.add(entity, PredictionPaused)
        now += DT
        system.update(world, DT)
        assertEquals(0f, transform.position.x, "paused entity must not be reconciled")

        // Removing the tag resumes reconciliation.
        world.remove<PredictionPaused>(entity)
        now += DT
        system.update(world, DT)
        assertEquals(CorrectionKind.Shift, predicted.correctionKind)
        assertEquals(5f, transform.position.x)

        // Poolable reset clears history and state so sequence 1 can be recorded again.
        predicted.reset()
        assertEquals(-1L, predicted.history.newestSeq)
        assertEquals(CorrectionKind.None, predicted.correctionKind)
        assertEquals(1f, predicted.speedScale)
        transform.position.set(0f, 0f, 0f)
        system.update(world, DT)
        predicted.recordInput(transform, 1L, now)
        assertEquals(1L, predicted.history.newestSeq)
    }

    @Test
    fun predictedBodyReconcilesWithoutMutatingTransformAndRespectsAckAheadAndPause() {
        val world = World()
        val entity = world.create()
        val transform = Transform()
        val body = PredictedBody()
        var appliedShifts = 0
        var lastShiftX = 0f
        body.applier = PredictedBodyApplier { _, _, correction, _ ->
            if (correction.kind == CorrectionKind.Shift) {
                appliedShifts++
                lastShiftX = correction.x
            }
        }
        world.add(entity, transform)
        world.add(entity, body)

        var now = 0.0
        val system = PredictedBodySystem { now }
        body.cadence.ensureStepsPerInput(DT, serverTicksPerSecond = 30)

        // While input 1 is still open on the client, ack=1 is ahead and waits until the window closes.
        body.cadence.onLatched(repayAllowed = false)
        body.markInputSent(1L)
        body.snapshot(1L, 0, 4f, 0f, 0f)
        body.setSimulatedPose(1f, 0f, 0f)
        system.update(world, DT)
        assertEquals(0, appliedShifts, "ack ahead of open input window must not shift early")
        assertEquals(CorrectionKind.None, body.correction.kind)

        // Finish input 1 window, record pose, then reconcile.
        body.cadence.onStepCompleted()
        assertTrue(body.cadence.onStepCompleted())
        body.recordInput(1L, 1f, 0f, 0f, now)
        now += DT
        system.update(world, DT)
        assertEquals(1, appliedShifts)
        assertEquals(3f, lastShiftX, 1e-4f)
        assertEquals(4f, body.simulatedX, 1e-4f)
        assertEquals(0f, transform.position.x, "PredictedBody must never mutate Transform directly")

        // PredictionPaused excludes entity at storage level.
        world.add(entity, PredictionPaused)
        body.recordInput(2L, 4f, 0f, 0f, now)
        body.snapshot(2L, 0, 8f, 0f, 0f)
        now += DT
        system.update(world, DT)
        assertEquals(1, appliedShifts, "paused body must be skipped")

        // Poolable reset clears everything.
        body.reset()
        assertEquals(-1L, body.history.newestSeq)
        assertEquals(0L, body.lastSentSeq)
        assertEquals(CorrectionKind.None, body.correction.kind)
        assertFalse(body.cadence.open)
    }

    @Test
    fun remotePoseSystemInterpolatesActiveEntitiesAndSkipsPausedEntities() {
        val world = World()
        val active = world.create()
        val paused = world.create()
        val activeTransform = Transform()
        val pausedTransform = Transform()
        val activePose = RemotePose()
        val pausedPose = RemotePose()
        world.add(active, activeTransform)
        world.add(active, activePose)
        world.add(paused, pausedTransform)
        world.add(paused, pausedPose)
        world.add(paused, RemotePosePaused)

        val interval = 1.0 / 15.0
        val timeline = AdaptiveSnapshotTimeline(
            minDelaySeconds = 2.0 / 30.0,
            maxDelaySeconds = 0.25,
            initialIntervalSeconds = interval,
        ).apply { headroomSeconds = 1.0 / 30.0 }
        var now = 0.0
        val system = RemotePoseSystem(timeline, { now })

        // Before any snapshot arrives, shouldProcess early-outs cleanly.
        system.update(world, DT)
        assertEquals(0f, activeTransform.position.x)

        var sent = 0
        for (frame in 0..120) {
            now = frame * DT.toDouble()
            while (sent * interval <= now + 1e-9) {
                val s = sent * interval
                timeline.observe(now, s)
                activePose.push(s, (s * 4.0).toFloat(), 0f, 0f, yaw = 1.2f, arrivedAtSeconds = now)
                pausedPose.push(s, (s * 4.0).toFloat(), 0f, 0f, yaw = 1.2f, arrivedAtSeconds = now)
                sent++
            }
            system.update(world, DT)
        }

        assertTrue(activeTransform.position.x > 6f, "active remote entity moved smoothly: x=${activeTransform.position.x}")
        assertEquals(4f, activePose.velocityX, 0.2f)
        assertEquals(4f, activePose.planarSpeed, 0.2f)
        assertEquals(1.2f, activeTransform.rotation.y, 1e-4f)
        assertEquals(0f, pausedTransform.position.x, "paused remote entity was excluded at storage level")
        assertEquals(0L, activePose.underruns)

        activePose.reset()
        assertFalse(activePose.buffer.hasSamples)
        assertEquals(0f, activePose.speed)
        assertTrue(activePose.applyYaw)
    }

    private companion object {
        const val DT = 1f / 60f
    }
}
