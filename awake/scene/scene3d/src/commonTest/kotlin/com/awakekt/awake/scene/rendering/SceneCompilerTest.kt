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
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.mesh.Mesh
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_DISTANCE
import com.awakekt.awake.render.passes.uniforms.RenderDebugView
import com.awakekt.awake.scene.core.transform.Transform
import com.awakekt.awake.scene.rendering.debug.debugSettings
import com.awakekt.awake.scene.rendering.light.Light
import com.awakekt.awake.scene.rendering.light.SceneLightingCompiler
import com.awakekt.awake.scene.rendering.mesh.InstancedMeshRenderer
import com.awakekt.awake.scene.rendering.mesh.LodGroup
import com.awakekt.awake.scene.rendering.mesh.LodLevel
import com.awakekt.awake.scene.rendering.mesh.MeshRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class SceneCompilerTest {
    @Test
    fun theSunsAmbientReachesTheFramesLight() {
        fun ambient(light: Light): Float? {
            val world = World()
            world.add(world.create(), light)
            val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = 50f))
            return SceneLightingCompiler(ClipSpace.WebGpu).sceneLight(world, camera, viewportAspect = 1f).ambient
        }

        assertEquals(0.5f, ambient(Light(type = Light.Type.Directional, ambient = 0.5f)))
        assertNull(ambient(Light(type = Light.Type.Directional)), "an unset ambient keeps each shader's own")
    }

    @Test
    fun theLightsShadowDistanceSetsTheFarCascadeSplit() {
        fun farSplit(cameraFar: Float, light: Light): Float {
            val world = World()
            world.add(world.create(), light)
            val camera = Camera(Lens(eye = Vec3f(0f, 0f, 5f), center = Vec3f(0f, 0f, 0f), fovYRadians = 1f, near = 0.1f, far = cameraFar))
            val cascades = SceneLightingCompiler(ClipSpace.WebGpu).sceneLight(world, camera, viewportAspect = 1f).cascades
            return requireNotNull(cascades).splitDistances.last()
        }
        val sun = Light(type = Light.Type.Directional)

        assertEquals(400f, farSplit(1000f, sun.copy(shadowDistance = 400f)), 0.01f, "The authored distance is dropped.")
        assertEquals(250f, farSplit(250f, sun.copy(shadowDistance = 400f)), 0.01f, "The camera's far plane still caps it.")
        assertEquals(DEFAULT_SHADOW_DISTANCE, farSplit(1000f, sun), 0.01f, "An unset distance keeps today's reach.")
    }

    @Test
    fun lightingCompilerReadsSkyboxAndFogWithoutRendererState() {
        val world = World()
        val skyEntity = world.create()
        world.add(
            skyEntity,
            Skybox(
                enabled = false,
                horizonColor = Color.fromHex(0xCC3344),
                zenithColor = Color.fromHex(0x3355CC),
            ),
        )
        val fogEntity = world.create()
        world.add(
            fogEntity,
            Fog(
                enabled = true,
                density = 0.25f,
                color = Color.fromHex(0x33AA55),
            ),
        )

        val environment = SceneLightingCompiler(ClipSpace.WebGpu).environmentUniforms(world)

        assertFalse(environment.showSky)
        assertEquals(Color.fromHex(0xCC3344), environment.horizonColor)
        assertEquals(Color.fromHex(0x3355CC), environment.zenithColor)
        assertEquals(0.25f, environment.fogDensity)
        assertEquals(Color.fromHex(0x33AA55), environment.fogColor)
    }

    @Test
    fun lightingCompilerCarriesTheDebugViewFromTheWorldsDebugSettings() {
        val world = World()
        world.debugSettings().apply {
            renderDebugView = RenderDebugView.ShadowMap
            renderDebugLayer = 2
        }

        val environment = SceneLightingCompiler(ClipSpace.WebGpu).environmentUniforms(world)

        assertEquals(RenderDebugView.ShadowMap, environment.debugView)
        assertEquals(2, environment.debugLayer)
        assertEquals(RenderDebugView.Off, SceneLightingCompiler(ClipSpace.WebGpu).environmentUniforms(World()).debugView)
    }

    @Test
    fun cullingCompilerUsesSharedPlanesWhenNoSpatialIndexExists() {
        val world = World()
        val camera = Camera(
            Lens(
                eye = Vec3f(0f, 0f, 5f),
                center = Vec3f.ZERO,
                fovYRadians = 1f,
                near = 0.1f,
                far = 100f,
            ),
        )

        val frame = SceneCullingCompiler(ClipSpace.WebGpu).prepare(world, camera)

        assertNull(frame.visible)
        assertEquals(6, frame.planes?.size)
        assertNull(frame.occlusionViewProjection)
    }

    @Test
    fun drawCollectorKeepsMeshFamiliesInTheirExplicitFrameOrder() {
        val world = World()
        val camera = Camera(
            Lens(
                eye = Vec3f(0f, 0f, 5f),
                center = Vec3f.ZERO,
                fovYRadians = 1f,
                near = 0.1f,
                far = 100f,
            ),
        )
        val ordinaryMesh = fakeMesh()
        val instancedMesh = fakeMesh()
        val lodMesh = fakeMesh()
        val material = fakeMaterial()

        val ordinary = world.create()
        world.add(ordinary, Transform())
        world.add(ordinary, MeshRenderer(ordinaryMesh, material))

        val instanced = world.create()
        world.add(instanced, InstancedMeshRenderer(instancedMesh, material, listOf(Mat4())))

        val lod = world.create()
        world.add(lod, Transform())
        world.add(lod, LodGroup(listOf(LodLevel(lodMesh, material, maxDistance = 100f))))

        val cullingCompiler = SceneCullingCompiler(ClipSpace.WebGpu)
        val culling = cullingCompiler.prepare(world, camera)
        val collector = SceneDrawCollector(cullingCompiler)

        val beforeParticles = collector.collectBeforeParticles(world, culling, elapsedTimeSeconds = 0f)
        val afterParticles = collector.collectAfterParticles(world, culling, camera)

        assertEquals(listOf(ordinaryMesh, instancedMesh), beforeParticles.map { it.mesh })
        assertEquals(listOf(lodMesh), afterParticles.map { it.mesh })
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
