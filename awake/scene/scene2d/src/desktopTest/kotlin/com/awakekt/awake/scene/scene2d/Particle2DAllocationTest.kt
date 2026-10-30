/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.scene2d

import com.awakekt.awake.core.particles.ParticleAlphaRamp
import com.awakekt.awake.core.particles.ParticleDeathListener
import com.awakekt.awake.core.particles.ParticleSpawnShape
import com.awakekt.awake.core.particles.ParticleSpinRange
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.BurstEmitter2DPool
import com.awakekt.awake.particles.Particle2DSystem
import com.awakekt.awake.particles.ParticleEmitter2D
import com.awakekt.awake.particles.ParticleSpawnRate
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.core.transform.TransformSystem
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Desktop-only steady-state and burst allocation gate for [Particle2DSystem] and [BurstEmitter2DPool]
 * as a scene runs them, through [Transform2DPlacement]. Drawing is not gated: it builds a
 * `SpriteDrawInput` per particle, as sprites do (see `appendSpriteDraws`).
 */
class Particle2DAllocationTest {
    private val threadBean = ManagementFactory.getThreadMXBean() as ThreadMXBean

    @Test
    fun steadyStateStepEmitAndBurstPoolSpawnAllocateZeroBytes() {
        val world = World()
        val transformSystem = TransformSystem()
        val particleSystem = Particle2DSystem(Transform2DPlacement())
        val pool = BurstEmitter2DPool(maxSlots = 16, slotMaxParticles = 16) { Random(it + 10) }
        pool.prepare(world)

        var deaths = 0L
        var rateAsks = 0L
        val countDeaths = ParticleDeathListener { _, _, _ -> deaths += 1 }
        val steadyRate = ParticleSpawnRate { 180f.also { rateAsks += 1 } }
        val sparks = SceneParticleEmitter2D(speed = 2f, size = 0.25f, lifetime = 0.08f, burstCount = 4).toBurstTemplate()

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
            onParticleDeath = countDeaths
            dynamicSpawnRate = steadyRate
        }
        world.add(ambientEntity, ambientTransform)
        world.add(ambientEntity, ambientEmitter)
        transformSystem.update(world, FRAME_DELTA)

        fun frame(step: Int) {
            ambientEmitter.emit(4)
            if (step % 4 == 0) {
                pool.spawn(world, x = 2f, y = 1f, count = 4, configure = CONFIGURE_BURST)
            } else if (step % 4 == 2) {
                pool.spawn(world, x = -2f, y = 1f, template = sparks)
            }
            particleSystem.update(world, FRAME_DELTA)
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
        println("Particle2DAllocation: ${windows.joinToString()} bytes per $MEASURED_STEPS steps")
        assertEquals(
            0L,
            windows[MEASURED_WINDOWS / 2],
            "Particle2DSystem.update() + emit() + both BurstEmitter2DPool.spawn() overloads, with a death listener " +
                "and a spawn-rate provider, must allocate nothing over $MEASURED_STEPS steady-state steps",
        )
    }

    private companion object {
        const val WARMUP_STEPS = 10_000
        const val MEASURED_STEPS = 600
        const val MEASURED_WINDOWS = 5
        const val FRAME_DELTA = 1f / 60f
        val CONFIGURE_BURST: (ParticleEmitter2D) -> Unit = { emitter ->
            emitter.lifetime = 0.08f
            emitter.speed = 2f
            emitter.size = 0.25f
        }
    }
}
