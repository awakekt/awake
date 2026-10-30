/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.canvas

import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop steady-state and burst allocation gate for [CanvasParticles] and [CanvasBurstPool].
 */
class CanvasParticlesAllocationTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun steadyStateStepEmitAndCanvasBurstPoolSpawnAllocateZeroBytes() {
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
            }
        }
        val emitter = checkNotNull(bar.particles)
        val pool = CanvasBurstPool(maxSlots = 8, slotMaxParticles = 16) { Random(it + 20) }

        repeat(WARMUP_STEPS) { step ->
            emitter.emit(4)
            if (step % 3 == 0) {
                pool.spawn(xDp = 80f, yDp = 40f, count = 6, configure = CONFIGURE_BURST)
            }
            emitter.step(FRAME_DELTA)
            pool.step(FRAME_DELTA)
        }

        val before = threadBean.currentThreadAllocatedBytes
        repeat(MEASURED_STEPS) { step ->
            emitter.emit(4)
            if (step % 3 == 0) {
                pool.spawn(xDp = 80f, yDp = 40f, count = 6, configure = CONFIGURE_BURST)
            }
            emitter.step(FRAME_DELTA)
            pool.step(FRAME_DELTA)
        }
        val after = threadBean.currentThreadAllocatedBytes

        val bytesPerStep = (after - before) / MEASURED_STEPS
        assertEquals(
            0L,
            bytesPerStep,
            "CanvasParticles.step() + emit() + CanvasBurstPool.spawn() must allocate 0 bytes/frame in steady state",
        )
    }

    private companion object {
        const val WARMUP_STEPS = 2_000
        const val MEASURED_STEPS = 600
        const val FRAME_DELTA = 1f / 60f
        val CONFIGURE_BURST: (CanvasParticles) -> Unit = { particles ->
            particles.lifetime = 0.06f
            particles.speed = 40f
            particles.size = 8f
        }
    }
}
