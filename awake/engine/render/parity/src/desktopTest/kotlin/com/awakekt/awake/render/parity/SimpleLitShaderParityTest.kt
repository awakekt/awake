/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shaderpack.forMobileSimpleLit
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
import com.awakekt.awake.render.renderer.createMaterial
import com.awakekt.awake.render.testing.HeadlessRenderSession
import com.awakekt.awake.render.texture.PbrTextureSet
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.showcase.app.EngineShowcaseRenderPlan
import com.awakekt.awake.vulkan.application.VulkanEngine
import com.awakekt.awake.webgpu.webGpuHeadlessPlan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Headless cross-backend verification of `RenderPlan.forMobileSimpleLit()` (`TexturedSimpleLit`
 * and `SkinnedTexturedSimpleLit`) on both Vulkan and WebGPU:
 * - StandardMaterial bind groups attach without validation errors on both backends.
 * - Unlit static and skinned draws preserve exact texel color (`<= 1 LSB`).
 * - Lit static and skinned draws respond monotonically to directional sun light and match across
 *   Vulkan and WebGPU.
 */
class SimpleLitShaderParityTest {

    @Test
    fun simpleLitStaticAndSkinnedDrawsMatchAcrossVulkanAndWebGpu() = runBlocking {
        val vk = captureBackend(HeadlessUiBackend.Vulkan)
        val wg = captureBackend(HeadlessUiBackend.WebGpu)

        assertRgbNear(TEXEL_RGB, vk.unlitStatic, 1, "Vulkan SimpleLit unlit static")
        assertRgbNear(TEXEL_RGB, wg.unlitStatic, 1, "WebGPU SimpleLit unlit static")
        assertRgbNear(TEXEL_RGB, vk.unlitSkinned, 1, "Vulkan SimpleLit unlit skinned")
        assertRgbNear(TEXEL_RGB, wg.unlitSkinned, 1, "WebGPU SimpleLit unlit skinned")

        assertTrue(
            vk.litStaticOverhead[0] > vk.litStaticDark[0] + SUN_CONTRAST,
            "Vulkan SimpleLit static overhead sun (${vk.litStaticOverhead.toList()}) must brighten over dark (${vk.litStaticDark.toList()})",
        )
        assertTrue(
            vk.litSkinnedOverhead[0] > vk.litSkinnedDark[0] + SUN_CONTRAST,
            "Vulkan SimpleLit skinned overhead sun (${vk.litSkinnedOverhead.toList()}) must brighten over dark (${vk.litSkinnedDark.toList()})",
        )

        assertRgbNear(vk.litStaticOverhead, wg.litStaticOverhead, CROSS_BACKEND_TOL, "SimpleLit lit static overhead Vk vs Wg")
        assertRgbNear(vk.litStaticDark, wg.litStaticDark, CROSS_BACKEND_TOL, "SimpleLit lit static dark Vk vs Wg")
        assertRgbNear(vk.litSkinnedOverhead, wg.litSkinnedOverhead, CROSS_BACKEND_TOL, "SimpleLit lit skinned overhead Vk vs Wg")
        assertRgbNear(vk.litSkinnedDark, wg.litSkinnedDark, CROSS_BACKEND_TOL, "SimpleLit lit skinned dark Vk vs Wg")
    }

    private data class BackendReadings(
        val unlitStatic: IntArray,
        val unlitSkinned: IntArray,
        val litStaticOverhead: IntArray,
        val litStaticDark: IntArray,
        val litSkinnedOverhead: IntArray,
        val litSkinnedDark: IntArray,
    )

