/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderdsl.bindingsForGroup
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.asset.shaders.ShaderStage
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.pipeline.ShaderSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SimpleLitAndOptInDepositShaderTest {
    private val materialGroup = BindingLayout.Standard.slot(BindingSemantic.Material)

    private fun assertLeanSimpleLit(name: String, standard: AslShaderDefinition, simple: AslShaderDefinition, solid: AslShaderDefinition) {
        val wgsl = simple.emitWgsl()
        val solidWgsl = solid.emitWgsl()

        // Zero PBR / TBN / GGX / pow / surface deposit bloat.
        val forbidden = listOf(
            "perturbNormal",
            "distributionGgx",
            "geometrySmith",
            "fresnelSchlick",
            "environmentReflection",
            "pow(",
            "simplexNoise2dGrad",
            "depositAmount",
            "dpdx(in.worldPos)",
            "dpdy(in.worldPos)",
            "textureSampleGrad(metallicRoughnessTexture",
            "textureSampleGrad(normalTexture",
            "textureSampleGrad(occlusionTexture",
            "textureSampleGrad(emissiveTexture",
            "textureSample(metallicRoughnessTexture",
            "textureSample(normalTexture",
            "textureSample(occlusionTexture",
            "textureSample(emissiveTexture",
        )
        for (token in forbidden) {
            assertFalse(token in wgsl, "$name must not emit '$token'")
            assertFalse(token in solidWgsl, "$name solid must not emit '$token'")
        }

        // Uses fast polynomial sRGB decode + sqrt display encode.
        assertTrue("fn fastSrgbToLinear" in wgsl, "$name should use polynomial fastSrgbToLinear")
        assertTrue("fn fastLinearToSrgb" in wgsl, "$name should use sqrt-based fastLinearToSrgb")
        assertTrue("baseColorTexture" in wgsl, "$name must still sample baseColorTexture")

        // Solid companion omits discard / fwidth / ditherRank for Early-Z.
        assertFalse("discard;" in solidWgsl, "$name solid must omit discard")
        assertFalse("fwidth(" in solidWgsl, "$name solid must omit fwidth")
        assertFalse("ditherRank(" in solidWgsl, "$name solid must omit ditherRank")

        // Only binds draw/frame uniforms + baseColorTexture (1) + baseColorSampler (2) — zero PBR maps (5..8).
        val simpleBindings = assertNotNull(simple.bindingsForGroup(materialGroup), "$name must declare material group")
        val simpleBindingIds = simpleBindings.entries.map { it.binding }.toSet()
        assertTrue(0 in simpleBindingIds && 1 in simpleBindingIds && 2 in simpleBindingIds, "$name must keep bindings 0, 1, 2")
        for (pbrBinding in 5..8) {
            assertFalse(pbrBinding in simpleBindingIds, "$name must not declare unused PBR texture binding $pbrBinding")
        }
        val standardBindings = assertNotNull(standard.bindingsForGroup(materialGroup))
        assertTrue(
            simpleBindings.entries.all { it in standardBindings.entries },
            "$name material group bindings must be a strict subset of standard sibling",
        )
        assertEquals(
            simpleBindings,
            solid.bindingsForGroup(materialGroup),
            "$name solid must keep exact material group bindings of its SimpleLit sibling",
        )
    }

    @Test
    fun allSimpleLitShadersEliminatePbrFetchesTbnPowAndDepositWhilePreservingMaterialBindings() {
        for (clip in listOf(ClipSpace.Vulkan, ClipSpace.WebGpu)) {
            assertLeanSimpleLit(
                "simpleLitTextured[$clip]",
                texturedShader(clip),
                simpleLitTexturedShader(clip),
                simpleLitTexturedSolidShader(clip),
            )
            assertLeanSimpleLit(
                "instancedSimpleLitTextured[$clip]",
                instancedTexturedShader(clip),
                instancedSimpleLitTexturedShader(clip),
                instancedSimpleLitTexturedSolidShader(clip),
            )
            assertLeanSimpleLit(
                "instancedSimpleLitTexturedTinted[$clip]",
                instancedTexturedTintedShader(clip),
                instancedSimpleLitTexturedTintedShader(clip),
                instancedSimpleLitTexturedTintedSolidShader(clip),
            )
        }
        assertLeanSimpleLit(
            "skinnedSimpleLitTextured",
            SkinnedTexturedShader,
            skinnedSimpleLitTexturedShader(),
            skinnedSimpleLitTexturedSolidShader(),
        )
    }

    @Test
    fun depositEnabledShadersDeclareSharedSimplexHelperOnceInsteadOfInliningNineCopies() {
        val depositShaders = listOf(
            texturedShader(ClipSpace.Vulkan),
            instancedTexturedShader(ClipSpace.Vulkan),
            instancedTexturedTintedShader(ClipSpace.Vulkan),
            SkinnedTexturedShader,
            SkinnedShader,
            litShadowShader(ClipSpace.Vulkan),
            instancedLitShadowShader(ClipSpace.Vulkan),
            skinnedInstancedLitShadowShader(ClipSpace.Vulkan),
            simpleLitTexturedShader(ClipSpace.Vulkan, includeSurfaceDeposit = true),
            skinnedSimpleLitTexturedShader(includeSurfaceDeposit = true),
        )
        for (def in depositShaders) {
            val wgsl = def.emitWgsl()
            val fnDeclarations = Regex("""fn simplexNoise2dGrad\b""").findAll(wgsl).count()
            assertEquals(1, fnDeclarations, "${def.name} should declare fn simplexNoise2dGrad exactly once")
            assertTrue("simplexNoise2dGrad(" in wgsl, "${def.name} should invoke shared simplexNoise2dGrad")
            // Shared helper should prevent 27-copy inline grad2 explosion (only 3 grad2 corners inside fn).
            val grad2Defs = Regex("""let grad[0-2]_""").findAll(wgsl).count()
            assertTrue(grad2Defs <= 3, "${def.name} should emit at most 3 grad2 blocks inside shared helper, got $grad2Defs")
        }
    }

    @Test
    fun noDepositShaderSetsOmitAllSurfaceDepositNoiseAndAttachSolidCompanions() {
        val noDepositSets = listOf(
            "LitShadowNoDeposit" to PackShaderSets.LitShadowNoDeposit,
            "TexturedNoDeposit" to PackShaderSets.TexturedNoDeposit,
            "InstancedTexturedNoDeposit" to PackShaderSets.InstancedTexturedNoDeposit,
            "InstancedTexturedTintedNoDeposit" to PackShaderSets.InstancedTexturedTintedNoDeposit,
            "InstancedNoDeposit" to PackShaderSets.InstancedNoDeposit,
            "SkinnedNoDeposit" to PackShaderSets.SkinnedNoDeposit,
            "SkinnedTexturedNoDeposit" to PackShaderSets.SkinnedTexturedNoDeposit,
            "SkinnedInstancedNoDeposit" to PackShaderSets.SkinnedInstancedNoDeposit,
            "TexturedSimpleLit" to PackShaderSets.TexturedSimpleLit,
            "InstancedTexturedSimpleLit" to PackShaderSets.InstancedTexturedSimpleLit,
            "InstancedTexturedTintedSimpleLit" to PackShaderSets.InstancedTexturedTintedSimpleLit,
            "SkinnedTexturedSimpleLit" to PackShaderSets.SkinnedTexturedSimpleLit,
        )
        for ((name, set) in noDepositSets) {
            val solid = assertNotNull(set.solidOpaque, "$name must provide solidOpaque companion")
            for (source in listOf(
                set.vulkan[ShaderStage.FRAGMENT],
                set.webGpu[ShaderStage.FRAGMENT],
                solid.vulkan[ShaderStage.FRAGMENT],
                solid.webGpu[ShaderStage.FRAGMENT],
            )) {
                val wgsl = (source as ShaderSource.InlineText).sourceCode
                assertFalse("simplexNoise2dGrad" in wgsl, "$name must omit simplexNoise2dGrad")
                assertFalse("depositAmount" in wgsl, "$name must omit depositAmount")
            }
        }
    }

    @Test
    fun renderPlanMobileAndNoDepositTransformsRemapBuiltInMeshPipelinesCleanly() {
        val base = RenderPlan(
            primary = ScenePipeline(PipelineKey.Primary, PackShaderSets.LitShadow, VertexFormat.PositionNormalColor),
            scenePipelines = listOf(
                ScenePipeline(PipelineKey.Format(VertexFormat.PositionNormalColorUv), PackShaderSets.Textured, VertexFormat.PositionNormalColorUv),
                ScenePipeline(PipelineKey.Format(VertexFormat.PositionNormalColorSkin), PackShaderSets.Skinned, VertexFormat.PositionNormalColorSkin),
                ScenePipeline(PipelineKey.Format(VertexFormat.PositionNormalColorUvSkin), PackShaderSets.SkinnedTextured, VertexFormat.PositionNormalColorUvSkin),
                ScenePipeline(PipelineKey.Instanced, PackShaderSets.Instanced, VertexFormat.PositionNormalColor, variant = PipelineVariant.Instanced),
                ScenePipeline(
                    PipelineKey.InstancedFormat(VertexFormat.PositionNormalColorUv),
                    PackShaderSets.InstancedTexturedTinted,
                    VertexFormat.PositionNormalColorUv,
                    variant = PipelineVariant.InstancedTinted,
                ),
                spriteScenePipeline(),
            ),
        )

        val mobile = base.forMobileSimpleLit()
        assertEquals(PackShaderSets.LitShadowNoDeposit, mobile.primary.shaders)
        assertEquals(PackShaderSets.TexturedSimpleLit, mobile.scenePipelines[0].shaders)
        assertEquals(PackShaderSets.SkinnedNoDeposit, mobile.scenePipelines[1].shaders)
        assertEquals(PackShaderSets.SkinnedTexturedSimpleLit, mobile.scenePipelines[2].shaders)
        assertEquals(PackShaderSets.InstancedNoDeposit, mobile.scenePipelines[3].shaders)
        assertEquals(PackShaderSets.InstancedTexturedTintedSimpleLit, mobile.scenePipelines[4].shaders)
        // Non-mesh pipelines pass through untouched.
        assertEquals(base.scenePipelines[5], mobile.scenePipelines[5])

        val mobileWithDeposit = base.forMobileSimpleLit(includeSurfaceDeposit = true)
        assertEquals(PackShaderSets.LitShadow, mobileWithDeposit.primary.shaders)
        assertEquals(PackShaderSets.Skinned, mobileWithDeposit.scenePipelines[1].shaders)
        val depositFrag = (mobileWithDeposit.scenePipelines[0].shaders.vulkan[ShaderStage.FRAGMENT] as ShaderSource.InlineText).sourceCode
        assertTrue("simplexNoise2dGrad" in depositFrag, "forMobileSimpleLit(includeSurfaceDeposit = true) should retain deposit noise")
        assertFalse("perturbNormal" in depositFrag, "forMobileSimpleLit(includeSurfaceDeposit = true) should remain SimpleLit")

        val noDeposit = base.withoutSurfaceDeposit()
        assertEquals(PackShaderSets.LitShadowNoDeposit, noDeposit.primary.shaders)
        assertEquals(PackShaderSets.TexturedNoDeposit, noDeposit.scenePipelines[0].shaders)
        assertEquals(PackShaderSets.SkinnedNoDeposit, noDeposit.scenePipelines[1].shaders)
        assertEquals(PackShaderSets.SkinnedTexturedNoDeposit, noDeposit.scenePipelines[2].shaders)
        assertEquals(PackShaderSets.InstancedNoDeposit, noDeposit.scenePipelines[3].shaders)
        assertEquals(PackShaderSets.InstancedTexturedTintedNoDeposit, noDeposit.scenePipelines[4].shaders)
    }
}
