/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.DitherFade
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals

class DitherFadeDrawTest {
    @Test
    fun anEntitysDitherFadeReachesItsDrawAndOthersStayOpaque() {
        val world = World()
        val fadedMesh = fakeMesh()
        val plainMesh = fakeMesh()
        val faded = world.create()
        world.add(faded, Transform())
        world.add(faded, MeshRenderer(fadedMesh, fakeMaterial()))
        world.add(faded, DitherFade(opacity = 0.35f))
        val plain = world.create()
        world.add(plain, Transform())
        world.add(plain, MeshRenderer(plainMesh, fakeMaterial()))
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        val culling = SceneCullingCompiler(ClipSpace.WebGpu)
        val collector = SceneDrawCollector(culling)

        val draws = collector.collectBeforeParticles(world, culling.prepare(world, camera), elapsedTimeSeconds = 0f)
        assertEquals(0.35f, draws.single { it.mesh === fadedMesh }.fadeOpacity)
        assertEquals(1f, draws.single { it.mesh === plainMesh }.fadeOpacity)

        // The pooled draw it reuses next frame forgets the fade once the component is gone.
        world.remove<DitherFade>(faded)
        val next = collector.collectBeforeParticles(world, culling.prepare(world, camera), elapsedTimeSeconds = 0f)
        assertEquals(1f, next.single { it.mesh === fadedMesh }.fadeOpacity)
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColor
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
