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
import com.awakekt.awake.render.passes.uniforms.DEFAULT_BASE_COLOR_FACTOR
import com.awakekt.awake.render.passes.uniforms.DEFAULT_EMISSIVE_FACTOR
import com.awakekt.awake.render.passes.uniforms.DEFAULT_METALLIC_FACTOR
import com.awakekt.awake.render.passes.uniforms.DEFAULT_ROUGHNESS_FACTOR
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.TextureAnimation
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TextureAnimationDrawTest {
    /** A texture animation reaches the draw beside the entity's material, or with glTF's default factors when it has none. */
    @Test
    fun aTextureAnimationReachesTheDrawWithOrWithoutAMaterial() {
        val world = World()
        val fire = TextureAnimation(columns = 7, rows = 6, frameCount = 38, framesPerSecond = 45.6f)
        val materialMesh = fakeMesh()
        val aloneMesh = fakeMesh()
        val withMaterial = world.create()
        world.add(withMaterial, Transform())
        world.add(withMaterial, MeshRenderer(materialMesh, fakeMaterial()))
        world.add(withMaterial, PbrMaterial(roughness = 0.2f))
        world.add(withMaterial, fire)
        val alone = world.create()
        world.add(alone, Transform())
        world.add(alone, MeshRenderer(aloneMesh, fakeMaterial()))
        world.add(alone, fire)
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)

        val draws = SceneDrawCollector(cullingCompiler)
            .collectBeforeParticles(world, cullingCompiler.prepare(world, camera), elapsedTimeSeconds = 0f)

        assertContentEquals(
            pbrMaterialFloats(0f, 0.2f, Color.White, Color.Transparent, fire),
            draws.single { it.mesh === materialMesh }.extraUniformFloats,
        )
        assertContentEquals(
            pbrMaterialFloats(DEFAULT_METALLIC_FACTOR, DEFAULT_ROUGHNESS_FACTOR, DEFAULT_BASE_COLOR_FACTOR, DEFAULT_EMISSIVE_FACTOR, fire),
            draws.single { it.mesh === aloneMesh }.extraUniformFloats,
        )
    }

    /** A run that starts partway through the sheet keeps its first frame on the way to the draw, with or without a material. */
    @Test
    fun aRunsFirstFrameReachesTheDrawWithOrWithoutAMaterial() {
        val world = World()
        val walk = TextureAnimation(columns = 8, rows = 4, frameCount = 8, framesPerSecond = 12f, firstFrame = 16)
        val materialMesh = fakeMesh()
        val aloneMesh = fakeMesh()
        val withMaterial = world.create()
        world.add(withMaterial, Transform())
        world.add(withMaterial, MeshRenderer(materialMesh, fakeMaterial()))
        world.add(withMaterial, PbrMaterial())
        world.add(withMaterial, walk)
        val alone = world.create()
        world.add(alone, Transform())
        world.add(alone, MeshRenderer(aloneMesh, fakeMaterial()))
        world.add(alone, walk)
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)

        val draws = SceneDrawCollector(cullingCompiler)
            .collectBeforeParticles(world, cullingCompiler.prepare(world, camera), elapsedTimeSeconds = 0f)

        for (mesh in listOf(materialMesh, aloneMesh)) {
            val scroll = MaterialUniformLayouts.PbrTexturedMaterial
                .readVec4(draws.single { it.mesh === mesh }.extraUniformFloats, UniformFields.TextureScroll)
            assertEquals(16f, scroll.w)
        }
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
