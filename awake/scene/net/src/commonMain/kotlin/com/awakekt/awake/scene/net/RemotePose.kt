/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.net

import com.awakekt.awake.ecs.Poolable
import com.awakekt.awake.net.prediction.RemotePoseBuffer

/**
 * Zero-allocation, [Poolable] ECS component holding buffered authoritative snapshots for one
 * remote networked entity, driven each render frame by [RemotePoseSystem].
 *
 * Attach alongside a root [com.awakekt.awake.scene.core.transform.Transform]. Push arriving server
 * poses via [push] (and omitted interest-tier ticks via [noteSkipped]); [RemotePoseSystem] writes
 * the interpolated position (and yaw when [applyYaw] is `true`) directly into `Transform`.
 *
 * Add [RemotePosePaused] when a remote entity is hidden, culled, or asleep to skip it at the ECS
 * storage level with zero per-entity branch cost.
 *
 * @property extrapolationSeconds Maximum extrapolation lead past the newest snapshot in seconds.
 * @property capacity Number of buffered snapshot poses retained (at least `2`).
 * @property blendSeconds Exponential time constant decaying post-underrun extrapolation error.
 * @property warpMetres Minimum jump distance in metres before [warpSpeedMetresPerSecond] snaps.
 * @property warpSpeedMetresPerSecond Implied speed above which a transition snaps instead of interpolating.
 */
class RemotePose(
    val extrapolationSeconds: Double = RemotePoseBuffer.DEFAULT_EXTRAPOLATION_SECONDS,
    val capacity: Int = RemotePoseBuffer.DEFAULT_CAPACITY,
    val blendSeconds: Double = RemotePoseBuffer.BLEND_SECONDS,
    val warpMetres: Float = RemotePoseBuffer.WARP_METRES,
    val warpSpeedMetresPerSecond: Double = RemotePoseBuffer.WARP_METRES_PER_SECOND,
) : Poolable {
    /** Underlying zero-allocation primitive ring buffer. */
    val buffer: RemotePoseBuffer = RemotePoseBuffer(
        extrapolationSeconds = extrapolationSeconds,
        capacity = capacity,
        blendSeconds = blendSeconds,
        warpMetres = warpMetres,
        warpSpeedMetresPerSecond = warpSpeedMetresPerSecond,
    )

    /** Whether [RemotePoseSystem] writes [yaw] into `Transform.rotation.y`. */
    var applyYaw: Boolean = true

    /** Sampled X position after the latest [RemotePoseSystem] tick. */
    val x: Float
        get() = buffer.x

    /** Sampled Y position after the latest [RemotePoseSystem] tick. */
    val y: Float
        get() = buffer.y

    /** Sampled Z position after the latest [RemotePoseSystem] tick. */
    val z: Float
        get() = buffer.z

    /** Sampled yaw in radians after the latest [RemotePoseSystem] tick. */
    val yaw: Float
        get() = buffer.yaw

    /** Sampled pitch after the latest [RemotePoseSystem] tick. */
    val pitch: Float
        get() = buffer.pitch

    /** Sampled ground-contact flag after the latest [RemotePoseSystem] tick. */
    val grounded: Boolean
        get() = buffer.grounded

    /** Derived instantaneous X velocity in metres/second after the latest tick. */
    val velocityX: Float
        get() = buffer.velocityX

    /** Derived instantaneous Y velocity in metres/second after the latest tick. */
    val velocityY: Float
        get() = buffer.velocityY

    /** Derived instantaneous Z velocity in metres/second after the latest tick. */
    val velocityZ: Float
        get() = buffer.velocityZ

    /** Derived 3D speed in metres/second after the latest tick, for smooth locomotion animation. */
    val speed: Float
        get() = buffer.speed

    /** Derived horizontal (XZ) speed in metres/second after the latest tick. */
    val planarSpeed: Float
        get() = buffer.planarSpeed

    /** Whether the latest sample ran past [RemotePoseBuffer.newestTime]. */
    val underrun: Boolean
        get() = buffer.underrun

    /** Cumulative count of samples that ran past [RemotePoseBuffer.newestTime]. */
    val underruns: Long
        get() = buffer.underruns

    /** Distance in metres between the drawn pose and the raw buffered snapshot curve. */
    val renderError: Float
        get() = buffer.renderError

    /** Appends an authoritative pose at [serverTimeSeconds] into [buffer]. */
    @Suppress("LongParameterList")
    fun push(
        serverTimeSeconds: Double,
        x: Float,
        y: Float,
        z: Float,
        yaw: Float = 0f,
        pitch: Float = 0f,
        grounded: Boolean = true,
        arrivedAtSeconds: Double = Double.NaN,
    ) {
        buffer.push(serverTimeSeconds, x, y, z, yaw, pitch, grounded, arrivedAtSeconds)
    }

    /** Notes that a snapshot at [snapshotTimeSeconds] omitted this entity (decimated interest tier). */
    fun noteSkipped(snapshotTimeSeconds: Double) {
        buffer.noteSkipped(snapshotTimeSeconds)
    }

    /** Clears all buffered poses, counters, and flags back to defaults for ECS pooling or reuse. */
    override fun reset() {
        buffer.reset()
        applyYaw = true
    }
}
