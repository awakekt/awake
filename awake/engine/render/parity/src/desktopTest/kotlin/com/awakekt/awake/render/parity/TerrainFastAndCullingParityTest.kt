/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.parity

import com.awakekt.awake.asset.shadercompiler.NagaShaderCompiler
import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderpack.pagedTerrainFastCoreShader
import com.awakekt.awake.asset.shaderpack.pagedTerrainFastShader
import com.awakekt.awake.asset.shaderpack.pagedTerrainShader
import com.awakekt.awake.asset.shaderpack.terrainFastCoreShader
import com.awakekt.awake.asset.shaderpack.terrainFastShader
import com.awakekt.awake.asset.shaderpack.terrainShader
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.kit.terrainlayers.TerrainControlMap
import com.awakekt.awake.kit.terrainlayers.TerrainLayer
import com.awakekt.awake.kit.terrainlayers.TerrainLayerPalette
import com.awakekt.awake.kit.terrainlayers.TerrainLightmap
import com.awakekt.awake.kit.terrainlayers.TerrainQuality
import com.awakekt.awake.kit.terrainlayers.packLayerArray
import com.awakekt.awake.kit.terrainlayers.pagedTerrainLayersFastCoreShader
import com.awakekt.awake.kit.terrainlayers.pagedTerrainLayersFastShader
import com.awakekt.awake.kit.terrainlayers.terrainLayersFastCoreShader
import com.awakekt.awake.kit.terrainlayers.terrainLayersFastShader
import com.awakekt.awake.kit.terrainlayers.terrainLayersShader
import com.awakekt.awake.kit.terrainlayers.terrainLayersSurface
import com.awakekt.awake.kit.terrainlayers.withTerrainQuality
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validates every `TerrainQuality.Fast` and neutral fast terrain shader through Naga to WGSL and
 * SPIR-V, confirms SPIR-V size shrinks vs Standard, and verifies live headless rendering on both
 * Vulkan and WebGPU with frustum-culled clipmap blocks.
 */
class TerrainFastAndCullingParityTest {

    private fun compileSpirv(name: String, shader: AslShaderDefinition): ByteArray {
        val wgsl = shader.emitWgsl()
        assertNull(NagaShaderCompiler.validate(wgsl), "WGSL validation failed for $name")
        val spirv = NagaShaderCompiler.wgslToSpirv(wgsl)
        assertTrue(spirv.isNotEmpty() && spirv.size % 4 == 0, "$name compiled to invalid SPIR-V (${spirv.size} B)")
        return spirv
    }

    @Test
    fun allFastTerrainShadersValidateCompileAndShrinkSpirvVsStandard() {
        for (clip in listOf(ClipSpace.Vulkan, ClipSpace.WebGpu)) {
            val stdLayers = compileSpirv("stdLayers[$clip]", terrainLayersShader(clip))
            val fastLayers = compileSpirv("fastLayers[$clip]", terrainLayersFastShader(clip))
            compileSpirv("fastLayersCore[$clip]", terrainLayersFastCoreShader(clip))
            compileSpirv("fastLayersDeposit[$clip]", terrainLayersFastShader(clip, includeSurfaceDeposit = true))
            compileSpirv("pagedFastLayers[$clip]", pagedTerrainLayersFastShader(clip))
            compileSpirv("pagedFastLayersCore[$clip]", pagedTerrainLayersFastCoreShader(clip))

            val stdNeutral = compileSpirv("stdNeutral[$clip]", terrainShader(clip))
            val fastNeutral = compileSpirv("fastNeutral[$clip]", terrainFastShader(clip))
            compileSpirv("fastNeutralCore[$clip]", terrainFastCoreShader(clip))
            compileSpirv("pagedStdNeutral[$clip]", pagedTerrainShader(clip))
            val pagedFastNeutral = compileSpirv("pagedFastNeutral[$clip]", pagedTerrainFastShader(clip))
            compileSpirv("pagedFastNeutralCore[$clip]", pagedTerrainFastCoreShader(clip))

            assertTrue(
                fastLayers.size < stdLayers.size,
                "fastLayers[$clip] (${fastLayers.size} B) must be smaller than stdLayers (${stdLayers.size} B)",
            )
            assertTrue(
                fastNeutral.size < stdNeutral.size,
                "fastNeutral[$clip] (${fastNeutral.size} B) must be smaller than stdNeutral (${stdNeutral.size} B)",
            )
            assertTrue(pagedFastNeutral.isNotEmpty())
        }
    }

