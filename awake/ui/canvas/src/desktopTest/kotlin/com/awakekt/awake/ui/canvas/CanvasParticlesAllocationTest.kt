/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.awakekt.awake.core.particles.ParticleDeathListener
import com.awakekt.awake.core.particles.ParticleSpawnRate
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop steady-state and burst allocation gate for [CanvasParticles] and [CanvasBurstPool], with
 * both hooks set. Drawing is not gated: it draws a texture with a tint per particle (see #1103).
 */
class CanvasParticlesAllocationTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun steadyStateStepEmitAndCanvasBurstPoolSpawnAllocateZeroBytes() {
        var deaths = 0L
        var rateAsks = 0L
        val countDeaths = ParticleDeathListener { _, _, _ -> deaths += 1 }
        val steadyRate = ParticleSpawnRate { 180f.also { rateAsks += 1 } }
        val bar = CanvasElement().apply {
            kind = CanvasElementKind.Bar
            width = 200f
            height = 20f
            particles = CanvasParticles(maxParticles = 256, random = Random(11)).apply {
                spawnRate = 180f
                lifetime = 0.2f
                maxLifetime = 0.45f
                size = 6f
                endSize = 14f
                alphaCurve = CanvasParticleAlphaCurve(fadeInEnd = 0.1f, fadeOutStart = 0.7f)
                shape = CanvasParticleShape(kind = CanvasParticleShapeKind.ElementEdge)
                speed = 32f
                maxSpeed = 96f
                spreadDegrees = 120f
                gravityY = 60f
                drag = 0.25f
                turbulence = 8f
                spin = CanvasParticleSpin(-120f, 120f, randomStartAngle = true)
                onParticleDeath = countDeaths
                dynamicSpawnRate = steadyRate
            }
        }
        val emitter = checkNotNull(bar.particles)
        val pool = CanvasBurstPool(maxSlots = 8, slotMaxParticles = 16) { Random(it + 20) }

        fun frame(step: Int) {
            emitter.emit(4)
            if (step % 3 == 0) {
                pool.spawn(xDp = 80f, yDp = 40f, count = 6, configure = CONFIGURE_BURST)
            }
            emitter.step(FRAME_DELTA)
            pool.step(FRAME_DELTA)
        }
        repeat(WARMUP_STEPS) { frame(it) }

        // Each window is checked whole, never divided by its steps. The median window is the gate, as
        // in the other allocation probes: a JIT deoptimisation can materialise a few hundred bytes in
        // one window while the code settles, but a per-frame allocation fills every window.
        val windows = LongArray(MEASURED_WINDOWS)
        repeat(MEASURED_WINDOWS) { window ->
            val deathsBefore = deaths
            val asksBefore = rateAsks
            val before = threadBean.currentThreadAllocatedBytes
            repeat(MEASURED_STEPS) { frame(it) }
            windows[window] = threadBean.currentThreadAllocatedBytes - before
            assertTrue(deaths > deathsBefore, "the death listener must run in every measured window")
            assertTrue(rateAsks > asksBefore, "the spawn-rate provider must be asked in every measured window")
        }
        windows.sort()
        println("CanvasParticlesAllocation: ${windows.joinToString()} bytes per $MEASURED_STEPS steps")
        assertEquals(
            0L,
            windows[MEASURED_WINDOWS / 2],
            "CanvasParticles.step() + emit() + CanvasBurstPool.spawn(), with a death listener and a spawn-rate " +
                "provider, must allocate nothing over $MEASURED_STEPS steady-state steps",
        )
    }

    private companion object {
        const val WARMUP_STEPS = 10_000
        const val MEASURED_STEPS = 600
        const val MEASURED_WINDOWS = 5
        const val FRAME_DELTA = 1f / 60f
        val CONFIGURE_BURST: (CanvasParticles) -> Unit = { particles ->
            particles.lifetime = 0.06f
            particles.speed = 40f
            particles.size = 8f
        }
    }
}
