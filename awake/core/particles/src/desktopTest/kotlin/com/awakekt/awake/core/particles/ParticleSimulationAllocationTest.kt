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
import kotlin.test.assertTrue

/**
 * Desktop-only: `currentThreadAllocatedBytes` has no wasm or Native equivalent.
 */
class ParticleSimulationAllocationTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    /**
     * Both hooks are set: a [ParticleGroundHeight] asked for every moving particle and a
     * [ParticleDeathListener] told of every death, so their calls are measured too.
     */
    @Test
    fun steadyStateStepAndBurstEmitAllocateZeroBytes() {
        var groundQueries = 0
        var deaths = 0
        val groundHeight = ParticleGroundHeight { x, _ ->
            groundQueries += 1
            -GROUND_DEPTH + x * GROUND_SLOPE
        }
        val deathListener = ParticleDeathListener { _, _, _ -> deaths += 1 }
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
            ground.groundHeightProvider = groundHeight
            ground.restitution = 0.4f
        }

        fun step() {
            sim.emit(BURST_SIZE)
            sim.step(FRAME_DELTA, onParticleDeath = deathListener)
        }
        repeat(WARMUP_STEPS) { step() }
        // Each window is checked whole, never divided by its steps. The median window is the gate, as
        // in the other allocation probes: a JIT deoptimisation can materialise a few hundred bytes in
        // one window while the code settles, but a per-step allocation fills every window.
        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val queriesBefore = groundQueries
            val deathsBefore = deaths
            val before = threadBean.currentThreadAllocatedBytes
            repeat(MEASURED_STEPS) { step() }
            windows[window] = threadBean.currentThreadAllocatedBytes - before
            assertTrue(groundQueries > queriesBefore, "the ground height hook must run in every measured window")
            assertTrue(deaths > deathsBefore, "the death hook must run in every measured window")
        }
        windows.sort()
        println("ParticleSimulationAllocation: ${windows.joinToString()} bytes per $MEASURED_STEPS steps")
        assertEquals(
            0L,
            windows[MEASURED_WINDOWS / 2],
            "ParticleSimulation.emit() + step() with both hooks must allocate 0 bytes over $MEASURED_STEPS steps",
        )
    }

    private companion object {
        const val POOL_CAPACITY = 512
        const val BURST_SIZE = 16
        const val WARMUP_STEPS = 2_000
        const val MEASURED_STEPS = 600
        const val MEASURED_WINDOWS = 5
        const val FRAME_DELTA = 1f / 60f
        const val GROUND_DEPTH = 40f
        const val GROUND_SLOPE = 0.01f
    }
}
