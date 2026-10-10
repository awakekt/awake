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
import com.awakekt.awake.core.math.squaredDistanceFrom
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.ParticleExtraFields
import com.awakekt.awake.render.passes.uniforms.ParticleExtraUniformLayout
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ParticleDrawBuilderTest {
    private val lens = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)

    @Test
    fun worldTraversalProducesOneDrawPerEmitterWithLiveParticles() {
        val world = World()
        val emitter = liveEmitter(ParticleFacing.Camera)
        world.add(world.create(), emitter)

        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, EmitterPlacement.None, lens, 1f)

        assertEquals(1, draws.size)
        assertEquals(1, draws.single().instanceModels?.size)
        assertEquals(false, draws.single().additive)

        emitter.visual = ParticleVisual(additive = true)
        draws.clear()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, EmitterPlacement.None, lens, 1f)
        assertEquals(true, draws.single().additive, "an additive emitter's draw adds")
    }

    /**
     * A particle draw blends and writes no depth, so it records with the transparent draws, after
     * every opaque one, and sorts far to near by where its particles are rather than the origin.
     */
    @Test
    fun aParticleDrawIsTransparentAndSortsWhereItsParticlesAre() {
        val world = World()
        world.add(world.create(), liveEmitter(ParticleFacing.Camera, at = Vec3f(3f, 0f, 0f)))

        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, EmitterPlacement.None, lens, 1f)

        assertTrue(draws.single().transparent)
        assertEquals(34f, draws.single().model.squaredDistanceFrom(lens.eye), "3 across and 5 back from the eye")
    }

    @Test
    fun anEmitterWithNoLiveParticlesDrawsNothing() {
        val world = World()
        world.add(world.create(), liveEmitter(ParticleFacing.Camera, spawned = false))

        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, EmitterPlacement.None, lens, 1f)

        assertTrue(draws.isEmpty())
    }

    @Test
    fun anEmittersChildrenAreDrawnWithIt() {
        val world = World()
        val parent = liveEmitter(ParticleFacing.Camera, children = listOf(liveEmitter(ParticleFacing.Camera)))
        world.add(world.create(), parent)

        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, EmitterPlacement.None, lens, 1f)

        assertEquals(2, draws.size, "the parent's draw and its child's")
    }

    /**
     * A flat emitter's draw carries its entity's plane as the quad axes the particle shader reads:
     * the entity's +X and -Z, unscaled, or the world's without a placement. A camera-facing emitter
     * keeps the camera's right and up.
     */
    @Test
    fun aFlatEmitterDrawsAlongItsEntitysPlaneAndACameraFacingOneAlongTheCamera() {
        val world = World()
        val ground = liveEmitter(ParticleFacing.Flat)
        val tipped = liveEmitter(ParticleFacing.Flat)
        val facing = liveEmitter(ParticleFacing.Camera)
        world.add(world.create(), ground)
        world.add(world.create(), facing)
        world.create().also {
            // Turned a quarter about Z and scaled by 2: +X becomes +Y, +Y becomes -X, +Z stays.
            val placed = Mat4().apply {
                m00 = 0f
                m10 = 2f
                m01 = -2f
                m11 = 0f
                m22 = 2f
            }
            world.add(it, Placed(worldMatrix = placed))
            world.add(it, tipped)
        }

        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, PlacedEntities, lens, 1f)

        fun axes(emitter: ParticleEmitter): List<Float> {
            val floats = draws.single { it.material === emitter.material }.extraUniformFloats
            val start = ParticleExtraUniformLayout.offsetOf(ParticleExtraFields.CameraRight)
            return (start until start + 8).map { floats[it] + 0f }
        }
        assertEquals(listOf(1f, 0f, 0f, 0f, 0f, 0f, -1f, 0f), axes(ground), "the world's +X and -Z")
        assertEquals(listOf(0f, 1f, 0f, 0f, 0f, 0f, -1f, 0f), axes(tipped), "the entity's +X and -Z, unscaled")
        assertEquals(listOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f), axes(facing), "the camera's right and up")
    }

    /** Particles are culled with the aspect of the view they are drawn in, not a fixed 16:9. */
    @Test
    fun particlesAreCulledWithTheAspectOfTheirView() {
        // The camera's half-height at the origin: eye 5 units away, fovY 1 rad.
        val halfHeight = 5f * tan(0.5f)
        fun drawnAt(x: Float, aspect: Float): Boolean {
            val world = World()
            // A small particle, so its culling radius does not reach across either edge.
            val emitter = liveEmitter(ParticleFacing.Camera, at = Vec3f(x, 0f, 0f), scale = 0.1f)
            world.add(world.create(), emitter)
            val draws = ArrayList<RenderDrawCommand>()
            ParticleDrawBuilder().appendWorldDrawCalls(draws, world, EmitterPlacement.None, lens, aspect)
            return draws.isNotEmpty()
        }
        val wide = 21f / 9f
        val narrow = 9f / 16f

        assertTrue(
            drawnAt(halfHeight * wide * 0.95f, wide),
            "A particle just inside the edge of a 21:9 view, past where 16:9 ends, is drawn.",
        )
        assertFalse(
            drawnAt(halfHeight * narrow * 1.5f, narrow),
            "A particle outside a 9:16 view, though inside 16:9, is culled.",
        )
    }

    /** A particle's turn rides in column 2's x of its instance matrix, where the particle shader reads it. */
    @Test
    fun aParticlesRotationReachesItsInstanceMatrix() {
        val world = World()
        val turned = liveEmitter(ParticleFacing.Camera)
        turned.particles[0].rotation = 1.25f
        world.add(world.create(), turned)

        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, EmitterPlacement.None, lens, 1f)

        assertEquals(1.25f, requireNotNull(draws.single().instanceModels)[0].m02)
    }

    /** A stretched particle points along its motion, so the builder writes no turn for it. */
    @Test
    fun aStretchedParticleCarriesNoRotation() {
        val world = World()
        val streak = liveEmitter(ParticleFacing.Camera, visual = ParticleVisual(stretchWithVelocity = true))
        streak.particles[0].rotation = 1.25f
        world.add(world.create(), streak)

        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, EmitterPlacement.None, lens, 1f)

        assertEquals(0f, requireNotNull(draws.single().instanceModels)[0].m02)
    }

    @Test
    fun farParticlesAreDrawnBeforeNearOnes() {
        val world = World()
        val emitter = liveEmitter(ParticleFacing.Camera, maxParticles = 2, at = Vec3f(0f, 0f, 1f))
        emitter.spawn(Vec3f(0f, 0f, -3f))
        world.add(world.create(), emitter)

        val draws = ArrayList<RenderDrawCommand>()
        ParticleDrawBuilder().appendWorldDrawCalls(draws, world, EmitterPlacement.None, lens, 1f)

        val models = requireNotNull(draws.single().instanceModels)
        assertEquals(2, models.size)
        assertTrue(models[0].m23 < models[1].m23, "the particle at z = -3 (farther from the eye at z = 5) comes first")
    }

    /** An emitter with one live particle at [at] (none when not [spawned]); `spawnRate = 0f`, so nothing else spawns. */
    private fun liveEmitter(
        facing: ParticleFacing,
        maxParticles: Int = 1,
        at: Vec3f = Vec3f.ZERO,
        scale: Float = 1f,
        children: List<ParticleEmitter> = emptyList(),
        spawned: Boolean = true,
        visual: ParticleVisual = ParticleVisual(facing = facing),
    ) = ParticleEmitter(
        mesh = fakeMesh(),
        material = fakeMaterial(),
        origin = Vec3f.ZERO,
        maxParticles = maxParticles,
        spawnRate = 0f,
        lifetime = 1f,
        startAlpha = 1f,
        scale = scale,
        visual = visual,
        children = children,
    ).apply { if (spawned) spawn(at) }

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
