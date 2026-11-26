/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.kit.terrainlayers

import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TerrainQualityFastShaderTest {
    private val materialGroup = BindingLayout.Standard.slot(BindingSemantic.Material)

    @Test
    fun fastTerrainLayersShadersEliminateFourTapMergeHeightBlendPowAndDeposit() {
        for (clip in listOf(ClipSpace.Vulkan, ClipSpace.WebGpu)) {
            val standard = terrainLayersShader(clip)
            val fast = terrainLayersFastShader(clip)
            val fastCore = terrainLayersFastCoreShader(clip)

            val fastWgsl = fast.emitWgsl()
            val fastCoreWgsl = fastCore.emitWgsl()

            // Zero 4-tap bilinear control merge, height-blend pass, pow(), or surface deposit noise.
            val forbidden = listOf("tap1Layers", "tap2Layers", "tap3Layers", "blendCeiling", "pow(", "0.1031")
            for (token in forbidden) {
                assertFalse(token in fastWgsl, "fast[$clip] must omit '$token'")
                assertFalse(token in fastCoreWgsl, "fastCore[$clip] must omit '$token'")
            }

            // Uses exp2 inverse tiling and fast sqrt/polynomial display transforms.
            assertTrue("exp2(" in fastWgsl, "fast[$clip] must use exp2 inverse tiling")
            assertTrue("fn fastLinearToSrgb" in fastWgsl, "fast[$clip] must use sqrt display encode")
            assertTrue("fn fastDisplayToLinear" in fastWgsl, "fast[$clip] must use polynomial sRGB decode")
            assertFalse("discard;" in fastCoreWgsl, "fastCore[$clip] must omit discard for Early-Z")

            // Exact group-0 layout parity so existing TerrainSurface textures bind unchanged.
            assertEquals(standard.bindingsForGroup(materialGroup), fast.bindingsForGroup(materialGroup))
            assertEquals(standard.bindingsForGroup(materialGroup), fastCore.bindingsForGroup(materialGroup))

            // Standard terrain layers shader also upgraded to exp2 inverse tiling (no pow(2.0, ...)).
            val stdWgsl = standard.emitWgsl()
            assertTrue("exp2(" in stdWgsl, "standard[$clip] should use exp2 inverse tiling")
            assertFalse("pow(2.0," in stdWgsl, "standard[$clip] should no longer compute tiling via pow(2.0, ...)")
        }
    }

    @Test
    fun pagedFastTerrainLayersShaderOmitsCrossCellFourTapBranchesAndKeepsBindings() {
        val standardPaged = terrainLayersShader(ClipSpace.Vulkan, CONTROL_SLOTS, paged = true)
        val fastPaged = pagedTerrainLayersFastShader(ClipSpace.Vulkan)
        val fastPagedCore = pagedTerrainLayersFastCoreShader(ClipSpace.Vulkan)

        val wgsl = fastPaged.emitWgsl()
        assertFalse("lightTap0" in wgsl, "paged fast lightmap must omit 4-tap seam fallback")
        assertFalse("tap1Layers" in wgsl, "paged fast control fetch must read single center tap")
        assertFalse("pow(" in wgsl, "paged fast shader must omit pow()")
        assertTrue("fastCtrlLayer" in wgsl && "fastLightCell" in wgsl)
        assertFalse("discard;" in fastPagedCore.emitWgsl())

        assertEquals(standardPaged.bindingsForGroup(materialGroup), fastPaged.bindingsForGroup(materialGroup))
        assertEquals(standardPaged.bindingsForGroup(materialGroup), fastPagedCore.bindingsForGroup(materialGroup))
    }

    @Test
    fun terrainSurfaceWithTerrainQualitySwitchesShaderSetCleanly() {
        val surface = terrainLayersSurface(
            palette = TerrainLayerPalette(layers = listOf(TerrainLayer(id = "grass", albedo = "grass.png"))),
            albedo = packLayerArray(listOf(TextureAsset(ByteArray(2 * 2 * 4) { -1 }, 2, 2))),
            control = TerrainControlMap(1, 1, ByteArray(4), byteArrayOf(-1, 0, 0, 0)),
        )
        assertEquals(TerrainLayersShaders, surface.shaders)

        val fastSurface = surface.withTerrainQuality(TerrainQuality.Fast)
        assertEquals(TerrainLayersFastShaders, fastSurface.shaders)
        assertEquals(surface.textures, fastSurface.textures)
        assertEquals(surface.samplerTextures, fastSurface.samplerTextures)
        assertNotNull(fastSurface.shaders.solidOpaque, "Fast shader set must include Early-Z core companion")

        val fastWithDeposit = surface.withTerrainQuality(TerrainQuality.Fast, includeSurfaceDeposit = true)
        val wgsl = (fastWithDeposit.shaders.vulkan[ShaderStage.FRAGMENT] as ShaderSource.InlineText).sourceCode
        assertTrue("0.1031" in wgsl, "opt-in deposit flag must include procedural surface deposit noise on Fast terrain")
        assertFalse("tap1Layers" in wgsl, "opt-in deposit on Fast terrain must stay single-tap")
    }
}