    @Test
    fun fastTerrainLayersRendersConsistentOutputAcrossVulkanAndWebGpuWithBlockCulling() {
        val standardSurface = buildTwoLayerTestSurface()
        val fastSurface = standardSurface.withTerrainQuality(TerrainQuality.Fast)

        val fastImages = listOf(HeadlessUiBackend.Vulkan, HeadlessUiBackend.WebGpu).map { backend ->
            openHeadlessScene(backend).use { session ->
                val std = session.renderer.renderTerrainCliffScene(
                    shaders = standardSurface.shaders,
                    surfaceTextures = standardSurface.textures,
                    sun = SUN,
                )
                val fast = session.renderer.renderTerrainCliffScene(
                    shaders = fastSurface.shaders,
                    surfaceTextures = fastSurface.textures,
                    sun = SUN,
                )
                val (litPixels, meanDiff) = compareLuminance(std::luminanceAt, fast::luminanceAt)
                assertTrue(litPixels > MIN_TERRAIN_PIXELS, "$backend fast terrain must cover terrain pixels ($litPixels)")
                assertTrue(
                    meanDiff < MAX_STD_VS_FAST_MEAN_DIFF,
                    "$backend fast vs standard terrain mean luminance diff $meanDiff exceeded $MAX_STD_VS_FAST_MEAN_DIFF",
                )
                fast
            }
        }

        val (_, crossMean) = compareLuminance(fastImages[0]::luminanceAt, fastImages[1]::luminanceAt)
        assertTrue(
            crossMean < MAX_CROSS_BACKEND_MEAN_DIFF,
            "Vulkan vs WebGPU Fast terrain mean luminance diff $crossMean exceeded $MAX_CROSS_BACKEND_MEAN_DIFF",
        )
    }

    private fun buildTwoLayerTestSurface() = terrainLayersSurface(
        palette = TerrainLayerPalette(
            layers = listOf(
                TerrainLayer(id = "grass", albedo = "grass.png", tiling = 4f),
                TerrainLayer(id = "rock", albedo = "rock.png", tiling = 8f),
            ),
        ),
        albedo = packLayerArray(
            listOf(
                solidTexture(0x64, 0x98, 0x48),
                solidTexture(0x88, 0x82, 0x7A),
            ),
        ),
        control = TerrainControlMap(
            2,
            2,
            byteArrayOf(0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0),
            byteArrayOf(200.toByte(), 55, 0, 0, 200.toByte(), 55, 0, 0, 180.toByte(), 75, 0, 0, 180.toByte(), 75, 0, 0),
        ),
        lightmap = TerrainLightmap(1, 1, byteArrayOf(0x80.toByte(), 0x80.toByte(), 0x80.toByte(), 0)),
    )

    private fun compareLuminance(a: (Int, Int) -> Int, b: (Int, Int) -> Int): Pair<Int, Double> {
        var totalDiff = 0L
        var litPixels = 0
        for (y in 0 until SCENE_SIZE) {
            for (x in 0 until SCENE_SIZE) {
                val bLum = b(x, y)
                if (bLum > 20) litPixels++
                totalDiff += abs(a(x, y) - bLum)
            }
        }
        return litPixels to totalDiff.toDouble() / (SCENE_SIZE * SCENE_SIZE)
    }

    private companion object {
        val SUN = Vec3f(0.8f, 1.0f, 0.3f)
        const val MIN_TERRAIN_PIXELS = 2_000
        const val MAX_STD_VS_FAST_MEAN_DIFF = 8.0
        const val MAX_CROSS_BACKEND_MEAN_DIFF = 3.0

        fun solidTexture(r: Int, g: Int, b: Int): TextureAsset = TextureAsset(
            data = ByteArray(2 * 2 * 4) { idx ->
                when (idx % 4) {
                    0 -> r.toByte()
                    1 -> g.toByte()
                    2 -> b.toByte()
                    else -> 255.toByte()
                }
            },
            width = 2,
            height = 2,
        )
    }
}
