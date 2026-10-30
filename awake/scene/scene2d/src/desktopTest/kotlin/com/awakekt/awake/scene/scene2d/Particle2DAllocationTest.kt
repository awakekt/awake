/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.particles.ParticleAlphaRamp
import com.awakekt.awake.core.particles.ParticleSpawnShape
import com.awakekt.awake.core.particles.ParticleSpinRange
import com.awakekt.awake.ecs.World
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Desktop-only steady-state and burst allocation gate for [Particle2DSystem] and [BurstEmitter2DPool].
 */
class Particle2DAllocationTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun steadyStateStepEmitAndBurstPoolSpawnAllocateZeroBytes() {
        val world = World()
        val transformSystem = TransformSystem()
        val particleSystem = Particle2DSystem()
        val pool = BurstEmitter2DPool(maxSlots = 16, slotMaxParticles = 16) { Random(it + 10) }
        pool.prepare(world)

        val ambientEntity = world.create()
        val ambientTransform = Transform().apply {
            position.set(1f, -2f, 0f)
            rotation.z = 0.25f
        }
        val ambientEmitter = ParticleEmitter2D(maxParticles = 256, random = Random(42)).apply {
            spawnRate = 180f
            lifetime = 0.2f
            maxLifetime = 0.5f
            size = 0.2f
            endSize = 0.6f
            alphaRamp = ParticleAlphaRamp(fadeInEnd = 0.1f, fadeOutStart = 0.7f)
            spawnShape = ParticleSpawnShape.RingXY(innerRadius = 0.1f, outerRadius = 0.8f)
            speed = 1f
            maxSpeed = 3f
            spreadDegrees = 90f
            gravityY = -4f
            drag = 0.3f
            turbulence = 0.5f
            spin = ParticleSpinRange(-90f, 90f, randomStartAngle = true)
            inheritOrientation = true
        }
        world.add(ambientEntity, ambientTransform)
        world.add(ambientEntity, ambientEmitter)
        transformSystem.update(world, FRAME_DELTA)

        repeat(WARMUP_STEPS) { step ->
            ambientEmitter.emit(4)
            if (step % 4 == 0) {
                pool.spawn(world, x = 2f, y = 1f, count = 4, configure = CONFIGURE_BURST)
            }
            particleSystem.update(world, FRAME_DELTA)
        }

        val before = threadBean.currentThreadAllocatedBytes
        repeat(MEASURED_STEPS) { step ->
            ambientEmitter.emit(4)
            if (step % 4 == 0) {
                pool.spawn(world, x = 2f, y = 1f, count = 4, configure = CONFIGURE_BURST)
            }
            particleSystem.update(world, FRAME_DELTA)
        }
        val after = threadBean.currentThreadAllocatedBytes

        val bytesPerStep = (after - before) / MEASURED_STEPS
        assertEquals(
            0L,
            bytesPerStep,
            "Particle2DSystem.update() + emit() + BurstEmitter2DPool.spawn() must allocate 0 bytes/frame in steady state",
        )
    }

    private companion object {
        const val WARMUP_STEPS = 2_000
        const val MEASURED_STEPS = 600
        const val FRAME_DELTA = 1f / 60f
        val CONFIGURE_BURST: (ParticleEmitter2D) -> Unit = { emitter ->
            emitter.lifetime = 0.08f
            emitter.speed = 2f
            emitter.size = 0.25f
        }
    }
}
