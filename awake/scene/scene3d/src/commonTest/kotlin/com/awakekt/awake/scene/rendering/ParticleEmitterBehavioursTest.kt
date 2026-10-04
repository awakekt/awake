/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.particles.ParticleAlphaCurve
import com.awakekt.awake.scene.rendering.particles.ParticleBurstCycle
import com.awakekt.awake.scene.rendering.particles.ParticleEmitter
import com.awakekt.awake.scene.rendering.particles.ParticleGround
import com.awakekt.awake.scene.rendering.particles.ParticleLifecycle
import com.awakekt.awake.scene.rendering.particles.ParticleMotion
import com.awakekt.awake.scene.rendering.particles.ParticleSystem
import com.awakekt.awake.scene.rendering.particles.ParticleVisual
import com.awakekt.awake.scene.rendering.particles.currentAlpha
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The emitter behaviours added for awakekt/awake#270: constant acceleration, an alpha curve,
 * looping burst cycles and emission in the emitter entity's orientation. Pure CPU simulation, so
 * these assert on the emitter's own particle pool.
 */
class ParticleEmitterBehavioursTest {

    private fun emitter(
        maxParticles: Int = 64,
        spawnRate: Float = 0f,
        lifetime: Float = 100f,
        motion: ParticleMotion = ParticleMotion(baseVelocity = Vec3f(0f, 0f, 0f)),
        visual: ParticleVisual = ParticleVisual(),
        ground: ParticleGround = ParticleGround(),
        lifecycle: ParticleLifecycle = ParticleLifecycle(),
    ) = ParticleEmitter(
        mesh = fakeMesh(),
        material = fakeMaterial(),
        origin = Vec3f(0f, 0f, 0f),
        maxParticles = maxParticles,
        spawnRate = spawnRate,
        lifetime = lifetime,
        startAlpha = 1f,
        scale = 1f,
        motion = motion,
        visual = visual,
        ground = ground,
        lifecycle = lifecycle,
    )

    private fun worldWith(emitter: ParticleEmitter, transform: Transform? = null): Pair<World, Entity> {
        val world = World()
        val entity = world.create()
        world.add(entity, emitter)
        if (transform != null) world.add(entity, transform)
        return world to entity
    }

    private fun ParticleEmitter.alive() = particles.filter { it.alive }

    // --- acceleration

    @Test
    fun accelerationAddsToEveryLiveParticlesVelocityEachStep() {
        val gravity = emitter(
            maxParticles = 1,
            spawnRate = 1000f,
            motion = ParticleMotion(baseVelocity = Vec3f(0f, 0f, 0f), acceleration = Vec3f(0f, -10f, 0f)),
        )
        val (world, _) = worldWith(gravity)
        val system = ParticleSystem()

        system.update(world, 0.1f) // spawns, then one step of gravity
        system.update(world, 0.1f)

        val particle = gravity.alive().single()
        assertEquals(-2f, particle.velocity.y, 1e-4f, "two 0.1s steps of -10 add -2 to the velocity")
        assertEquals(0f, particle.velocity.x, 1e-6f)
        assertTrue(particle.position.y < 0f, "a falling particle moves down")
    }

    @Test
    fun noAccelerationLeavesVelocityUnchanged() {
        val plain = emitter(
            maxParticles = 1,
            spawnRate = 1000f,
            motion = ParticleMotion(baseVelocity = Vec3f(0f, 3f, 0f)),
        )
        val (world, _) = worldWith(plain)

        ParticleSystem().update(world, 0.5f)

        assertEquals(3f, plain.alive().single().velocity.y, 1e-6f)
    }

    @Test
    fun aSettledParticleIgnoresAcceleration() {
        val onGround = emitter(
            maxParticles = 1,
            spawnRate = 1000f,
            motion = ParticleMotion(baseVelocity = Vec3f(0f, -1f, 0f), acceleration = Vec3f(0f, -10f, 0f)),
            ground = ParticleGround(groundY = 0f),
        )
        val (world, _) = worldWith(onGround)
        val system = ParticleSystem()

        system.update(world, 0.1f) // lands on y = 0 and settles
        system.update(world, 0.1f)

        val particle = onGround.alive().single()
        assertTrue(particle.settled, "the particle reached the ground")
        assertEquals(0f, particle.velocity.y, 1e-6f, "gravity must not wake a settled particle")
        assertEquals(0f, particle.position.y, 1e-6f)
    }

