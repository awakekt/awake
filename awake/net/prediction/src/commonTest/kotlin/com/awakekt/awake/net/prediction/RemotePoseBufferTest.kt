/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.net.prediction

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RemotePoseBufferTest {
    @Test
    fun interpolatesPositionPitchAndShortestArcYaw() {
        val buf = RemotePoseBuffer()
        assertFalse(buf.sample(0.0))
        val nearPi = (PI - 0.1).toFloat()
        val pastNegPi = (-PI + 0.1).toFloat()
        buf.push(0.0, 0f, 1f, 0f, yaw = nearPi, pitch = -10f, grounded = true)
        buf.push(0.1, 1f, 3f, 2f, yaw = pastNegPi, pitch = 10f, grounded = false)

        assertTrue(buf.sample(0.05))
        assertEquals(0.5f, buf.x, 1e-4f)
        assertEquals(2f, buf.y, 1e-4f)
        assertEquals(1f, buf.z, 1e-4f)
        assertEquals(0f, buf.pitch, 1e-4f)
        assertTrue(abs(abs(buf.yaw) - PI.toFloat()) < 1e-3f, "shortest arc crossed pi: yaw=${buf.yaw}")
        assertTrue(buf.grounded)
        assertFalse(buf.underrun)
        assertEquals(0L, buf.underruns)
    }

    @Test
    fun extrapolatesBoundedVelocityAndBlendsBackAfterUnderrun() {
        val buf = RemotePoseBuffer(extrapolationSeconds = 0.1)
        buf.push(0.0, 0f, 1f, 0f, grounded = true)
        buf.push(0.1, 1f, 1.5f, 0f, grounded = true)

        // 50 ms past newest: extrapolates +0.5 m on X, holds Y because grounded.
        assertTrue(buf.sample(0.15))
        assertTrue(buf.underrun)
        assertEquals(1L, buf.underruns)
        assertEquals(1.5f, buf.x, 1e-4f)
        assertEquals(1.5f, buf.y, 1e-4f)

        // 300 ms past newest: clamped at 100 ms extrapolation (x = 2.0).
        assertTrue(buf.sample(0.4))
        assertEquals(2.0f, buf.x, 1e-4f)

        // Server stopped at x = 1.0: resumes interpolating and blends residual error smoothly.
        buf.push(0.5, 1.0f, 1.5f, 0f, grounded = true)
        assertTrue(buf.sample(0.45))
        assertFalse(buf.underrun)
        assertTrue(buf.x in 1.05f..1.99f, "post-underrun error blends smoothly instead of snapping: x=${buf.x}")
    }

    @Test
    fun warpSnapsDirectlyWithoutInterpolating() {
        val buf = RemotePoseBuffer()
        buf.push(0.0, 0f, 0f, 0f)
        buf.push(0.1, 50f, 0f, 0f)
        assertTrue(buf.sample(0.05))
        assertEquals(0f, buf.x)
        assertTrue(buf.sample(0.1))
        assertEquals(50f, buf.x)
    }

    @Test
    fun decimatedInterestTierCadenceDelayEliminatesUnderrunsAndResetClearsAllState() {
        val interval = 1.0 / 15.0
        val timeline = AdaptiveSnapshotTimeline(
            minDelaySeconds = 2.0 / 30.0,
            maxDelaySeconds = 0.25,
            initialIntervalSeconds = interval,
        ).apply { headroomSeconds = 1.0 / 30.0 }
        val buf = RemotePoseBuffer()
        val dt = 1f / 60f
        var sent = 0
        var lastPlayback = Double.NEGATIVE_INFINITY
        for (frame in 0..480) {
            val now = frame * dt.toDouble()
            timeline.advance(dt)
            while (sent * interval <= now + 1e-9) {
                val server = sent * interval
                timeline.observe(now, server)
                val age = sent + 1
                val pushed = if (age <= 3) {
                    true
                } else if (age <= 12) {
                    sent % 2 == 0
                } else {
                    sent % 4 == 0
                }
                if (pushed) {
                    buf.push(server, (server * 3.0).toFloat(), 0f, 0f, arrivedAtSeconds = now)
                } else {
                    buf.noteSkipped(server)
                }
                sent++
            }
            val effective = timeline.playbackTime(now) - buf.cadenceDelay(timeline.intervalSeconds, dt)
            assertTrue(effective >= lastPlayback - 1e-9, "effective playback never rewinds")
            lastPlayback = effective
            buf.sample(effective)
        }
        assertEquals(0L, buf.underruns, "eased-in decimated tier achieves zero underruns")

        buf.reset()
        assertFalse(buf.hasSamples)
        assertEquals(0L, buf.underruns)
        assertEquals(0f, buf.speed)
        assertTrue(buf.newestTime.isNaN())
    }

    @Test
    fun derivedVelocityAndConfigurableWarpThresholds() {
        val buf = RemotePoseBuffer(
            extrapolationSeconds = 0.1,
            blendSeconds = 0.05,
            warpMetres = 20f,
            warpSpeedMetresPerSecond = 200.0,
        )
        buf.push(0.0, 0f, 0f, 0f, grounded = false)
        buf.push(0.1, 3f, 0f, 4f, grounded = false)

        // Interpolating: 30 m/s on X, 40 m/s on Z -> planarSpeed = speed = 50 m/s (allowed by custom warp threshold).
        assertTrue(buf.sample(0.05))
        assertEquals(1.5f, buf.x, 1e-4f)
        assertEquals(30f, buf.velocityX, 1e-3f)
        assertEquals(40f, buf.velocityZ, 1e-3f)
        assertEquals(50f, buf.planarSpeed, 1e-3f)
        assertEquals(50f, buf.speed, 1e-3f)

        // Extrapolating within lead retains velocity; holding past extrapolationSeconds zeroes velocity.
        assertTrue(buf.sample(0.15))
        assertEquals(50f, buf.speed, 1e-3f)
        assertTrue(buf.sample(0.30))
        assertEquals(0f, buf.speed, 1e-4f)

        // Default warp threshold zeroes velocity on teleport.
        val strict = RemotePoseBuffer()
        strict.push(0.0, 0f, 0f, 0f)
        strict.push(0.1, 50f, 0f, 0f)
        assertTrue(strict.sample(0.05))
        assertEquals(0f, strict.speed)
    }
}
