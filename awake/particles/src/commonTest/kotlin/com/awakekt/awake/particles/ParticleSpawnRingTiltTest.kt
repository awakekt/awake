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
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ParticleMotion.orientSpawnRing] tilts a world-space emitter's spawn ring by its entity's rotation, so
 * the ring lies where the emitter's flat quads lie, while its launch velocity stays in world axes.
 *
 * Every case turns the entity a quarter about Z, so its XZ ring plane is the world's YZ plane: a tilted
 * ring has every spawn point at world `x == 0`, an untilted one at world `y == 0`.
 */
class ParticleSpawnRingTiltTest {
    @Test
    fun aTiltedRingLiesInTheEntitysTurnedPlane() {
        val spawned = spawnRing(ParticleMotion(spawnRadius = RADIUS, orientSpawnRing = true))

        assertTrue(spawned.positions.all { abs(it.x) < TOLERANCE }, "every spawn point is in the world's YZ plane")
        assertTrue(spawned.positions.any { abs(it.y) > RADIUS / 2f }, "and the ring reaches up the Y axis")
        spawned.positions.forEach { assertEquals(RADIUS, it.length(), TOLERANCE, "on the ring") }
    }

    @Test
    fun aTiltedRingStillLaunchesAlongWorldAxes() {
        val spawned = spawnRing(ParticleMotion(baseVelocity = UP, spawnRadius = RADIUS, orientSpawnRing = true))

        spawned.velocities.forEach { assertVec(UP, it, "straight up, not turned with the entity") }
    }

    @Test
    fun anUntiltedRingStaysHorizontal() {
        val spawned = spawnRing(ParticleMotion(spawnRadius = RADIUS))

        assertTrue(spawned.positions.all { abs(it.y) < TOLERANCE }, "every spawn point is on the world's ground plane")
    }

    @Test
    fun inheritOrientationTurnsTheRingAndTheVelocityAsBefore() {
        val spawned = spawnRing(ParticleMotion(baseVelocity = UP, spawnRadius = RADIUS, inheritOrientation = true))

        assertTrue(spawned.positions.all { abs(it.x) < TOLERANCE }, "the ring is turned")
        spawned.velocities.forEach { assertVec(Vec3f(-1f, 0f, 0f), it, "and so is the velocity") }
    }

    @Test
    fun aLocalEmittersRingAlreadyTurnsSoTheOptionAddsNothing() {
        val local = ParticleMotion(baseVelocity = UP, spawnRadius = RADIUS, space = ParticleSpace.Local)

        val plain = spawnRing(local)
        val tilted = spawnRing(local.copy(orientSpawnRing = true))

        assertTrue(plain.positions.all { abs(it.x) < TOLERANCE }, "a local ring is in the entity's axes already")
        plain.positions.zip(tilted.positions).forEach { (a, b) -> assertVec(a, b, "the same spawn points") }
    }

    @Test
    fun aTiltedRingsRadialSpeedStaysHorizontalByDefault() {
        val spawned = spawnRing(ParticleMotion(baseVelocity = ZERO, spawnRadius = RADIUS, radialSpeed = 3f, orientSpawnRing = true))

        spawned.velocities.forEach { assertEquals(0f, it.y, TOLERANCE, "pushed horizontally, as before") }
    }

    @Test
    fun radialInRingPlanePushesOutwardAcrossTheTiltedRing() {
        val motion = ParticleMotion(
            baseVelocity = ZERO,
            spawnRadius = RADIUS,
            radialSpeed = 3f,
            orientSpawnRing = true,
            radialInRingPlane = true,
        )
        val spawned = spawnRing(motion)

        spawned.positions.zip(spawned.velocities).forEach { (p, v) ->
            assertEquals(0f, v.x, TOLERANCE, "in the ring's YZ plane")
            assertVec(Vec3f(p.x * 1.5f, p.y * 1.5f, p.z * 1.5f), v, "outward through the spawn point")
        }
    }

    private class Spawned(val positions: List<Vec3f>, val velocities: List<Vec3f>)

    // One short step of an emitter on an entity turned a quarter about Z; the same seed each run.
    private fun spawnRing(motion: ParticleMotion): Spawned {
        val placed = Placed()
        placed.worldMatrix.apply {
            m00 = 0f
            m10 = 1f
            m01 = -1f
            m11 = 0f
        }
        val emitter = ParticleEmitter(
            mesh = fakeMesh(),
            material = fakeMaterial(),
            origin = Vec3f(0f, 0f, 0f),
            maxParticles = COUNT,
            spawnRate = COUNT / STEP,
            lifetime = 100f,
            startAlpha = 1f,
            scale = 0.5f,
            motion = motion,
        )
        val world = World()
        val entity = world.create()
        world.add(entity, placed)
        world.add(
            entity,
            emitter.apply {
                dynamics = ParticleDynamics(followEntity = entity)
                random = Random(SEED)
            },
        )

        ParticleSystem(PlacedEntities).update(world, STEP)

        val positions = ArrayList<Vec3f>()
        val velocities = ArrayList<Vec3f>()
        emitter.forEachLiveParticle { p, v ->
            positions += p.copy()
            velocities += v.copy()
        }
        assertTrue(positions.size > COUNT / 2, "the step spawned a ring of particles")
        return Spawned(positions, velocities)
    }

    private fun Vec3f.length() = sqrt(x * x + y * y + z * z)

    private fun assertVec(expected: Vec3f, actual: Vec3f, message: String) {
        assertEquals(expected.x, actual.x, TOLERANCE, "$message (x)")
        assertEquals(expected.y, actual.y, TOLERANCE, "$message (y)")
        assertEquals(expected.z, actual.z, TOLERANCE, "$message (z)")
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

private val UP = Vec3f(0f, 1f, 0f)
private val ZERO = Vec3f(0f, 0f, 0f)
private const val RADIUS = 2f
private const val COUNT = 16
private const val STEP = 0.001f
private const val TOLERANCE = 1e-2f
private const val SEED = 1400