    // --- alpha curve

    @Test
    fun theDefaultAlphaCurveIsTheLinearFade() {
        val linear = ParticleAlphaCurve()
        listOf(0f, 0.25f, 0.5f, 0.9f).forEach { t ->
            assertEquals(1f - t, linear.factorAt(t), 1e-6f, "at $t of life")
        }
        assertEquals(0f, linear.factorAt(1f), 1e-6f)
    }

    @Test
    fun anAlphaCurveFadesInHoldsThenFadesOut() {
        val curve = ParticleAlphaCurve(fadeInEnd = 0.2f, fadeOutStart = 0.6f)

        assertEquals(0f, curve.factorAt(0f), 1e-6f, "starts invisible")
        assertEquals(0.5f, curve.factorAt(0.1f), 1e-6f, "halfway through the fade in")
        assertEquals(1f, curve.factorAt(0.2f), 1e-6f)
        assertEquals(1f, curve.factorAt(0.4f), 1e-6f, "holds")
        assertEquals(1f, curve.factorAt(0.6f), 1e-6f)
        assertEquals(0.5f, curve.factorAt(0.8f), 1e-6f, "halfway through the fade out")
        assertEquals(0f, curve.factorAt(1f), 1e-6f, "ends invisible")
    }

    @Test
    fun anAlphaCurveThatHoldsToTheEndDropsOnlyAtDeath() {
        val curve = ParticleAlphaCurve(fadeInEnd = 0f, fadeOutStart = 1f)

        assertEquals(1f, curve.factorAt(0.99f), 1e-6f)
        assertEquals(0f, curve.factorAt(1f), 1e-6f)
    }

    @Test
    fun anInvalidAlphaCurveIsRejected() {
        assertFailsWith<IllegalArgumentException> { ParticleAlphaCurve(fadeInEnd = -0.1f) }
        assertFailsWith<IllegalArgumentException> { ParticleAlphaCurve(fadeOutStart = 1.1f) }
        assertFailsWith<IllegalArgumentException> { ParticleAlphaCurve(fadeInEnd = 0.7f, fadeOutStart = 0.3f) }
    }

    @Test
    fun aParticleUsesItsEmittersAlphaCurveScaledByStartAlpha() {
        val shaped = emitter(
            maxParticles = 1,
            spawnRate = 1000f,
            lifetime = 10f,
            visual = ParticleVisual(alphaCurve = ParticleAlphaCurve(fadeInEnd = 0.5f, fadeOutStart = 0.5f)),
        )
        shaped.startAlpha = 0.8f
        val (world, _) = worldWith(shaped)
        val system = ParticleSystem()

        system.update(world, 2.5f) // spawns, then ages to a quarter of its life

        val particle = shaped.alive().single()
        assertEquals(0.4f, particle.currentAlpha(shaped), 1e-4f, "half of the 0.8 start alpha, halfway in")
    }

    @Test
    fun anEmitterWithoutACurveKeepsTheLinearFade() {
        val plain = emitter(maxParticles = 1, spawnRate = 1000f, lifetime = 10f)
        plain.startAlpha = 0.8f
        val (world, _) = worldWith(plain)

        ParticleSystem().update(world, 5f)

        val particle = plain.alive().single()
        assertEquals(particle.currentAlpha(), particle.currentAlpha(plain), 1e-6f)
        assertEquals(0.4f, particle.currentAlpha(plain), 1e-4f)
    }

    // --- burst cycles

    private val pulsing = ParticleBurstCycle(
        cycleSeconds = 1f,
        activeSeconds = 0.5f,
        burstInterval = 0.25f,
        burstSize = 3,
    )

