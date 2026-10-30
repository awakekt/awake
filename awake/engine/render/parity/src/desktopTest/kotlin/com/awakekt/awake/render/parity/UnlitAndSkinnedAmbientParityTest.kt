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
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.engine.platform.HeadlessSurface
import com.awakekt.awake.engine.platform.dsl.AppSpecBuilder
import com.awakekt.awake.render.command.GpuDrawPreparationSource
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.MaterialUniformLayouts
import com.awakekt.awake.render.passes.uniforms.PointLight
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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Headless cross-backend pixel verification for `pbr_material.unlit` across `skinned_textured`,
 * `skinned` and `textured`, and for `SceneLight.ambient` across `skinned`, `skinned_textured` and
 * `skinned_instanced`.
 *
 * Negative controls (measured via `--rerun` on Vulkan and WebGPU):
 * 1. Forcing the unlit branch off in `AslMeshVariantShaders` and `AslTexturedShader` caused both
 *    Vulkan and WebGPU to fail immediately on `unlit skinned_textured (overhead sun)`, rendering
 *    `[215, 165, 143]` instead of `#DDAD98` (`[221, 173, 152]`, channel 0 diff `6 > 1` from PBR
 *    Neutral's toe offset).
 * 2. Ignoring `lightColor.w` in `AslMeshVariantShaders` froze `skinned` and `skinned_textured` at
 *    their `0.35` ambient default, causing both Vulkan and WebGPU to fail on
 *    `skinned_textured ambient=0.1` (`expected [17, 9, 2], got [64, 40, 26]`, channel 0 diff
 *    `47 > 1`).
 */
class UnlitAndSkinnedAmbientParityTest {

    @Test
    fun vulkanUnlitAndSkinnedAmbientMatchReference() = verifyBackend(HeadlessUiBackend.Vulkan)

    @Test
    fun webGpuUnlitAndSkinnedAmbientMatchReference() = verifyBackend(HeadlessUiBackend.WebGpu)

    private fun verifyBackend(backend: HeadlessUiBackend) = runBlocking {
        openUnlitSession(backend).use { session ->
            val renderer = session.renderer
            verifyUnlitSkinnedTexturedMatchesTexelUnderAnyLight(backend, renderer)
            verifyUnlitSkinnedAndTexturedMultiplyStoredFactors(backend, renderer)
            verifySkinnedShadersFollowLightAmbient(backend, renderer)
        }
    }

    private suspend fun verifyUnlitSkinnedTexturedMatchesTexelUnderAnyLight(
        backend: HeadlessUiBackend,
        renderer: Renderer,
    ) {
        val extras = skinnedMaterialFloats(IDENTITY_JOINT, Color.White, Color.Transparent, unlit = true)
        val lights = listOf(
            "overhead sun" to LightingCase(SceneLight(direction = Vec3f(0f, 1f, 0f), color = Vec3f(1f, 1f, 1f)), exposure = 1f),
            "sun below" to LightingCase(SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f(1f, 1f, 1f)), exposure = 1f),
            "coloured sun + torch" to LightingCase(
                SceneLight(
                    direction = Vec3f(0.4f, 1f, 0.2f),
                    color = Vec3f(0.2f, 0.4f, 1.5f),
                    points = listOf(PointLight(position = Vec3f(0f, 1.5f, 0f), color = Vec3f(2f, 3f, 6f), range = 10f)),
                ),
                exposure = 2.5f,
            ),
            "dim dark scene" to LightingCase(
                SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f(0f, 0f, 0f), ambient = 0.05f),
                exposure = 0.25f,
            ),
        )
        for ((label, lighting) in lights) {
            val rgb = renderSkinnedTextured(renderer, SKIN_TEXEL_ASSET, extras, lighting)
            assertRgbNear(SKIN_TEXEL_RGB, rgb, ONE_LSB, "$backend unlit skinned_textured ($label)")
        }

        // Stored baseColorFactor x texture x vertexColor on skinned_textured.
        val tintedExtras = skinnedMaterialFloats(
            IDENTITY_JOINT,
            baseColorFactor = Color(0.8f, 0.5f, 0.5f, 1f),
            emissiveFactor = Color(1f, 1f, 1f, 1f),
            unlit = true,
        )
        val tintedRgb = renderSkinnedTextured(
            renderer = renderer,
            texture = solidTexture(220, 180, 140),
            extras = tintedExtras,
            lighting = LightingCase(SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f(1f, 1f, 1f)), exposure = 2f),
            vertexColor = Vec3f(0.5f, 0.75f, 1f),
        )
        assertRgbNear(intArrayOf(88, 68, 70), tintedRgb, ONE_LSB, "$backend unlit skinned_textured product")
    }

    private suspend fun verifyUnlitSkinnedAndTexturedMultiplyStoredFactors(
        backend: HeadlessUiBackend,
        renderer: Renderer,
    ) {
        val harsh = LightingCase(
            SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f(1f, 1f, 1f)),
            exposure = 2f,
            fogDensity = 0.2f,
        )
        val skinnedRgb = renderSkinnedUntextured(
            renderer = renderer,
            extras = skinnedMaterialFloats(IDENTITY_JOINT, Color(0.5f, 1f, 0.5f, 1f), Color.White, unlit = true),
            lighting = harsh,
            vertexColor = Vec3f(221f / 255f, 173f / 255f, 152f / 255f),
        )
        assertRgbNear(intArrayOf(111, 173, 76), skinnedRgb, ONE_LSB, "$backend unlit skinned")

        val texturedRgb = renderStaticTextured(
            renderer = renderer,
            texture = SKIN_TEXEL_ASSET,
            extras = pbrMaterialFloats(
                metallic = 1f,
                roughness = 0.1f,
                baseColorFactor = Color(0.5f, 1f, 0.5f, 1f),
                emissiveFactor = Color.White,
                unlit = true,
            ),
            lighting = harsh,
            vertexColor = Vec3f(1f, 0.5f, 1f),
        )
        assertRgbNear(intArrayOf(111, 87, 76), texturedRgb, ONE_LSB, "$backend unlit textured")
    }

    private suspend fun verifySkinnedShadersFollowLightAmbient(
        backend: HeadlessUiBackend,
        renderer: Renderer,
    ) {
        val ambients = listOf(null, 0.1f, 0.75f)
        for (authoredAmbient in ambients) {
            val effectiveSkinnedAmbient = authoredAmbient ?: DEFAULT_SKINNED_AMBIENT
            val effectiveInstancedAmbient = authoredAmbient ?: DEFAULT_LIT_SHADOW_AMBIENT
            val light = LightingCase(SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f(1f, 1f, 1f), ambient = authoredAmbient))
            val expectedTexel = displayReferredBytes(SKIN_TEXEL_RGB, effectiveSkinnedAmbient)
            val expectedWhite = displayReferredBytes(WHITE_RGB, effectiveSkinnedAmbient)
            val expectedInstanced = linearRadianceBytes(effectiveInstancedAmbient)

            val skinnedTexturedRgb = renderSkinnedTextured(renderer, SKIN_TEXEL_ASSET, IDENTITY_JOINT, light)
            val skinnedRgb = renderSkinnedUntextured(renderer, IDENTITY_JOINT, light, Vec3f(1f, 1f, 1f))
            val instancedRgb = renderSkinnedInstanced(renderer, light.light)

            assertRgbNear(expectedTexel, skinnedTexturedRgb, ONE_LSB, "$backend skinned_textured ambient=$authoredAmbient")
            assertRgbNear(expectedWhite, skinnedRgb, ONE_LSB, "$backend skinned ambient=$authoredAmbient")
            assertRgbNear(expectedInstanced, instancedRgb, ONE_LSB, "$backend skinned_instanced ambient=$authoredAmbient")
        }

        // Point lights scale by (1 - ambientShare): raising ambient to 0.8 leaves only 20% headroom for the torch.
        val torch = listOf(PointLight(position = Vec3f(0f, 2f, 0f), color = Vec3f(2f, 0f, 0f), range = 10f))
        val lowUnlit = renderSkinnedTextured(
            renderer,
            SolidWhite,
            IDENTITY_JOINT,
            LightingCase(SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f.ZERO, ambient = 0.2f)),
        )
        val lowTorch = renderSkinnedTextured(
            renderer,
            SolidWhite,
            IDENTITY_JOINT,
            LightingCase(SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f.ZERO, points = torch, ambient = 0.2f)),
        )
        val highUnlit = renderSkinnedTextured(
            renderer,
            SolidWhite,
            IDENTITY_JOINT,
            LightingCase(SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f.ZERO, ambient = 0.8f)),
        )
        val highTorch = renderSkinnedTextured(
            renderer,
            SolidWhite,
            IDENTITY_JOINT,
            LightingCase(SceneLight(direction = Vec3f(0f, -1f, 0f), color = Vec3f.ZERO, points = torch, ambient = 0.8f)),
        )
        val lowGain = lowTorch[0] - lowUnlit[0]
        val highGain = highTorch[0] - highUnlit[0]
        assertTrue(lowGain > highGain + TORCH_MARGIN, "$backend point-light gain must shrink with ambient: low=$lowGain, high=$highGain")
    }

    private suspend fun renderSkinnedTextured(
        renderer: Renderer,
        texture: TextureAsset,
        extras: FloatArray,
        lighting: LightingCase,
        vertexColor: Vec3f = Vec3f(1f, 1f, 1f),
    ): IntArray {
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(skinnedTexturedQuad(vertexColor))
        val material = renderer.createMaterial(SkinnedUniformLayout, texture = texture)
        return try {
            val input = compile(renderer, RenderDrawCommand(mesh, material, extraUniformFloats = extras), lighting.light, lighting.exposure, lighting.fogDensity)
            renderer.renderToTexture(target, input)
            centreRgb(renderer.readPixels(target).data)
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private suspend fun renderSkinnedUntextured(
        renderer: Renderer,
        extras: FloatArray,
        lighting: LightingCase,
        vertexColor: Vec3f,
    ): IntArray {
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(skinnedQuad(vertexColor))
        val material = renderer.createMaterial(SkinnedUniformLayout)
        return try {
            val input = compile(renderer, RenderDrawCommand(mesh, material, extraUniformFloats = extras), lighting.light, lighting.exposure, lighting.fogDensity)
            renderer.renderToTexture(target, input)
            centreRgb(renderer.readPixels(target).data)
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private suspend fun renderSkinnedInstanced(renderer: Renderer, light: SceneLight): IntArray {
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(skinnedQuad(Vec3f(1f, 1f, 1f)))
        val material = renderer.createMaterial(MaterialUniformLayouts.LitShadow)
        return try {
            val draw = RenderDrawCommand(
                mesh = mesh,
                material = material,
                instanceModels = listOf(Mat4()),
                instanceJointPalettes = listOf(IDENTITY_JOINT),
            )
            renderer.renderToTexture(target, compile(renderer, draw, light, exposure = 1f, fogDensity = 0f))
            centreRgb(renderer.readPixels(target).data)
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private suspend fun renderStaticTextured(
        renderer: Renderer,
        texture: TextureAsset,
        extras: FloatArray,
        lighting: LightingCase,
        vertexColor: Vec3f,
    ): IntArray {
        val target = renderer.createRenderTarget(SIZE, SIZE)
        val mesh = renderer.createMesh(staticTexturedQuad(vertexColor))
        val material = renderer.createMaterial(
            texture = texture,
            uniformFloatCount = MaterialUniformLayouts.PbrTextured.total,
            pbrTextures = PbrTextureSet(),
        )
        return try {
            val input = compile(renderer, RenderDrawCommand(mesh, material, extraUniformFloats = extras), lighting.light, lighting.exposure, lighting.fogDensity)
            renderer.renderToTexture(target, input)
            centreRgb(renderer.readPixels(target).data)
        } finally {
            renderer.waitIdle()
            mesh.destroy()
            material.destroy()
            target.destroy()
        }
    }

    private data class LightingCase(val light: SceneLight, val exposure: Float = 1f, val fogDensity: Float = 0f)

    companion object {
        private const val SIZE = 64
        private const val ONE_LSB = 1
        private const val TORCH_MARGIN = 15
        private const val DEFAULT_SKINNED_AMBIENT = 0.35f
        private const val DEFAULT_LIT_SHADOW_AMBIENT = 0.08f
        private val IDENTITY_JOINT = floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
        private val SKIN_TEXEL_RGB = intArrayOf(0xDD, 0xAD, 0x98)
        private val WHITE_RGB = intArrayOf(255, 255, 255)
        private val SKIN_TEXEL_ASSET = solidTexture(0xDD, 0xAD, 0x98)

        private fun solidTexture(r: Int, g: Int, b: Int): TextureAsset = TextureAsset(
            data = ByteArray(2 * 2 * 4) { index ->
                when (index % 4) {
                    0 -> r.toByte()
                    1 -> g.toByte()
                    2 -> b.toByte()
                    else -> 255.toByte()
                }
            },
            width = 2,
            height = 2,
        )

        private fun centreRgb(pixels: ByteArray): IntArray {
            val base = (SIZE / 2 * SIZE + SIZE / 2) * 4
            return intArrayOf(pixels[base].toInt() and 0xFF, pixels[base + 1].toInt() and 0xFF, pixels[base + 2].toInt() and 0xFF)
        }

        private fun assertRgbNear(expected: IntArray, actual: IntArray, tolerance: Int, label: String) {
            for (c in 0..2) {
                assertTrue(
                    abs(expected[c] - actual[c]) <= tolerance,
                    "$label expected ${expected.toList()}, got ${actual.toList()} (channel $c diff ${abs(expected[c] - actual[c])} > $tolerance)",
                )
            }
        }

        private fun displayReferredBytes(rgb: IntArray, shade: Float): IntArray =
            toneMapAndEncodeBytes(FloatArray(3) { ((rgb[it] / 255f) * shade).pow(2.2f) })

        private fun linearRadianceBytes(radiance: Float): IntArray =
            toneMapAndEncodeBytes(floatArrayOf(radiance, radiance, radiance))

        private fun toneMapAndEncodeBytes(decoded: FloatArray): IntArray {
            val low = minOf(decoded[0], decoded[1], decoded[2])
            val offset = if (low < 0.08f) low - 6.25f * low * low else 0.04f
            val shifted = FloatArray(3) { decoded[it] - offset }
            val peak = maxOf(shifted[0], shifted[1], shifted[2])
            val mapped = if (peak < 0.76f) {
                shifted
            } else {
                val d = 0.24f
                val newPeak = 1f - d * d / (peak + d - 0.76f)
                val fade = 1f - 1f / (0.15f * (peak - newPeak) + 1f)
                FloatArray(3) { shifted[it] * (newPeak / peak) * (1f - fade) + newPeak * fade }
            }
            return IntArray(3) { (mapped[it].coerceAtLeast(0f).pow(1f / 2.2f) * 255f).roundToInt().coerceIn(0, 255) }
        }

        private fun skinnedTexturedQuad(c: Vec3f) = MeshGeometry(
            floatArrayOf(
                -GROUND_HALF, 0f, -GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                GROUND_HALF, 0f, -GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                GROUND_HALF, 0f, GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 1f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                -GROUND_HALF, 0f, GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
            VertexFormat.PositionNormalColorUvSkin,
        )

        private fun skinnedQuad(c: Vec3f) = MeshGeometry(
            floatArrayOf(
                -GROUND_HALF, 0f, -GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                GROUND_HALF, 0f, -GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                GROUND_HALF, 0f, GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
                -GROUND_HALF, 0f, GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
            VertexFormat.PositionNormalColorSkin,
        )

        private fun staticTexturedQuad(c: Vec3f) = MeshGeometry(
            floatArrayOf(
                -GROUND_HALF, 0f, -GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 0f, 0f,
                GROUND_HALF, 0f, -GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 1f, 0f,
                GROUND_HALF, 0f, GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 1f, 1f,
                -GROUND_HALF, 0f, GROUND_HALF, 0f, 1f, 0f, c.x, c.y, c.z, 0f, 1f,
            ),
            intArrayOf(0, 1, 2, 2, 3, 0),
            VertexFormat.PositionNormalColorUv,
        )
    }
}

private const val SESSION_SIZE = 64
private const val BOOT_TIMEOUT_MS = 30_000L

private fun compile(
    renderer: Renderer,
    draw: RenderDrawCommand,
    light: SceneLight,
    exposure: Float,
    fogDensity: Float,
) = ScenePassCompiler.compile(
    lens = Lens(eye = Vec3f(0f, EYE_Y, EYE_Z), center = Vec3f.ZERO, fovYRadians = 1f, near = 0.1f, far = 50f),
    drawCalls = listOf(draw),
    light = light,
    environment = EnvironmentUniforms.Default.copy(
        shadowsEnabled = false,
        exposure = exposure,
        fogColor = Color.Black,
        fogDensity = fogDensity,
    ),
    clipSpace = renderer.clipSpace,
    aspect = 1f,
    drawPreparer = (renderer as GpuDrawPreparationSource).gpuDrawPreparer,
)

private suspend fun openUnlitSession(backend: HeadlessUiBackend): HeadlessRenderSession {
    val plan = EngineShowcaseRenderPlan.copy(contentFeatures = emptyList())
    if (backend == HeadlessUiBackend.WebGpu) return webGpuHeadlessPlan(plan, surface = HeadlessSurface(SESSION_SIZE, SESSION_SIZE))
    val ready = CompletableDeferred<Renderer>()
    val app = AppSpecBuilder().apply { ready { ready.complete(it) } }.build().createLifecycle()
    val engine = VulkanEngine(app, plan)
    engine.create(HeadlessSurface(SESSION_SIZE, SESSION_SIZE))
    val renderer = withTimeout(BOOT_TIMEOUT_MS) { ready.await() }
    return object : HeadlessRenderSession {
        override val renderer = renderer
        override fun close() = engine.dispose()
    }
}
