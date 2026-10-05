/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.particles.ParticleEmitter
import com.awakekt.awake.particles.ParticleFacing
import com.awakekt.awake.particles.ParticleVisual
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.ParticleExtraFields
import com.awakekt.awake.render.passes.uniforms.ParticleExtraUniformLayout
import com.awakekt.awake.scene.core.transform.Transform
import kotlin.math.tan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneParticleCompilerTest {
    /**
     * The scene hands the particle library its camera and each entity's `Transform`: a flat emitter's
     * draw carries its entity's plane as the quad axes the particle shader reads, the entity's +X and
     * -Z, unscaled, or the world's without a transform. A camera-facing emitter keeps the camera's
     * right and up.
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
            world.add(it, Transform(worldMatrix = placed))
            world.add(it, tipped)
        }

        val draws = ArrayList<RenderDrawCommand>()
        SceneParticleCompiler().appendWorldDrawCalls(draws, world, Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f)), 1f)

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
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        fun drawnAt(x: Float, aspect: Float): Boolean {
            val world = World()
            // A small particle, so its culling radius does not reach across either edge.
            val emitter = liveEmitter(ParticleFacing.Camera, at = Vec3f(x, 0f, 0f), scale = 0.1f)
            world.add(world.create(), emitter)
            return SceneFeatureCollector3D(ClipSpace.WebGpu, emptyList())
                .collect(world, camera, elapsedTimeSeconds = 0f, viewportAspect = aspect)
                .particleDraws.isNotEmpty()
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

    private fun liveEmitter(facing: ParticleFacing, at: Vec3f = Vec3f.ZERO, scale: Float = 1f) = ParticleEmitter(
        mesh = fakeMesh(),
        material = fakeMaterial(),
        origin = Vec3f.ZERO,
        maxParticles = 1,
        spawnRate = 0f,
        lifetime = 1f,
        startAlpha = 1f,
        scale = scale,
        visual = ParticleVisual(facing = facing),
    ).apply { spawn(at) }

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