    @Test
    fun aBurstCycleSpawnsOnlyDuringTheActivePartOfEachCycle() {
        val pulses = emitter(lifecycle = ParticleLifecycle(burstCycle = pulsing))
        val (world, _) = worldWith(pulses)
        val system = ParticleSystem()

        fun runTo(seconds: Float, from: Float) {
            var t = from
            while (t < seconds - 1e-4f) {
                system.update(world, 0.05f)
                t += 0.05f
            }
        }

        runTo(0.5f, from = 0f) // bursts at 0.00 and 0.25
        assertEquals(6, pulses.spawnedTotal, "two bursts of three in the active half")

        runTo(0.95f, from = 0.5f) // the pause: nothing new
        assertEquals(6, pulses.spawnedTotal, "no bursts while the cycle is paused")

        runTo(1.5f, from = 0.95f) // the next cycle's active half
        assertEquals(12, pulses.spawnedTotal, "the cycle loops with two more bursts")
    }

    @Test
    fun aBurstCycleReplacesTheContinuousSpawnRate() {
        val pulses = emitter(spawnRate = 1000f, lifecycle = ParticleLifecycle(burstCycle = pulsing))
        val (world, _) = worldWith(pulses)

        ParticleSystem().update(world, 0.05f)

        assertEquals(3, pulses.spawnedTotal, "one burst, not 1000 particles a second")
    }

    @Test
    fun aLongFrameFiresEveryBurstItSpannedExactlyOnce() {
        val pulses = emitter(lifecycle = ParticleLifecycle(burstCycle = pulsing))
        val (world, _) = worldWith(pulses)
        val system = ParticleSystem()

        system.update(world, 0.9f) // spans both of the first cycle's bursts in one frame
        assertEquals(6, pulses.spawnedTotal)

        system.update(world, 0.05f) // the next frame must not refire them
        assertEquals(6, pulses.spawnedTotal)
    }

    @Test
    fun aBurstIntoAFullPoolSpawnsOnlyWhatFitsAndDoesNotRefireLater() {
        val tiny = emitter(maxParticles = 2, lifecycle = ParticleLifecycle(burstCycle = pulsing))
        val (world, _) = worldWith(tiny)
        val system = ParticleSystem()

        system.update(world, 0.05f)
        assertEquals(2, tiny.alive().size, "a burst of three fills a pool of two")

        tiny.particles.forEach { it.reset() } // slots free up again
        system.update(world, 0.05f)
        assertEquals(0, tiny.alive().size, "the leftover particle of the first burst is dropped, not carried")
    }

    @Test
    fun burstCountStillCapsABurstCycle() {
        val capped = emitter(lifecycle = ParticleLifecycle(burstCount = 4, burstCycle = pulsing))
        val (world, _) = worldWith(capped)
        val system = ParticleSystem()

        repeat(40) { system.update(world, 0.05f) }

        assertEquals(4, capped.spawnedTotal)
    }

    @Test
    fun theBurstCycleCountsStartAtTheEdgesOfAnActiveWindow() {
        // activeSeconds / burstInterval is exactly 4: bursts at 0, 0.25, 0.5 and 0.75, never a fifth.
        val cycle = ParticleBurstCycle(cycleSeconds = 2f, activeSeconds = 1f, burstInterval = 0.25f, burstSize = 1)
        val started = { t: Float -> cycle.burstsStartedBy(t) }

        assertEquals(0, started(0f))
        assertEquals(1, started(0.01f))
        assertEquals(2, started(0.25f))
        assertEquals(4, started(0.99f))
        assertEquals(4, started(1.0f), "the window is over: no fifth burst at its closing edge")
        assertEquals(4, started(1.99f))
        assertEquals(5, started(2.01f), "the second cycle starts a new burst")
    }

    @Test
    fun anInvalidBurstCycleIsRejected() {
        assertFailsWith<IllegalArgumentException> { ParticleBurstCycle(0f, 0.5f, 0.25f, 1) }
        assertFailsWith<IllegalArgumentException> { ParticleBurstCycle(1f, 0f, 0.25f, 1) }
        assertFailsWith<IllegalArgumentException> { ParticleBurstCycle(1f, 2f, 0.25f, 1) }
        assertFailsWith<IllegalArgumentException> { ParticleBurstCycle(1f, 0.5f, 0f, 1) }
        assertFailsWith<IllegalArgumentException> { ParticleBurstCycle(1f, 0.5f, 0.25f, 0) }
    }

