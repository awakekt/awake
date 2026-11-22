/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.pbrMaterialFloats
import com.awakekt.awake.render.passes.uniforms.skinnedMaterialFloats
import com.awakekt.awake.render.renderer.Renderer
import com.awakekt.awake.render.renderer.SkinnedUniformLayout
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies skinned-mesh Cook-Torrance PBR (metallic, roughness, occlusion) and environment
 * reflection (`reflectance`) across both static textured and skinned textured pipelines on
 * Vulkan and WebGPU.
 */
class SkinnedPbrAndReflectParityTest {

    @Test
    fun vulkanSkinnedPbrAndReflectWork() = verify(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuSkinnedPbrAndReflectWork() = verify(HeadlessUiBackend.WebGpu)

    private fun verify(backend: HeadlessUiBackend) = runBlocking {
        open(backend).use { session ->
            val renderer = session.renderer

            // 1. Skinned textured PBR: smooth metal reflects much brighter than rough matte dark surface.
            val skinnedMatte = renderQuad(renderer, skinned = true, metallic = 0f, roughness = 1f, reflectance = 0f)
            val skinnedReflect = renderQuad(renderer, skinned = true, metallic = 0f, roughness = 0.1f, reflectance = 0.9f)
            val skinnedMetal = renderQuad(renderer, skinned = true, metallic = 1f, roughness = 0.1f, reflectance = 0f)
            val skinnedOccluded = renderQuad(renderer, skinned = true, metallic = 0f, roughness = 1f, reflectance = 0f, occlusion = DARK_OCCLUSION)

            println("$backend skinned: matte=$skinnedMatte reflect=$skinnedReflect metal=$skinnedMetal occluded=$skinnedOccluded")
            assertTrue(
                skinnedReflect.max() > skinnedMatte.max() + BRIGHT_MARGIN,
                "$backend: reflectance must brighten skinned mesh: matte=$skinnedMatte reflect=$skinnedReflect",
            )
            assertTrue(
                skinnedMetal.max() > skinnedMatte.max() + BRIGHT_MARGIN,
                "$backend: smooth metallic skinned PBR must produce specular/env reflection: matte=$skinnedMatte metal=$skinnedMetal",
            )
            assertTrue(
                skinnedOccluded.max() < skinnedMatte.max() - OCCLUSION_MARGIN,
                "$backend: occlusion map must darken skinned ambient: matte=$skinnedMatte occluded=$skinnedOccluded",
            )

            // 2. Static textured reflection: reflectance brightens a smooth dielectric noticeably.
            val staticMatte = renderQuad(renderer, skinned = false, metallic = 0f, roughness = 1f, reflectance = 0f)
            val staticReflect = renderQuad(renderer, skinned = false, metallic = 0f, roughness = 0.1f, reflectance = 0.9f)
            println("$backend static: matte=$staticMatte reflect=$staticReflect")
            assertTrue(
                staticReflect.max() > staticMatte.max() + BRIGHT_MARGIN,
                "$backend: reflectance must brighten static mesh: matte=$staticMatte reflect=$staticReflect",
            )
        }
    }

    private suspend fun renderQuad(
        renderer: Renderer,
        skinned: Boolean,
        metallic: Float,
        roughness: Float,
        reflectance: Float,
        occlusion: TextureAsset? = null,
    ): List<Int> {
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(quad(skinned))
        val pbr = PbrTextureSet(occlusion = occlusion)
        val material = renderer.createMaterial(
            texture = DARK_BASE,
            uniformFloatCount = if (skinned) SkinnedUniformLayout.total else MaterialUniformLayouts.PbrTextured.total,
            pbrTextures = pbr,
        )
        val extras = if (skinned) {
            skinnedMaterialFloats(
                palette = IDENTITY_JOINT,
                baseColorFactor = Color.White,
                emissiveFactor = Color.Transparent,
                unlit = false,
                metallic = metallic,
                roughness = roughness,
                reflectance = reflectance,
            )
        } else {
            pbrMaterialFloats(
                metallic = metallic,
                roughness = roughness,
                baseColorFactor = Color.White,
                emissiveFactor = Color.Transparent,
                unlit = false,
                reflectance = reflectance,
            )
        }
        return try {
            renderer.renderToTexture(target, compile(renderer, RenderDrawCommand(mesh, material, extraUniformFloats = extras)))
            val pixels = renderer.readPixels(target).data
            val base = ((SIZE / 2) * SIZE + SIZE / 2) * 4
            (0..2).map { pixels[base + it].toInt() and 0xFF }
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private companion object {
        const val SIZE = 64
        const val HALF = 6f
        const val BRIGHT_MARGIN = 25
        const val OCCLUSION_MARGIN = 5
        const val BOOT_TIMEOUT_MS = 30_000L
        val IDENTITY_JOINT = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)

        // Low-albedo dark grey base so specular + environment reflection contrast cleanly.
        val DARK_BASE = solid(70)
        val DARK_OCCLUSION = solid(20)
        val LIGHT = SceneLight(direction = Vec3f(0f, 6f, -9f), color = Vec3f(0.8f, 0.8f, 0.8f), ambient = 0.25f)

        fun solid(v: Int) = TextureAsset(
            data = ByteArray(16 * 16 * 4) { idx -> if (idx % 4 == 3) 255.toByte() else v.toByte() },
            width = 16,
            height = 16,
        )

        fun quad(skinned: Boolean): MeshGeometry {
            val corners = listOf(-HALF to -HALF, HALF to -HALF, HALF to HALF, -HALF to HALF)
            val uvs = listOf(0f to 0f, 1f to 0f, 1f to 1f, 0f to 1f)
            val vertices = corners.zip(uvs).flatMap { (c, uv) ->
                listOf(c.first, 0f, c.second, 0f, 1f, 0f, 1f, 1f, 1f, uv.first, uv.second) +
                    if (skinned) listOf(0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f) else emptyList()
            }
            return MeshGeometry(
                vertices.toFloatArray(),
                intArrayOf(0, 1, 2, 2, 3, 0),
                if (skinned) VertexFormat.PositionNormalColorUvSkin else VertexFormat.PositionNormalColorUv,
            )
        }

        fun compile(renderer: Renderer, draw: RenderDrawCommand) = ScenePassCompiler.compile(
            lens = Lens(eye = Vec3f(0f, 6f, 9f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f),
            drawCalls = listOf(draw),
            light = LIGHT,
            environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false, exposure = 1f, fogColor = Color.Black, fogDensity = 0f),
            clipSpace = renderer.clipSpace,
            aspect = 1f,
            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
        )

        suspend fun open(backend: HeadlessUiBackend): HeadlessRenderSession {
            val plan = EngineShowcaseRenderPlan.copy(contentFeatures = emptyList())
            if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(plan, surface = HeadlessSurface(SIZE, SIZE))
            val ready = CompletableDeferred<Renderer>()
            val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
            val engine = VulkanEngine(app, plan)
            engine.create(HeadlessSurface(SIZE, SIZE))
            val renderer = withTimeout(BOOT_TIMEOUT_MS) { ready.await() }
            return object : HeadlessRenderSession {
                override val renderer = renderer
                override fun close() = engine.dispose()
            }
        }
    }
}
