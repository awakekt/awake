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
import com.awakekt.awake.ecs.Entity
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.ParticleExtraFields
import com.awakekt.awake.render.passes.uniforms.ParticleExtraUniformLayout
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [ParticleSpace.Local] keeps an emitter's particles in its entity's frame, so they move, turn and scale
 * with it, while [ParticleSpace.World] leaves them where they were emitted, as it always has.
 */
class ParticleLocalSpaceTest {
    private val lens = Lens(eye = Vec3f(0f, 0f, 20f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)

    @Test
    fun aLocalEmittersParticlesKeepTheirOffsetWhenTheEmitterMoves() {
        val (world, emitter, placed) = stillParticleAt(Vec3f(1f, 0f, 0f), ParticleSpace.Local)

        placed.moveTo(10f, 2f, 0f)
        ParticleSystem(PlacedEntities).update(world, 0f)

        assertVec(Vec3f(11f, 2f, 0f), emitter.onlyParticle(), "one along from the emitter, wherever it went")
        assertVec(Vec3f(11f, 2f, 0f), drawnAt(world), "and drawn there")
    }

    @Test
    fun aWorldEmittersParticlesStayBehindWhenTheEmitterMoves() {
        val (world, emitter, placed) = stillParticleAt(Vec3f(1f, 0f, 0f), ParticleSpace.World)

        placed.moveTo(10f, 2f, 0f)
        ParticleSystem(PlacedEntities).update(world, 0f)

        assertVec(Vec3f(1f, 0f, 0f), emitter.onlyParticle(), "where it was emitted")
        assertVec(Vec3f(1f, 0f, 0f), drawnAt(world), "and drawn there")
    }

    @Test
    fun aLocalEmittersParticlesTurnAndScaleWithIt() {
        val (world, emitter, placed) = stillParticleAt(Vec3f(1f, 0f, 0f), ParticleSpace.Local)

        placed.quarterTurnAboutZ(scale = 2f)
        ParticleSystem(PlacedEntities).update(world, 0f)
        val draw = draws(world).single().instanceModels!!.single()

        assertVec(Vec3f(0f, 2f, 0f), emitter.onlyParticle(), "local +X is world +Y after the turn, twice as far")
        assertVec(Vec3f(0f, 2f, 0f), Vec3f(draw.m03, draw.m13, draw.m23), "drawn where it now is")
        assertEquals(PARTICLE_SIZE * 2f, draw.m00, 1e-5f, "twice the size")
    }

    @Test
    fun aLocalEmitterSpawnsAtItsEntityAndFiresAlongItsAxes() {
        val placed = Placed().apply { moveTo(5f, 0f, 0f) }
        placed.quarterTurnAboutZ(scale = 1f)
        val emitter = emitter(ParticleSpace.Local, spawnRate = 1000f, velocity = Vec3f(1f, 0f, 0f))
        val world = worldWith(emitter, placed)

        ParticleSystem(PlacedEntities).update(world, 0.001f)

        assertVec(Vec3f(5f, 0f, 0f), emitter.onlyParticle(), "at the entity", tolerance = 1e-2f)
        var velocity = Vec3f(0f, 0f, 0f)
        emitter.forEachLiveParticle { _, v -> velocity = v.copy() }
        assertVec(Vec3f(0f, 1f, 0f), velocity, "local +X is world +Y")
    }

    @Test
    fun gravityStillPullsDownOnATurnedLocalEmitter() {
        val (world, emitter, placed) = stillParticleAt(Vec3f(0f, 0f, 0f), ParticleSpace.Local)
        emitter.motion = emitter.motion.copy(acceleration = Vec3f(0f, -10f, 0f))
        placed.quarterTurnAboutZ(scale = 1f)

        val system = ParticleSystem(PlacedEntities)
        system.update(world, 0.1f)
        system.update(world, 0.1f)

        var velocity = Vec3f(0f, 0f, 0f)
        emitter.forEachLiveParticle { _, v -> velocity = v.copy() }
        assertVec(Vec3f(0f, -2f, 0f), velocity, "two 0.1s steps of -10 along world Y")
    }

    @Test
    fun aLocalEmittersBillboardsStillFaceTheCamera() {
        val (world, _, placed) = stillParticleAt(Vec3f(1f, 0f, 0f), ParticleSpace.Local)
        placed.quarterTurnAboutZ(scale = 1f)

        val uniforms = draws(world).single().extraUniformFloats
        val right = ParticleExtraUniformLayout.offsetOf(ParticleExtraFields.CameraRight)

        assertVec(Vec3f(1f, 0f, 0f), Vec3f(uniforms[right], uniforms[right + 1], uniforms[right + 2]), "the camera's right")
    }

    @Test
    fun aLocalParticlesDeathIsReportedWhereItIsInTheWorld() {
        var died: Vec3f? = null
        val (world, emitter, placed) = stillParticleAt(Vec3f(1f, 0f, 0f), ParticleSpace.Local, lifetime = 0.5f)
        emitter.lifecycle = ParticleLifecycle(onParticleDeath = { _, at -> died = at.copy() })

        placed.moveTo(10f, 0f, 0f)
        ParticleSystem(PlacedEntities).update(world, 1f)

        assertVec(Vec3f(11f, 0f, 0f), died!!, "in the world, one along from the moved emitter")
    }

    // A placed entity with one still particle emitted by hand at [at], before the entity moves.
    private fun stillParticleAt(
        at: Vec3f,
        space: ParticleSpace,
        lifetime: Float = 100f,
    ): Triple<World, ParticleEmitter, Placed> {
        val placed = Placed()
        val emitter = emitter(space, lifetime = lifetime)
        val world = worldWith(emitter, placed)
        ParticleSystem(PlacedEntities).update(world, 0f)
        emitter.spawn(at)
        return Triple(world, emitter, placed)
    }

    private fun emitter(
        space: ParticleSpace,
        spawnRate: Float = 0f,
        lifetime: Float = 100f,
        velocity: Vec3f = Vec3f(0f, 0f, 0f),
    ) = ParticleEmitter(
        mesh = fakeMesh(),
        material = fakeMaterial(),
        origin = Vec3f(0f, 0f, 0f),
        maxParticles = 1,
        spawnRate = spawnRate,
        lifetime = lifetime,
        startAlpha = 1f,
        scale = PARTICLE_SIZE,
        motion = ParticleMotion(baseVelocity = velocity, space = space),
    )

    private fun worldWith(emitter: ParticleEmitter, placed: Placed): World {
        val world = World()
        val entity: Entity = world.create()
        world.add(entity, placed)
        world.add(entity, emitter.apply { dynamics = ParticleDynamics(followEntity = entity) })
        return world
    }

    private fun draws(world: World): List<RenderDrawCommand> = ArrayList<RenderDrawCommand>().also {
        ParticleDrawBuilder().appendWorldDrawCalls(it, world, PlacedEntities, lens, 1f)
    }

    private fun drawnAt(world: World): Vec3f = draws(world).single().instanceModels!!.single().let { Vec3f(it.m03, it.m13, it.m23) }

    private fun ParticleEmitter.onlyParticle(): Vec3f {
        val found = ArrayList<Vec3f>()
        forEachLiveParticle { position, _ -> found += position.copy() }
        return found.single()
    }

    private fun Placed.moveTo(x: Float, y: Float, z: Float) {
        position.set(x, y, z)
        worldMatrix.m03 = x
        worldMatrix.m13 = y
        worldMatrix.m23 = z
    }

    private fun Placed.quarterTurnAboutZ(scale: Float) = worldMatrix.setAxes(
        x = Vec3f(0f, scale, 0f),
        y = Vec3f(-scale, 0f, 0f),
        z = Vec3f(0f, 0f, scale),
    )

    private fun Mat4.setAxes(x: Vec3f, y: Vec3f, z: Vec3f) {
        m00 = x.x
        m10 = x.y
        m20 = x.z
        m01 = y.x
        m11 = y.y
        m21 = y.z
        m02 = z.x
        m12 = z.y
        m22 = z.z
    }

    private fun assertVec(expected: Vec3f, actual: Vec3f, message: String, tolerance: Float = 1e-4f) {
        assertEquals(expected.x, actual.x, tolerance, "$message (x)")
        assertEquals(expected.y, actual.y, tolerance, "$message (y)")
        assertEquals(expected.z, actual.z, tolerance, "$message (z)")
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

private const val PARTICLE_SIZE = 0.5f
