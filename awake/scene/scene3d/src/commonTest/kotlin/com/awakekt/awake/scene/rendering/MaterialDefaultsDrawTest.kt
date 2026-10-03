/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertContentEquals

class MaterialDefaultsDrawTest {
    /** An entity without a PbrMaterial draws its material's authored factors; its own PbrMaterial wins. */
    @Test
    fun aMaterialsAuthoredFactorsDrawUnlessTheEntityOverridesThem() {
        val world = World()
        val authored = PbrMaterial(metallic = 0.8f, roughness = 0.3f, baseColorFactor = Color(0.5f, 0.5f, 0.5f))
        val plainMesh = fakeMesh()
        val overriddenMesh = fakeMesh()
        val plain = world.create()
        world.add(plain, Transform())
        world.add(plain, MeshRenderer(plainMesh, fakeMaterial(), defaultMaterial = authored))
        val overridden = world.create()
        world.add(overridden, Transform())
        world.add(overridden, MeshRenderer(overriddenMesh, fakeMaterial(), defaultMaterial = authored))
        world.add(overridden, PbrMaterial(metallic = 0f, roughness = 1f))
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)

        val draws = SceneDrawCollector(cullingCompiler)
            .collectBeforeParticles(world, cullingCompiler.prepare(world, camera), elapsedTimeSeconds = 0f)

        assertContentEquals(pbrMaterialFloats(0.8f, 0.3f, Color(0.5f, 0.5f, 0.5f), Color.Transparent), draws.single { it.mesh === plainMesh }.extraUniformFloats)
        assertContentEquals(pbrMaterialFloats(0f, 1f, Color.White, Color.Transparent), draws.single { it.mesh === overriddenMesh }.extraUniformFloats)
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUv
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
