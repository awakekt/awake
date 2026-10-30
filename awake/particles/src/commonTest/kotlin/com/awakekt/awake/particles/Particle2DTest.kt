/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.core.particles.ParticlePlane
import com.awakekt.awake.core.particles.ParticleSimulation
import com.awakekt.awake.core.particles.ParticleSpawnShape
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.passes.sprites.SpriteDrawInput
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** [ParticleEmitter2D], [Particle2DSystem], [BurstEmitter2DPool] and [appendSpriteDraws] without a scene. */
class Particle2DTest {
    @Test
    fun aBeamEmitterMovesExactlyLikeACoreSimulationConfiguredByHandWithTheSameSeed() {
        val emitter = ParticleEmitter2D(maxParticles = 16, random = Random(21)).apply {
            spawnRate = 0f
            directionDegrees = 30f
            spreadDegrees = 0f
            speed = 2f
            gravityY = -1f
            lifetime = 5f
            spawnShape = ParticleSpawnShape.RingXY(innerRadius = 0.2f, outerRadius = 0.5f)
        }
        val radians = (30.0 * PI / 180.0).toFloat()
        val reference = ParticleSimulation(16, Random(21)).apply {
            plane = ParticlePlane.XY
            spawnRate = 0f
            minLifetime = 5f
            maxLifetime = 5f
            baseVelX = cos(radians) * 2f
            baseVelY = sin(radians) * 2f
            baseVelZ = 0f
            minSpeed = 2f
            maxSpeed = 2f
            accelY = -1f
            spawnShape.setRingXY(innerRadius = 0.2f, outerRadius = 0.5f)
        }

        assertMatchesReference(emitter, reference)
    }

    @Test
    fun aFannedEmitterWithASpeedRangeMovesExactlyLikeACoreSimulationConfiguredByHand() {
        val emitter = ParticleEmitter2D(maxParticles = 16, random = Random(8)).apply {
            spawnRate = 0f
            directionDegrees = 90f
            spreadDegrees = 60f
            speed = 1f
            maxSpeed = 3f
            drag = 0.5f
            lifetime = 5f
        }
        val reference = ParticleSimulation(16, Random(8)).apply {
            plane = ParticlePlane.XY
            spawnRate = 0f
            minLifetime = 5f
            maxLifetime = 5f
            // With a speed range the base velocity is the unit direction and the speed is drawn.
            baseVelX = cos((PI / 2.0).toFloat())
            baseVelY = 1f
            minSpeed = 1f
            maxSpeed = 3f
            hasConeHalfAngle = true
            coneHalfAngleDegrees = 30f
            drag = 0.5f
        }

        assertMatchesReference(emitter, reference)
    }

    @Test
    fun aNegativeCapacityIsRejectedAndAZeroCapacityEmitterDropsWhatItIsAskedFor() {
        assertFailsWith<IllegalArgumentException> { ParticleEmitter2D(maxParticles = -1) }
        val empty = ParticleEmitter2D(maxParticles = 0)
        assertEquals(0, empty.emit(5))
        assertEquals(5L, empty.droppedParticleCount)
    }

    @Test
    fun aPlacedEmitterSpawnsAtItsEntityAndTurnsItsLaunchByTheEntitysRotation() {
        val world = World()
        val entity = world.create()
        val emitter = ParticleEmitter2D(maxParticles = 4, random = Random(2)).apply {
            spawnRate = 0f
            directionDegrees = 0f
            spreadDegrees = 0f
            speed = 4f
            lifetime = 5f
            inheritOrientation = true
        }
        world.add(entity, emitter)
        val system = Particle2DSystem(FixedPlacement(x = 3f, y = 1f, zRadians = (PI / 2.0).toFloat()))

        system.update(world, 0f)
        emitter.emit(1)
        system.update(world, 0.5f)

        assertEquals(3f, emitter.simulation.posX[0], 1e-3f, "launched +X, turned to +Y, from x = 3")
        assertEquals(3f, emitter.simulation.posY[0], 1e-3f, "y = 1 + 4 * 0.5")
    }

