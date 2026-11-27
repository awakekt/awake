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
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertTrue

/** Verifies zero per-frame steady-state allocations across all ECS prediction and remote-pose systems. */
class NetSceneAllocationTest {
    @Test
    fun predictionSystemAllocatesNothingEvenWhileBlendingAndShifting() {
        val world = World()
        val active = Array(ACTIVE_LOCAL) {
            val e = world.create()
            val t = Transform()
            val p = PredictedTransform()
            world.add(e, t)
            world.add(e, p)
            t to p
        }
        repeat(PAUSED_COUNT) {
            val e = world.create()
            world.add(e, Transform())
            world.add(e, PredictedTransform())
            world.add(e, PredictionPaused)
        }
        var now = 0.0
        var seq = 0L
        var sink = 0f
        val system = PredictionSystem(PredictionClock { now })

        fun frame() {
            now += DT.toDouble()
            seq++
            for (i in active.indices) {
                val (t, p) = active[i]
                t.position.x += 0.1f
                p.recordInput(t, seq, now, walked = 0.1f)
                val drift = if (seq % 10L == 0L) 2.0f else 0.15f
                p.snapshot(seq, 0, t.position.x + drift, 0f, 0f)
            }
            system.update(world, DT)
            sink += active[0].second.drawnX
        }

        repeat(WARMUP) { frame() }
        val reading = readAllocation(MEASURED) { repeat(MEASURED) { frame() } }
        assertAllocatesNothing(reading, "PredictionSystem.update")
        assertTrue(sink.isFinite())
    }

    @Test
    fun predictedBodySystemAllocatesNothingEvenWhileBlendingAndShifting() {
        val world = World()
        var applierHits = 0L
        val applier = PredictedBodyApplier { _, _, correction, _ ->
            if (correction.kind != CorrectionKind.None) applierHits++
        }
        val active = Array(ACTIVE_LOCAL) {
            val e = world.create()
            val b = PredictedBody(applier = applier)
            world.add(e, b)
            b
        }
        repeat(PAUSED_COUNT) {
            val e = world.create()
            world.add(e, PredictedBody(applier = applier))
            world.add(e, PredictionPaused)
        }
        var now = 0.0
        var seq = 0L
        val system = PredictedBodySystem { now }

        fun frame() {
            now += DT.toDouble()
            seq++
            for (i in active.indices) {
                val b = active[i]
                val x = b.simulatedX + 0.1f
                b.setSimulatedPose(x, 0f, 0f)
                b.recordInput(seq, x, 0f, 0f, now)
                val drift = if (seq % 10L == 0L) 2.0f else 0.15f
                b.snapshot(seq, 0, x + drift, 0f, 0f)
            }
            system.update(world, DT)
        }

        repeat(WARMUP) { frame() }
        val reading = readAllocation(MEASURED) { repeat(MEASURED) { frame() } }
        assertAllocatesNothing(reading, "PredictedBodySystem.update")
        assertTrue(applierHits > 0L)
    }

    @Test
    fun remotePoseSystemAllocatesNothingAcrossActiveAndPausedEntities() {
        val world = World()
        val remotes = Array(ACTIVE_REMOTES) {
            val e = world.create()
            val t = Transform()
            val r = RemotePose()
            world.add(e, t)
            world.add(e, r)
            r
        }
        repeat(PAUSED_COUNT) {
            val e = world.create()
            world.add(e, Transform())
            world.add(e, RemotePose())
            world.add(e, RemotePosePaused)
        }
        val interval = 1.0 / 15.0
        val timeline = AdaptiveSnapshotTimeline(
            minDelaySeconds = 2.0 / 30.0,
            maxDelaySeconds = 0.25,
            initialIntervalSeconds = interval,
        ).apply { headroomSeconds = 1.0 / 30.0 }
        var now = 0.0
        var lastPush = -1.0
        val system = RemotePoseSystem(timeline, { now })

        fun frame() {
            now += DT.toDouble()
            if (now - lastPush >= interval) {
                lastPush = now
                timeline.observe(now, now)
                val x = (now * 3.0).toFloat()
                for (i in remotes.indices) remotes[i].push(now, x, 0f, 0f, yaw = 0.4f, arrivedAtSeconds = now)
            }
            system.update(world, DT)
        }

        repeat(WARMUP) { frame() }
        val reading = readAllocation(MEASURED) { repeat(MEASURED) { frame() } }
        assertAllocatesNothing(reading, "RemotePoseSystem.update")
        assertTrue(remotes[0].x > 0f)
    }

    private companion object {
        const val DT = 1f / 60f
        const val ACTIVE_LOCAL = 4
        const val ACTIVE_REMOTES = 64
        const val PAUSED_COUNT = 64
        const val WARMUP = 2_000
        const val MEASURED = 5_000
    }
}
