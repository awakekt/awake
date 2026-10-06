/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import kotlin.math.PI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ParticleSpinTest {
    private val degrees = (PI / 180.0).toFloat()

    private fun emitter(spin: ParticleSpin?, maxParticles: Int = 1) = ParticleEmitter(
        mesh = fakeMesh(),
        material = fakeMaterial(),
        origin = Vec3f(0f, 0f, 0f),
        maxParticles = maxParticles,
        spawnRate = 100_000f,
        lifetime = 100f,
        startAlpha = 1f,
        scale = 1f,
        motion = ParticleMotion(baseVelocity = Vec3f(0f, 0f, 0f)),
        visual = ParticleVisual(spin = spin),
    )

    private fun step(emitter: ParticleEmitter, vararg seconds: Float) {
        val world = World()
        world.add(world.create(), emitter)
        val system = ParticleSystem(EmitterPlacement.None)
        seconds.forEach { system.update(world, it) }
    }

    private fun ParticleEmitter.alive() = particles.filter { it.alive }

    @Test
    fun anEmitterWithoutSpinNeverTurnsItsParticles() {
        val plain = emitter(spin = null, maxParticles = 8)

        step(plain, 0.5f, 0.5f)

        val particles = plain.alive()
        assertTrue(particles.isNotEmpty())
        assertTrue(particles.all { it.rotation == 0f && it.spinRate == 0f }, "no spin leaves every particle upright")
    }

    @Test
    fun aParticleTurnsAtTheRateItDrewForTheRestOfItsLife() {
        val turning = emitter(ParticleSpin(minDegreesPerSecond = 90f, maxDegreesPerSecond = 90f))

        step(turning, 0.001f, 0.5f)

        val particle = turning.alive().single()
        assertEquals(90f * degrees, particle.spinRate, 1e-5f)
        assertTrue(particle.rotation > 0f, "it has turned")
        assertEquals(particle.spinRate * particle.age, particle.rotation, 1e-4f, "its turn is its rate times the time it has lived")
    }

    @Test
    fun eachParticleDrawsItsOwnRateWithinTheRangeAndBothDirectionsAreUsed() {
        val spread = emitter(ParticleSpin(minDegreesPerSecond = -30f, maxDegreesPerSecond = 60f), maxParticles = 400)

        step(spread, 0.01f)

        val rates = spread.alive().map { it.spinRate / degrees }
        assertEquals(400, rates.size)
        assertTrue(rates.all { it in -30.001f..60.001f }, "every rate is inside the range, got ${rates.min()}..${rates.max()}")
        assertTrue(rates.min() < 0f && rates.max() > 0f, "a range spanning zero sends particles both ways")
        assertTrue(rates.distinct().size > 100, "particles draw their own rates rather than sharing one")
    }

    @Test
    fun aRandomStartAngleSpreadsTheStartsOverAFullTurnAndTheDefaultStartsUpright() {
        val random = emitter(ParticleSpin(0f, 0f, randomStartAngle = true), maxParticles = 400)
        val upright = emitter(ParticleSpin(0f, 0f), maxParticles = 400)

        step(random, 0.01f)
        step(upright, 0.01f)

        val angles = random.alive().map { it.rotation }
        assertTrue(angles.all { it >= 0f && it < 2 * PI.toFloat() }, "angles stay within one turn")
        assertTrue(angles.min() < PI.toFloat() / 2 && angles.max() > 3 * PI.toFloat() / 2, "they cover the circle, got ${angles.min()}..${angles.max()}")
        assertTrue(upright.alive().all { it.rotation == 0f }, "without randomStartAngle every particle starts upright")
    }

    @Test
    fun aLandedParticleStopsTurning() {
        val landed = emitter(ParticleSpin(90f, 90f))
        step(landed, 0.001f)
        val particle = landed.alive().single()
        particle.settled = true
        val before = particle.rotation

        step(landed, 0.5f)

        assertEquals(before, particle.rotation, "a settled particle holds its angle")
    }

    @Test
    fun aSlotReusedAfterADeathStartsWithoutTheOldParticlesTurn() {
        val brief = emitter(ParticleSpin(90f, 90f)).also { it.lifetime = 0.2f }
        step(brief, 0.001f, 0.5f)

        val slot = brief.particles.single()
        assertTrue(!slot.alive, "the particle has died")
        assertEquals(0f, slot.rotation, "a dead slot is fully reset")
        assertEquals(0f, slot.spinRate, "a dead slot is fully reset")
    }

    @Test
    fun aSpinMustHaveFiniteOrderedRates() {
        assertFailsWith<IllegalArgumentException> { ParticleSpin(minDegreesPerSecond = 10f, maxDegreesPerSecond = -10f) }
        assertFailsWith<IllegalArgumentException> { ParticleSpin(Float.NaN, 10f) }
        assertFailsWith<IllegalArgumentException> { ParticleSpin(0f, Float.POSITIVE_INFINITY) }
        assertEquals(ParticleSpin(5f, 5f), ParticleSpin(5f, 5f), "equal rates are a valid single speed")
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionUv
        override val sizeBytes: Long = 0
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
