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
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.camera.Camera
import com.awakekt.awake.scene.rendering.deposit.SurfaceDeposit
import com.awakekt.awake.scene.rendering.light.SceneLightingCompiler
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import com.awakekt.awake.scene.rendering.mesh.SceneDrawCollector
import com.awakekt.awake.scene.rendering.mesh.SurfaceDepositMask
import com.awakekt.awake.scene.rendering.spatial.SceneCullingCompiler
import kotlin.test.Test
import kotlin.test.assertEquals

/** Verifies [SurfaceDeposit] compiles into environment uniforms and [SurfaceDepositMask] reaches draw commands. */
class SurfaceDepositSceneTest {
    @Test
    fun surfaceDepositComponentCompilesToEnvironmentUniformsOnlyWhenEnabled() {
        val world = World()
        val compiler = SceneLightingCompiler(ClipSpace.Vulkan)
        assertEquals(0f, compiler.environmentUniforms(world).surfaceDepositAmount)

        val e = world.create()
        val deposit = SurfaceDeposit(
            enabled = false,
            color = Color(0.58f, 0.54f, 0.51f, 1f),
            amount = 0.8f,
            slopeThreshold = 0.6f,
            softness = 0.22f,
            roughness = 0.95f,
            noiseScale = 2.4f,
            secondaryColor = Color(0.20f, 0.18f, 0.17f, 1f),
            glint = 0.15f,
            grainStrength = 0.72f,
            seed = 9.5f,
        )
        world.add(e, deposit)
        assertEquals(0f, compiler.environmentUniforms(world).surfaceDepositAmount, "disabled deposit compiles to 0 amount")

        deposit.enabled = true
        val env = compiler.environmentUniforms(world)
        assertEquals(0.8f, env.surfaceDepositAmount)
        assertEquals(deposit.color, env.surfaceDepositColor)
        assertEquals(0.6f, env.surfaceDepositSlope)
        assertEquals(0.22f, env.surfaceDepositSoftness)
        assertEquals(0.95f, env.surfaceDepositRoughness)
        assertEquals(2.4f, env.surfaceDepositNoiseScale)
        assertEquals(deposit.secondaryColor, env.surfaceDepositSecondaryColor)
        assertEquals(0.15f, env.surfaceDepositGlint)
        assertEquals(0.72f, env.surfaceDepositGrain)
        assertEquals(9.5f, env.surfaceDepositSeed)
    }

    @Test
    fun surfaceDepositMaskCopiesScaleAndSeedToDrawCommandsAndDefaultsToOne() {
        val world = World()
        val normalEntity = world.create()
        world.add(normalEntity, Transform())
        world.add(normalEntity, MeshRenderer(FakeMesh, FakeMaterial))

        val maskedEntity = world.create()
        world.add(maskedEntity, Transform())
        world.add(maskedEntity, MeshRenderer(FakeMesh, FakeMaterial))
        val mask = SurfaceDepositMask(scale = 0f, seed = 7.25f)
        world.add(maskedEntity, mask)

        val camera = Camera(lens = Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 100f))
        val culling = SceneCullingCompiler(ClipSpace.Vulkan)
        val collector = SceneDrawCollector(culling)
        val first = collector.collectBeforeParticles(world, culling.prepare(world, camera), elapsedTimeSeconds = 0f)
        assertEquals(2, first.size)
        assertEquals(1f, first[0].surfaceDepositScale)
        assertEquals(0f, first[0].surfaceDepositSeed)
        assertEquals(0f, first[1].surfaceDepositScale)
        assertEquals(7.25f, first[1].surfaceDepositSeed)

        mask.scale = 0.45f
        mask.seed = 21.5f
        val second = collector.collectBeforeParticles(world, culling.prepare(world, camera), elapsedTimeSeconds = 0f)
        assertEquals(0.45f, second[1].surfaceDepositScale)
        assertEquals(21.5f, second[1].surfaceDepositSeed)
    }

    private object FakeMesh : Mesh {
        override val format: VertexFormat = VertexFormat.PositionNormalColorUv
        override val sizeBytes: Long = 0L
        override fun destroy() = Unit
    }

    private object FakeMaterial : Material {
        override fun updateUniformBuffer(uniformFloats: FloatArray) = Unit
        override fun destroy() = Unit
    }
}
