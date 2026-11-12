/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.particles

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.ParticleExtraFields
import com.awakekt.awake.render.passes.uniforms.ParticleExtraUniformLayout
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [ParticleFacing.Upright] stands each quad in its emitter entity's XY plane, texture top toward the
 * entity's +Y, while the spawn ring stays in the entity's XZ plane, so a pitched entity tilts its ring,
 * its velocity and its sprites together and the sprites stay perpendicular to the ring.
 *
 * The quad's corners are where the particle shader puts an unspun, unstretched particle: its centre
 * plus or minus half its size along the right and up axes the draw carries.
 */
class ParticleUprightFacingTest {
    @Test
    fun anUntiltedUprightQuadStandsInTheEntitysXyPlaneWithItsTopAtPlusY() {
        val (world, emitter) = emitterOn(Placed(), ParticleMotion(baseVelocity = ZERO))

        val (right, up) = quadAxes(world, emitter)

        assertVec(Vec3f(1f, 0f, 0f), right, "the entity's +X is the quad's right")
        assertVec(Vec3f(0f, 1f, 0f), up, "the entity's +Y is the texture's top")
        val corners = corners(Vec3f(0f, 0f, 0f), right, up, emitter.scale)
        corners.forEach { assertEquals(0f, it.z, TOLERANCE, "every corner in the XY plane") }
        assertEquals(emitter.scale / 2f, corners.maxOf { it.y }, TOLERANCE, "the top edge is half a size up +Y")
    }

    @Test
    fun withoutAPlacementTheQuadStandsInTheWorldsXyPlane() {
        val world = World()
        val emitter = uprightEmitter(ParticleMotion(baseVelocity = ZERO)).apply { spawn(Vec3f(0f, 0f, 0f)) }
        world.add(world.create(), emitter)

        val (right, up) = quadAxes(world, emitter, EmitterPlacement.None)

        assertVec(Vec3f(1f, 0f, 0f), right, "the world's +X")
        assertVec(Vec3f(0f, 1f, 0f), up, "the world's +Y")
    }

    @Test
    fun underAPitchedEntityTheQuadAndTheRingTiltTogetherAndStayPerpendicular() {
        val c = cos(PITCH)
        val s = sin(PITCH)
        // Pitched about +X: the entity's +Y is (0, c, s) and its +Z is (0, -s, c).
        val pitched = Placed().apply {
            worldMatrix.m11 = c
            worldMatrix.m21 = s
            worldMatrix.m12 = -s
            worldMatrix.m22 = c
        }
        val entityY = Vec3f(0f, c, s)
        val entityZ = Vec3f(0f, -s, c)
        val motion = ParticleMotion(baseVelocity = Vec3f(0f, 1f, 0f), spawnRadius = RADIUS, inheritOrientation = true)
        val (world, emitter) = emitterOn(pitched, motion)

        val (right, up) = quadAxes(world, emitter)

        assertVec(Vec3f(1f, 0f, 0f), right, "the quad's right is the entity's +X")
        assertVec(entityY, up, "the texture's top tilts to the entity's +Y")
        corners(Vec3f(0f, 0f, 0f), right, up, emitter.scale).forEach {
            assertEquals(0f, it.dot(entityZ), TOLERANCE, "every corner in the entity's XY plane")
        }
        var count = 0
        emitter.forEachLiveParticle { position, velocity ->
            count += 1
            // The step has already moved each particle up to STEP seconds along its unit velocity.
            assertEquals(0f, position.dot(entityY), STEP + TOLERANCE, "the spawn point is in the entity's XZ plane")
            assertEquals(RADIUS, position.length(), TOLERANCE, "on the ring")
            assertVec(entityY, velocity, "the velocity tilts with the entity")
            assertEquals(0f, velocity.dot(entityZ), TOLERANCE, "and runs along the standing sprite")
        }
        assertTrue(count > COUNT / 2, "the step spawned a ring of particles")
        assertEquals(0f, up.dot(entityY) - 1f, TOLERANCE, "the sprite stands along the ring's normal")
    }

    @Test
    fun aFlatQuadOnTheSameEntityStillLiesInItsXzPlane() {
        val (world, emitter) = emitterOn(Placed(), ParticleMotion(baseVelocity = ZERO), ParticleFacing.Flat)

        val (right, up) = quadAxes(world, emitter)

        assertVec(Vec3f(1f, 0f, 0f), right, "Flat keeps the entity's +X")
        assertVec(Vec3f(0f, 0f, -1f), up, "and its -Z")
    }

    /** One short step of an emitter on an entity placed by [placed], spawning a ring of particles. */
    private fun emitterOn(
        placed: Placed,
        motion: ParticleMotion,
        facing: ParticleFacing = ParticleFacing.Upright,
    ): Pair<World, ParticleEmitter> {
        val world = World()
        val entity = world.create()
        val emitter = uprightEmitter(motion, facing).apply {
            dynamics = ParticleDynamics(followEntity = entity)
            random = Random(SEED)
        }
        world.add(entity, placed)
        world.add(entity, emitter)
        ParticleSystem(PlacedEntities).update(world, STEP)
        return world to emitter
    }

    private fun uprightEmitter(motion: ParticleMotion, facing: ParticleFacing = ParticleFacing.Upright) = ParticleEmitter(
        mesh = fakeMesh(),
        material = fakeMaterial(),
        origin = Vec3f(0f, 0f, 0f),
        maxParticles = COUNT,
        spawnRate = COUNT / STEP,
        lifetime = 100f,
        startAlpha = 1f,
        scale = 0.5f,
        motion = motion,
        visual = ParticleVisual(facing = facing),
    )

    /** The quad's right and up axes [emitter]'s draw carries to the particle shader. */
    private fun quadAxes(world: World, emitter: ParticleEmitter, placement: EmitterPlacement = PlacedEntities): Pair<Vec3f, Vec3f> {
        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, placement, lens, 1f)
        val floats = draws.single { it.material === emitter.material }.extraUniformFloats
        val start = ParticleExtraUniformLayout.offsetOf(ParticleExtraFields.CameraRight)
        val right = Vec3f(floats[start], floats[start + 1], floats[start + 2])
        val up = Vec3f(floats[start + 4], floats[start + 5], floats[start + 6])
        return right to up
    }

    private fun corners(centre: Vec3f, right: Vec3f, up: Vec3f, size: Float): List<Vec3f> =
        listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f).map { (u, v) ->
            val h = size / 2f
            Vec3f(
                centre.x + right.x * u * h + up.x * v * h,
                centre.y + right.y * u * h + up.y * v * h,
                centre.z + right.z * u * h + up.z * v * h,
            )
        }

    private fun Vec3f.dot(other: Vec3f) = x * other.x + y * other.y + z * other.z

    private fun Vec3f.length() = sqrt(dot(this))

    private fun assertVec(expected: Vec3f, actual: Vec3f, message: String) {
        assertEquals(expected.x, actual.x, TOLERANCE, "$message (x)")
        assertEquals(expected.y, actual.y, TOLERANCE, "$message (y)")
        assertEquals(expected.z, actual.z, TOLERANCE, "$message (z)")
    }

    private val lens = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)

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

private val ZERO = Vec3f(0f, 0f, 0f)
private const val PITCH = 0.6f
private const val RADIUS = 1.5f
private const val COUNT = 16
private const val STEP = 0.001f
private const val TOLERANCE = 1e-3f
private const val SEED = 1402
