/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.director

import com.awakekt.awake.core.camera.CameraGround
import com.awakekt.awake.testing.allocation.assertAllocatesNothing
import com.awakekt.awake.testing.allocation.readAllocation
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop-only zero-allocation guard: proves steady-state [DirectorPlayer.step] allocates 0 bytes
 * per frame even with orbit framing, handheld fBm noise, terrain collision, day-cycle sweep, and
 * dynamic hero rim lighting all active simultaneously.
 */
class DirectorAllocationTest {
    @Test
    fun directorPlayerStepAllocatesZeroBytesPerFrame() {
        val script = directorScript("alloc_probe") {
            shot("orbit_hero") {
                seconds(10f)
                orbit("Hero", startAzimuthDeg = -180f, endAzimuthDeg = 180f)
                feel = CameraFeelSpec(handheldIntensity = 0.4f, smoothTime = 0.1f, baseTrauma = 0.25f)
            }
            lighting(
                LightingCueSpec.DayCycleSweep(
                    at = TimeRef.seconds(0f),
                    duration = DurationRef.seconds(10f),
                    fromTime = 0.25f,
                    toTime = 0.75f,
                ),
            )
            lighting(
                LightingCueSpec.HeroRimLight(
                    at = TimeRef.seconds(0f),
                    duration = DurationRef.seconds(10f),
                    subject = "Hero",
                ),
            )
        }
        val player = DirectorPlayer(script, loop = true)
        val pose = DirectedCameraPose()
        val atmo = DirectedAtmosphereState()
        val ground = CameraGround { _, _ -> 0f }
        val stage = object : StageQuery {
            override val cameraGround: CameraGround = ground
            override fun readSubject(name: String, out: StageSubjectState): Boolean {
                out.found = true
                out.position.set(0f, 0f, 0f)
                out.boundsMin.set(-0.5f, 0f, -0.5f)
                out.boundsMax.set(0.5f, 1.8f, 0.5f)
                out.forward.set(0f, 0f, -1f)
                out.velocity.set(1f, 0f, 0f)
                return true
            }

            override fun groundHeight(x: Float, z: Float): Float = 0f
        }

        repeat(WARMUP_FRAMES) {
            player.step(FRAME_DELTA, 16f / 9f, stage, pose, atmo)
        }

        val reading = readAllocation(MEASURED_FRAMES) {
            repeat(MEASURED_FRAMES) {
                player.step(FRAME_DELTA, 16f / 9f, stage, pose, atmo)
            }
        }
        println("DirectorPlayerAllocation: $reading")
        assertAllocatesNothing(reading, "DirectorPlayer.step()")
        assertEquals(
            0L,
            reading.medianBytes,
            "Steady-state DirectorPlayer.step() must allocate 0 bytes over $MEASURED_FRAMES frames: $reading",
        )
    }

    private companion object {
        const val WARMUP_FRAMES = 10_000
        const val MEASURED_FRAMES = 600
        const val FRAME_DELTA = 1f / 60f
    }
}
