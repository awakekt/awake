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
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.TexturedBlend
import com.awakekt.awake.render.passes.uniforms.pbrTexturedMaterialFloats
import com.awakekt.awake.render.renderer.UniformFields
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals

class LitWhenAdditiveDrawTest {
    /**
     * An entity's material decides whether its additive draw stays lit, and the shared packer reads
     * it from the draw the collector builds: unlit by default, lit when the material asks, and a
     * draw that covers what is behind it is lit either way.
     */
    @Test
    fun anAdditiveDrawStaysLitOnlyWhenItsMaterialAsks() {
        val world = World()
        val glow = fakeMesh()
        val litGlow = fakeMesh()
        val litSolid = fakeMesh()
        world.create().also {
            world.add(it, Transform())
            world.add(it, MeshRenderer(glow, fakeMaterial(), transparent = true, additive = true))
        }
        world.create().also {
            world.add(it, Transform())
            world.add(it, MeshRenderer(litGlow, fakeMaterial(), transparent = true, additive = true))
            world.add(it, PbrMaterial(litWhenAdditive = true))
        }
        world.create().also {
            world.add(it, Transform())
            world.add(it, MeshRenderer(litSolid, fakeMaterial()))
            world.add(it, PbrMaterial(litWhenAdditive = true))
        }
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)

        val draws = SceneDrawCollector(cullingCompiler)
            .collectBeforeParticles(world, cullingCompiler.prepare(world, camera), elapsedTimeSeconds = 0f)

        fun blendOf(mesh: Mesh): Float = draws.single { it.mesh === mesh }.blendLane()
        assertEquals(TexturedBlend.AddsUnlit.code.toFloat(), blendOf(glow))
        assertEquals(TexturedBlend.AddsLit.code.toFloat(), blendOf(litGlow))
        assertEquals(TexturedBlend.Covers.code.toFloat(), blendOf(litSolid))
    }

    private fun RenderDrawCommand.blendLane(): Float =
        MaterialUniformLayouts.PbrTexturedMaterial.readVec4(pbrTexturedMaterialFloats(this), UniformFields.PbrFactors).w

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