    // --- orientation

    /** A quarter turn about Z: the node's local +X becomes world +Y and its local +Y becomes world -X. */
    private fun quarterTurnAboutZ() = Transform().apply {
        worldMatrix.m00 = 0f
        worldMatrix.m10 = 1f
        worldMatrix.m01 = -1f
        worldMatrix.m11 = 0f
    }

    @Test
    fun anEmitterThatInheritsOrientationFiresAlongItsTurnedAxes() {
        val turned = emitter(
            maxParticles = 1,
            spawnRate = 1000f,
            motion = ParticleMotion(baseVelocity = Vec3f(0f, 2f, 0f), inheritOrientation = true),
        )
        val (world, _) = worldWith(turned, quarterTurnAboutZ())

        ParticleSystem().update(world, 0.001f)

        val velocity = turned.alive().single().velocity
        assertEquals(-2f, velocity.x, 1e-4f, "local +Y points along world -X after the quarter turn")
        assertEquals(0f, velocity.y, 1e-4f)
        assertEquals(0f, velocity.z, 1e-4f)
    }

    @Test
    fun anEmitterThatDoesNotInheritOrientationKeepsWorldAxes() {
        val world = worldWith(
            emitter(maxParticles = 1, spawnRate = 1000f, motion = ParticleMotion(baseVelocity = Vec3f(0f, 2f, 0f))),
            quarterTurnAboutZ(),
        )
        val untouched = world.first.family<ParticleEmitter>().components().first()

        ParticleSystem().update(world.first, 0.001f)

        val velocity = untouched.alive().single().velocity
        assertEquals(0f, velocity.x, 1e-4f)
        assertEquals(2f, velocity.y, 1e-4f)
    }

    @Test
    fun theSpawnRingTurnsWithTheEmitter() {
        val ring = emitter(
            maxParticles = 32,
            spawnRate = 100000f,
            motion = ParticleMotion(baseVelocity = Vec3f(0f, 0f, 0f), spawnRadius = 2f, inheritOrientation = true),
        )
        val (world, _) = worldWith(ring, quarterTurnAboutZ())

        ParticleSystem().update(world, 0.001f)

        val spawned = ring.alive()
        assertTrue(spawned.size > 8, "enough particles to cover the ring")
        // A ring in the local XZ plane, turned a quarter about Z, lies in the world YZ plane.
        spawned.forEach { assertEquals(0f, it.position.x, 1e-3f, "x stays on the YZ plane") }
        assertTrue(spawned.any { abs(it.position.y) > 0.5f }, "the ring now reaches along world Y")
    }

    @Test
    fun anEmitterWithoutATransformHasNoOrientationToInherit() {
        val bare = emitter(
            maxParticles = 1,
            spawnRate = 1000f,
            motion = ParticleMotion(baseVelocity = Vec3f(0f, 2f, 0f), inheritOrientation = true),
        )
        val (world, _) = worldWith(bare)

        ParticleSystem().update(world, 0.001f)

        assertEquals(2f, bare.alive().single().velocity.y, 1e-4f)
    }

    @Test
    fun aScaledTransformDoesNotScaleTheLaunchSpeed() {
        val scaled = Transform().apply {
            worldMatrix.m00 = 3f
            worldMatrix.m11 = 3f
            worldMatrix.m22 = 3f
        }
        val fast = emitter(
            maxParticles = 1,
            spawnRate = 1000f,
            motion = ParticleMotion(baseVelocity = Vec3f(0f, 2f, 0f), inheritOrientation = true),
        )
        val (world, _) = worldWith(fast, scaled)

        ParticleSystem().update(world, 0.001f)

        assertEquals(2f, fast.alive().single().velocity.y, 1e-4f, "scale is divided out of the rotation")
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