    @Test
    fun aPooledBurstSpawnsWhereItWasFiredIgnoresLocalSpaceAndRecycles() {
        val world = World()
        val system = Particle2DSystem(EmitterPlacement.None)
        val pool = BurstEmitter2DPool(maxSlots = 2, slotMaxParticles = 4) { Random(it + 1) }
        val template = ParticleEmitter2D(maxParticles = 0).apply {
            speed = 0f
            lifetime = 0.25f
            localSpace = true
            sortOrder = 3
        }

        assertTrue(pool.spawn(world, x = 6f, y = -1f, template = template, count = 2, z = 0.25f))
        assertEquals(1, pool.activeSlotCount)
        system.update(world, 0.1f)

        val draws = drawAll(world)
        assertEquals(2, draws.size)
        for (draw in draws) {
            assertEquals(6f, draw.model.m03, 1e-4f)
            assertEquals(-1f, draw.model.m13, 1e-4f)
            assertEquals(0.25f, draw.model.m23, 1e-4f)
            assertEquals(3, draw.sortOrder)
        }

        system.update(world, 0.3f)
        assertEquals(0, pool.activeSlotCount, "an aged-out burst frees its slot")
        assertEquals(2, world.family<ParticleEmitter2D>().size, "pooled entities stay for reuse")
        assertTrue(pool.spawn(world, x = 0f, y = 0f, template = template))
        pool.dispose(world)
        assertEquals(0, world.family<ParticleEmitter2D>().size)
    }

    @Test
    fun drawingReusesEachSlotsMatrixAndAnUnchangedTint() {
        val emitter = ParticleEmitter2D(maxParticles = 2, random = Random(0)).apply {
            spawnRate = 0f
            speed = 1f
            lifetime = 2f
        }
        emitter.emit(1)
        val first = ArrayList<SpriteDrawInput>().also { emitter.appendSpriteDraws(it, sortLayer = 0) }.single()
        val again = ArrayList<SpriteDrawInput>().also { emitter.appendSpriteDraws(it, sortLayer = 0) }.single()
        assertSame(first.model, again.model, "a slot's model matrix is the emitter's own, rewritten in place")
        assertSame(first.tint, again.tint, "an unchanged tint is reused")

        emitter.enabled = false
        assertTrue(ArrayList<SpriteDrawInput>().also { emitter.appendSpriteDraws(it, sortLayer = 0) }.isEmpty())
    }

    @Test
    fun aLocalSpaceEmitterDrawsUnderItsParentAndAWorldSpaceOneAtTheGivenDepth() {
        val parent = Mat4().setEulerTRS(10f, 4f, 0.5f, 0f, 0f, 0f, 1f, 1f, 1f)
        val local = ParticleEmitter2D(maxParticles = 1, random = Random(0)).apply {
            spawnRate = 0f
            speed = 0f
            localSpace = true
        }
        val trailing = ParticleEmitter2D(maxParticles = 1, random = Random(0)).apply {
            spawnRate = 0f
            speed = 0f
        }
        local.emit(1)
        trailing.emit(1)

        val draws = ArrayList<SpriteDrawInput>()
        local.appendSpriteDraws(draws, sortLayer = 0, parent = parent, depth = 0.5f)
        trailing.appendSpriteDraws(draws, sortLayer = 0, parent = parent, depth = 0.5f)
        assertEquals(10f, draws[0].model.m03, 1e-4f)
        assertEquals(4f, draws[0].model.m13, 1e-4f)
        assertEquals(0f, draws[1].model.m03, 1e-4f)
        assertEquals(0.5f, draws[1].model.m23, 1e-4f)
    }

    private fun assertMatchesReference(emitter: ParticleEmitter2D, reference: ParticleSimulation) {
        val world = World()
        world.add(world.create(), emitter)
        val system = Particle2DSystem(EmitterPlacement.None)
        assertEquals(8, emitter.emit(8))
        assertEquals(8, reference.emit(8))
        repeat(STEPS) {
            system.update(world, STEP)
            reference.step(STEP)
        }
        val simulated = emitter.simulation
        assertContentEquals(reference.posX, simulated.posX)
        assertContentEquals(reference.posY, simulated.posY)
        assertContentEquals(reference.velX, simulated.velX)
        assertContentEquals(reference.velY, simulated.velY)
        assertFalse(simulated.posZ.any { it != 0f }, "a 2D emitter stays on the XY plane")
    }

    private fun drawAll(world: World): List<SpriteDrawInput> {
        val draws = ArrayList<SpriteDrawInput>()
        world.queryEach(ParticleEmitter2D::class) { _, emitter -> emitter.appendSpriteDraws(draws, sortLayer = 0) }
        return draws
    }

    /** Every entity at `(x, y)`, turned [zRadians] about `Z`. */
    private class FixedPlacement(private val x: Float, private val y: Float, zRadians: Float) : EmitterPlacement {
        private val turned = Mat4().setEulerTRS(0f, 0f, 0f, 0f, 0f, zRadians, 1f, 1f, 1f)

        override fun position(world: World, entity: Entity, into: Vec3f): Boolean {
            into.set(x, y, 0f)
            return true
        }

        override fun orientation(world: World, entity: Entity): Mat4 = turned
    }

    private companion object {
        const val STEPS = 12
        const val STEP = 1f / 30f
    }
}