    private suspend fun captureBackend(backend: HeadlessUiBackend): BackendReadings =
        openSimpleLitSession(backend).use { session ->
            val renderer = session.renderer
            val sunOverhead = SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f), ambient = 0.15f)
            val sunBelow = SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f(1f, 1f, 1f), ambient = 0.15f)

            val unlitStatic = drawStatic(
                renderer,
                pbrMaterialFloats(metallic = 0f, roughness = 0.5f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent, unlit = true),
                sunBelow,
            )
            val unlitSkinned = drawSkinned(
                renderer,
                skinnedMaterialFloats(IDENTITY_JOINT, Color.White, Color.Transparent, unlit = true),
                sunBelow,
            )
            val litStaticOverhead = drawStatic(
                renderer,
                pbrMaterialFloats(metallic = 0f, roughness = 0.5f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent, unlit = false),
                sunOverhead,
            )
            val litStaticDark = drawStatic(
                renderer,
                pbrMaterialFloats(metallic = 0f, roughness = 0.5f, baseColorFactor = Color.White, emissiveFactor = Color.Transparent, unlit = false),
                sunBelow,
            )
            val litSkinnedOverhead = drawSkinned(
                renderer,
                skinnedMaterialFloats(IDENTITY_JOINT, Color.White, Color.Transparent, unlit = false),
                sunOverhead,
            )
            val litSkinnedDark = drawSkinned(
                renderer,
                skinnedMaterialFloats(IDENTITY_JOINT, Color.White, Color.Transparent, unlit = false),
                sunBelow,
            )
            BackendReadings(unlitStatic, unlitSkinned, litStaticOverhead, litStaticDark, litSkinnedOverhead, litSkinnedDark)
        }

    private suspend fun drawStatic(renderer: Renderer, extras: FloatArray, light: SceneLight): IntArray {
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(staticQuad())
        val material = renderer.createMaterial(
            texture = TEXEL_ASSET,
            uniformFloatCount = MaterialUniformLayouts.PbrTextured.total,
            pbrTextures = PbrTextureSet(),
        )
        return try {
            renderer.renderToTexture(target, compileDraw(renderer, RenderDrawCommand(mesh, material, extraUniformFloats = extras), light))
            centerRgb(renderer.readPixels(target).data)
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private suspend fun drawSkinned(renderer: Renderer, extras: FloatArray, light: SceneLight): IntArray {
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(skinnedQuad())
        val material = renderer.createMaterial(SkinnedUniformLayout, texture = TEXEL_ASSET)
        return try {
            renderer.renderToTexture(target, compileDraw(renderer, RenderDrawCommand(mesh, material, extraUniformFloats = extras), light))
            centerRgb(renderer.readPixels(target).data)
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    companion object {
        private const val SIZE = 64
        private const val HALF = 2f
        private const val CROSS_BACKEND_TOL = 2
        private const val SUN_CONTRAST = 40
        private const val BOOT_TIMEOUT_MS = 30_000L
        private val IDENTITY_JOINT = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        private val TEXEL_RGB = intArrayOf(0xD8, 0x9C, 0x74)
        private val TEXEL_ASSET = TextureAsset(
            data = ByteArray(2 * 2 * 4) { idx ->
                when (idx % 4) {
                    0 -> TEXEL_RGB[0].toByte()
                    1 -> TEXEL_RGB[1].toByte()
                    2 -> TEXEL_RGB[2].toByte()
                    else -> 255.toByte()
                }
            },
            width = 2,
            height = 2,
        )

        private fun centerRgb(pixels: ByteArray): IntArray {
            val base = (SIZE / 2 * SIZE + SIZE / 2) * 4
            return intArrayOf(pixels[base].toInt() and 0xFF, pixels[base + 1].toInt() and 0xFF, pixels[base + 2].toInt() and 0xFF)
        }

        private fun assertRgbNear(expected: IntArray, actual: IntArray, tol: Int, label: String) {
            for (c in 0..2) {
                assertTrue(
                    abs(expected[c] - actual[c]) <= tol,
                    "$label expected ${expected.toList()}, got ${actual.toList()} (channel $c diff ${abs(expected[c] - actual[c])} > $tol)",
                )
            }
        }

        private fun staticQuad() = MeshGeometry(
            floatArrayOf(
                -HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f,
                HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f,
                HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f,
                -HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
            VertexFormat.PositionNormalColorUv,
        )

        private fun skinnedQuad() = MeshGeometry(
            floatArrayOf(
                -HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                HALF, 0f, -HALF, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                -HALF, 0f, HALF, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
            VertexFormat.PositionNormalColorUvSkin,
        )

        private fun compileDraw(renderer: Renderer, draw: RenderDrawCommand, light: SceneLight) = ScenePassCompiler.compile(
            lens = Lens(eye = Vec3f(0f, 3f, 0.01f), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f),
            drawCalls = listOf(draw),
            light = light,
            environment = EnvironmentUniforms.Default.copy(shadowsEnabled = false),
            clipSpace = renderer.clipSpace,
            aspect = 1f,
            drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
        )

        private suspend fun openSimpleLitSession(backend: HeadlessUiBackend): HeadlessRenderSession {
            val plan = EngineShowcaseRenderPlan.forMobileSimpleLit().copy(contentFeatures = emptyList())
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
