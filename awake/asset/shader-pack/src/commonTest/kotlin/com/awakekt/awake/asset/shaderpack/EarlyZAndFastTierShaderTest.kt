/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.asset.shaders.RenderBackend
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.core.geometry.MeshGeometry
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.passes.ContentPaint
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.texture.TextureAsset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EarlyZAndFastTierShaderTest {
    private val materialGroup = BindingLayout.Standard.slot(BindingSemantic.Material)

    private fun assertSolidCompanion(name: String, standard: AslShaderDefinition, solid: AslShaderDefinition) {
        val solidWgsl = solid.emitWgsl()
        assertFalse("discard;" in solidWgsl, "$name solid shader must not emit discard so hardware Early-Z/FPK stays active")
        assertFalse("fwidth(" in solidWgsl, "$name solid shader must not compute unused fwidth derivatives")
        assertFalse("ditherRank(" in solidWgsl, "$name solid shader must not include dither fade helpers")
        assertEquals(
            standard.bindingsForGroup(materialGroup),
            solid.bindingsForGroup(materialGroup),
            "$name solid shader must keep the exact material group layout of its standard sibling",
        )
    }

    @Test
    fun allEightOpaqueMeshSolidShadersOmitDiscardAndMatchMaterialBindings() {
        for (clip in listOf(ClipSpace.Vulkan, ClipSpace.WebGpu)) {
            assertSolidCompanion("litShadow[$clip]", litShadowShader(clip), litShadowSolidShader(clip))
            assertSolidCompanion("textured[$clip]", texturedShader(clip), texturedSolidShader(clip))
            assertSolidCompanion("instancedTextured[$clip]", instancedTexturedShader(clip), instancedTexturedSolidShader(clip))
            assertSolidCompanion("instancedTexturedTinted[$clip]", instancedTexturedTintedShader(clip), instancedTexturedTintedSolidShader(clip))
            assertSolidCompanion("instancedLitShadow[$clip]", instancedLitShadowShader(clip), instancedLitShadowSolidShader(clip))
            assertSolidCompanion("skinnedInstanced[$clip]", skinnedInstancedLitShadowShader(clip), skinnedInstancedLitShadowSolidShader(clip))
        }
        assertSolidCompanion("skinned", SkinnedShader, SkinnedSolidShader)
        assertSolidCompanion("skinnedTextured", SkinnedTexturedShader, SkinnedTexturedSolidShader)
    }

    @Test
    fun packShaderSetsAttachSolidOpaqueCompanionsToEveryOpaqueSceneFamily() {
        val opaqueSets = listOf(
            "LitShadow" to PackShaderSets.LitShadow,
            "Textured" to PackShaderSets.Textured,
            "InstancedTextured" to PackShaderSets.InstancedTextured,
            "InstancedTexturedTinted" to PackShaderSets.InstancedTexturedTinted,
            "Instanced" to PackShaderSets.Instanced,
            "Skinned" to PackShaderSets.Skinned,
            "SkinnedTextured" to PackShaderSets.SkinnedTextured,
            "SkinnedInstanced" to PackShaderSets.SkinnedInstanced,
            "Terrain" to PackShaderSets.Terrain,
        )
        for ((name, set) in opaqueSets) {
            val solid = assertNotNull(set.solidOpaque, "$name must provide a discard-free solidOpaque companion")
            val vkSource = solid.vulkan[ShaderStage.FRAGMENT] as? ShaderSource.InlineText
            val wgSource = solid.webGpu[ShaderStage.FRAGMENT] as? ShaderSource.InlineText
            assertNotNull(vkSource, "$name solidOpaque Vulkan WGSL")
            assertNotNull(wgSource, "$name solidOpaque WebGPU WGSL")
            assertFalse("discard;" in vkSource.sourceCode, "$name solidOpaque Vulkan must omit discard")
            assertFalse("discard;" in wgSource.sourceCode, "$name solidOpaque WebGPU must omit discard")
        }
    }

    @Test
    fun skyboxAndCelestialPaintAfterOpaqueWithDepthLessEqual() {
        val skyFeature = skyboxContentFeature(PackShaderSets.Skybox).resolve(RenderBackend.Vulkan)
        assertEquals(ContentPaint.AfterOpaque, skyFeature.paint)
        assertEquals(PipelineVariant.Sky, skyFeature.spec.variant)
        assertTrue(skyFeature.spec.variant.depthTestEnabled)
        assertFalse(skyFeature.spec.variant.depthWriteEnabled)
        assertTrue(skyFeature.spec.variant.depthCompareLessEqual)

        val cubemap = TextureAsset(data = ByteArray(4 * 6) { -1 }, width = 1, height = 1, layerCount = 6, isCubemap = true)
        val cubemapFeature = skyboxCubemapContentFeature(cubemap).resolve(RenderBackend.Vulkan)
        assertEquals(ContentPaint.AfterOpaque, cubemapFeature.paint)
        assertEquals(PipelineVariant.Sky, cubemapFeature.spec.variant)

        val additiveCelestial = celestialContentFeature(additive = true).resolve(RenderBackend.Vulkan)
        assertEquals(ContentPaint.AfterOpaque, additiveCelestial.paint)
        assertEquals(PipelineVariant.SkyAdditive, additiveCelestial.spec.variant)
        assertTrue(additiveCelestial.spec.variant.depthCompareLessEqual)

        val blendedCelestial = celestialContentFeature(additive = false).resolve(RenderBackend.Vulkan)
        assertEquals(ContentPaint.AfterOpaque, blendedCelestial.paint)
        assertEquals(PipelineVariant.SkyBlended, blendedCelestial.spec.variant)
        assertTrue(blendedCelestial.spec.variant.depthCompareLessEqual)
    }

    @Test
    fun cloudLayerFastShaderOmitsStandardThreeShellMathAndAttachesAsAlternateSpec() {
        val fastWgsl = CloudLayerFastShader.emitWgsl()
        assertTrue("fn valueNoise" in fastWgsl)
        assertTrue("fn rotateOctave" in fastWgsl)
        assertFalse("fn cumulusMacro" in fastWgsl, "Fast cloud shader must omit 3-shell cumulusMacro helper")
        assertFalse("parallaxFactor" in fastWgsl, "Fast cloud shader must omit Standard parallax volume math")
        assertEquals(
            CloudLayerShader.bindingsForGroup(materialGroup),
            CloudLayerFastShader.bindingsForGroup(materialGroup),
        )
        assertNotNull(PackShaderSets.CloudLayer.solidOpaque)

        val quad = MeshGeometry(
            vertices = FloatArray(VertexFormat.PositionNormalColorUv.strideFloats * 3),
            indices = intArrayOf(0, 1, 2),
            format = VertexFormat.PositionNormalColorUv,
        )
        val feature = cloudLayerContentFeature(quad).resolve(RenderBackend.Vulkan)
        val altSpec = assertNotNull(feature.alternateSpec, "cloudLayerContentFeature must expose Fast alternateSpec")
        assertEquals(feature.spec.uniforms, altSpec.uniforms)
        assertEquals(feature.spec.vertexFormat, altSpec.vertexFormat)
    }

    @Test
    fun stylizedWaterFastShaderOmitsNoiseAndSceneDepthAndAttachesAsAlternateSpec() {
        val fastWgsl = StylizedWaterFastShader.emitWgsl()
        assertFalse("fn valueNoise" in fastWgsl, "Fast water shader must omit valueNoise")
        assertFalse("fn valueNoiseGrad" in fastWgsl, "Fast water shader must omit valueNoiseGrad")
        assertFalse("sceneDepth" in fastWgsl, "Fast water shader must omit sceneDepth texture")
        assertFalse("clipPos" in fastWgsl, "Fast water shader must omit clipPos varying")
        assertNull(
            StylizedWaterFastShader.bindingsForGroup(BindingLayout.Standard.slot(BindingSemantic.SceneDepth)),
            "Fast water shader must not declare SceneDepth bind group",
        )
        assertEquals(
            StylizedWaterShader.bindingsForGroup(materialGroup),
            StylizedWaterFastShader.bindingsForGroup(materialGroup),
        )
        assertNotNull(PackShaderSets.StylizedWater.solidOpaque)
        assertNotNull(PackShaderSets.StylizedWaterDepth.solidOpaque)

        val quad = MeshGeometry(
            vertices = FloatArray(VertexFormat.PositionNormalColorUv.strideFloats * 3),
            indices = intArrayOf(0, 1, 2),
            format = VertexFormat.PositionNormalColorUv,
        )
        val depthFeature = stylizedWaterContentFeature(quad, samplesDepth = true).resolve(RenderBackend.WebGpu)
        val altSpec = assertNotNull(depthFeature.alternateSpec, "stylizedWaterContentFeature must expose Fast alternateSpec")
        assertEquals(depthFeature.spec.uniforms, altSpec.uniforms)
        assertFalse(
            BindingLayout.Standard.slot(BindingSemantic.SceneDepth) in altSpec.bindingsByGroup,
            "Fast water alternateSpec must omit SceneDepth group",
        )
    }
}
