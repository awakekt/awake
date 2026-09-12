/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.scene.rendering.particles.ParticleEmitter
import kotlin.test.Test
import kotlin.test.assertEquals

class SceneParticleCompilerTest {
    @Test
    fun worldTraversalProducesOneDrawPerEmitterWithLiveParticles() {
        val world = World()
        val emitter = ParticleEmitter(
            mesh = fakeMesh(),
            material = fakeMaterial(),
            origin = Vec3f.ZERO,
            maxParticles = 1,
            spawnRate = 0f,
            lifetime = 1f,
            startAlpha = 1f,
            scale = 1f,
        )
        emitter.particles[0].apply {
            alive = true
            lifetime = 1f
            startAlpha = 1f
            scale = 1f
        }
        world.add(world.create(), emitter)

        val draws = ArrayList<RenderDrawCommand>()
        SceneParticleCompiler().appendWorldDrawCalls(
            destination = draws,
            world = world,
            camera = Camera(
                Lens(
                    eye = Vec3f(0f, 0f, 5f),
                    center = Vec3f.ZERO,
                    fovYRadians = 1f,
                    near = 0.1f,
                    far = 100f,
                ),
            ),
        )

        assertEquals(1, draws.size)
        assertEquals(1, draws.single().instanceModels?.size)
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
