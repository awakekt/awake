/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.Family2System
import com.awakekt.awake.ecs.FamilySpec
import com.awakekt.awake.ecs.World
import com.awakekt.awake.net.prediction.AdaptiveSnapshotTimeline
import com.awakekt.awake.net.prediction.PredictionClock
import com.awakekt.awake.net.prediction.RemotePoseBuffer
import com.awakekt.awake.scene.core.transform.Transform

/**
 * Zero-allocation [Family2System] that advances a shared [AdaptiveSnapshotTimeline] and writes
 * interpolated poses from every active [RemotePose] into its [Transform] each render frame.
 *
 * Register in the frame phase before `TransformSystem`. Excludes entities tagged with
 * [RemotePosePaused] at the ECS [FamilySpec] storage level, skips the whole tick in O(1) via
 * [shouldProcess] before the first snapshot arrives, and uses monomorphic inline [runTick]
 * iteration for 0 B/frame steady-state allocation across large remote populations.
 *
 * @property timeline Shared adaptive server-to-playback clock fed by arriving snapshots.
 * @param clock Monotonic client clock in seconds, sampled once per frame without boxing.
 * @property extrapolationHoldSeconds Maximum lead passed to [AdaptiveSnapshotTimeline.hold].
 */
class RemotePoseSystem(
    val timeline: AdaptiveSnapshotTimeline,
    private val clock: PredictionClock,
    val extrapolationHoldSeconds: Double = RemotePoseBuffer.DEFAULT_EXTRAPOLATION_SECONDS,
) : Family2System<Transform, RemotePose>(TRANSFORM, REMOTE_POSE, ACTIVE_SPEC) {
    init {
        require(extrapolationHoldSeconds.isFinite() && extrapolationHoldSeconds >= 0.0) {
            "extrapolationHoldSeconds must be finite and non-negative, was $extrapolationHoldSeconds."
        }
    }

    private var framePlayback: Double = 0.0
    private var frameInterval: Double = 0.0

    /** Cumulative underruns observed across all active remote entities during the latest tick. */
    var lastFrameUnderruns: Long = 0L
        private set

    override fun shouldProcess(world: World, delta: Float): Boolean = timeline.ready

    override fun begin(world: World, delta: Float) {
        val now = clock.nowSeconds()
        require(now.isFinite()) { "nowSeconds must be finite, was $now." }
        timeline.advance(delta)
        timeline.hold(now, extrapolationHoldSeconds)
        framePlayback = timeline.playbackTime(now)
        frameInterval = timeline.intervalSeconds
        lastFrameUnderruns = 0L
    }

    override fun update(world: World, delta: Float) {
        runTick(world, delta) { _, transform, remote ->
            sampleInto(transform, remote, delta)
        }
    }

    override fun process(world: World, delta: Float, entity: Entity, first: Transform, second: RemotePose) {
        sampleInto(first, second, delta)
    }

    private fun sampleInto(transform: Transform, remote: RemotePose, delta: Float) {
        val buf = remote.buffer
        if (!buf.hasSamples) return
        val before = buf.underruns
        val cadence = buf.cadenceDelay(frameInterval, delta)
        if (buf.sample(framePlayback - cadence)) {
            transform.position.set(buf.x, buf.y, buf.z)
            if (remote.applyYaw) transform.rotation.y = buf.yaw
            lastFrameUnderruns += buf.underruns - before
        }
    }

    private companion object {
        val TRANSFORM = Transform::class
        val REMOTE_POSE = RemotePose::class
        val ACTIVE_SPEC = FamilySpec.exclude(RemotePosePaused::class)
    }
}
