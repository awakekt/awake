/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.particles

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class ParticleSimulationAllocationTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun steadyStateStepAndBurstEmitAllocateZeroBytes() {
        val sim = ParticleSimulation(maxParticles = POOL_CAPACITY, random = Random(99)).apply {
            plane = ParticlePlane.XY
            spawnRate = 600f
            minLifetime = 0.25f
            maxLifetime = 0.75f
            initialAlpha = 0.9f
            initialScale = 4f
            hasEndScale = true
            endScale = 12f
            frameCount = 4
            frameRate = 12f
            baseVelX = 0f
            baseVelY = -80f
            hasConeHalfAngle = true
            coneHalfAngleDegrees = 45f
            radialSpeed = 20f
            accelY = 120f
            drag = 0.5f
            turbulence = 8f
            inheritOrientation = true
            orientation.setRotationZ(0.35f)
            spawnShape.setRectEdge(halfWidth = 32f, halfHeight = 12f)
            alphaRamp.set(fadeInEnd = 0.15f, fadeOutStart = 0.7f)
            spin.set(minDegreesPerSecond = -180f, maxDegreesPerSecond = 180f, randomStartAngle = true)
        }

        repeat(WARMUP_STEPS) {
            sim.emit(BURST_SIZE)
            sim.step(FRAME_DELTA)
        }

        val before = threadBean.currentThreadAllocatedBytes
        repeat(MEASURED_STEPS) {
            sim.emit(BURST_SIZE)
            sim.step(FRAME_DELTA)
        }
        val after = threadBean.currentThreadAllocatedBytes

        val bytesPerStep = (after - before) / MEASURED_STEPS
        assertEquals(
            0L,
            bytesPerStep,
            "ParticleSimulation.emit() + step() must allocate 0 bytes per frame in steady state",
        )
    }

    private companion object {
        const val POOL_CAPACITY = 512
        const val BURST_SIZE = 16
        const val WARMUP_STEPS = 2_000
        const val MEASURED_STEPS = 600
        const val FRAME_DELTA = 1f / 60f
    }
}
