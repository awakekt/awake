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
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.animation.SkinnedPose
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.PbrMaterial
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertSame

class SkinnedTintDrawTest {
    private val identity = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

    /** A skinned part with a material is tinted by it; one without keeps its bare palette. */
    @Test
    fun aSkinnedPartTakesItsMaterialsTint() {
        val world = World()
        val hairMesh = fakeMesh()
        val bodyMesh = fakeMesh()
        val hair = world.create()
        world.add(hair, Transform())
        world.add(hair, MeshRenderer(hairMesh, fakeMaterial()))
        world.add(hair, SkinnedPose(identity))
        world.add(hair, PbrMaterial(baseColorFactor = Color(0.4f, 0.5f, 0.45f)))
        val body = world.create()
        val bodyPose = SkinnedPose(identity)
        world.add(body, Transform())
        world.add(body, MeshRenderer(bodyMesh, fakeMaterial()))
        world.add(body, bodyPose)

        val first = collect(world)
        val hairExtras = first.single { it.mesh === hairMesh }.extraUniformFloats

        assertContentEquals(skinnedMaterialFloats(identity, Color(0.4f, 0.5f, 0.45f), Color.Transparent), hairExtras)
        assertSame(bodyPose.jointPalette, first.single { it.mesh === bodyMesh }.extraUniformFloats)
        assertSame(hairExtras, collect(world).single { it.mesh === hairMesh }.extraUniformFloats, "rewritten in place each frame")
    }

    private fun collect(world: World) = SceneCullingCompiler(ClipSpace.WebGpu).let { culling ->
        val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        SceneDrawCollector(culling).collectBeforeParticles(world, culling.prepare(world, camera), elapsedTimeSeconds = 0f).toList()
    }

    private fun fakeMesh(): Mesh = object : Mesh {
        override val format = VertexFormat.PositionNormalColorUvSkin
        override val sizeBytes = 0L
        override fun destroy() = Unit
    }

    private fun fakeMaterial(): Material = object : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
