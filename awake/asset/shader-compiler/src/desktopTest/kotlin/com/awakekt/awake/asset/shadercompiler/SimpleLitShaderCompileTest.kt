/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import com.awakekt.awake.asset.shaderdsl.AslShaderDefinition
import com.awakekt.awake.asset.shaderpack.SkinnedShader
import com.awakekt.awake.asset.shaderpack.SkinnedTexturedShader
import com.awakekt.awake.asset.shaderpack.instancedLitShadowShader
import com.awakekt.awake.asset.shaderpack.instancedLitShadowSolidShader
import com.awakekt.awake.asset.shaderpack.instancedSimpleLitTexturedShader
import com.awakekt.awake.asset.shaderpack.instancedSimpleLitTexturedSolidShader
import com.awakekt.awake.asset.shaderpack.instancedSimpleLitTexturedTintedShader
import com.awakekt.awake.asset.shaderpack.instancedSimpleLitTexturedTintedSolidShader
import com.awakekt.awake.asset.shaderpack.instancedTexturedShader
import com.awakekt.awake.asset.shaderpack.instancedTexturedSolidShader
import com.awakekt.awake.asset.shaderpack.instancedTexturedTintedShader
import com.awakekt.awake.asset.shaderpack.instancedTexturedTintedSolidShader
import com.awakekt.awake.asset.shaderpack.litShadowShader
import com.awakekt.awake.asset.shaderpack.litShadowSolidShader
import com.awakekt.awake.asset.shaderpack.simpleLitTexturedShader
import com.awakekt.awake.asset.shaderpack.simpleLitTexturedSolidShader
import com.awakekt.awake.asset.shaderpack.skinnedInstancedLitShadowShader
import com.awakekt.awake.asset.shaderpack.skinnedInstancedLitShadowSolidShader
import com.awakekt.awake.asset.shaderpack.skinnedShader
import com.awakekt.awake.asset.shaderpack.skinnedSimpleLitTexturedShader
import com.awakekt.awake.asset.shaderpack.skinnedSimpleLitTexturedSolidShader
import com.awakekt.awake.asset.shaderpack.skinnedSolidShader
import com.awakekt.awake.asset.shaderpack.skinnedTexturedShader
import com.awakekt.awake.asset.shaderpack.skinnedTexturedSolidShader
import com.awakekt.awake.asset.shaderpack.texturedShader
import com.awakekt.awake.asset.shaderpack.texturedSolidShader
import com.awakekt.awake.core.math.ClipSpace
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Validates every `SimpleLit` and deposit-free mesh shader variant through Naga to both WGSL and
 * SPIR-V, and confirms that both removing surface-deposit noise and switching to `SimpleLit`
 * strictly shrink compiled SPIR-V shader binary size.
 */
class SimpleLitShaderCompileTest {
    private fun compileBoth(name: String, def: AslShaderDefinition): ByteArray {
        val wgsl = def.emitWgsl()
        assertNull(NagaShaderCompiler.validate(wgsl), "WGSL validation failed for $name")
        val spirv = NagaShaderCompiler.wgslToSpirv(wgsl)
        assertTrue(spirv.isNotEmpty() && spirv.size % 4 == 0, "$name compiled to invalid SPIR-V size ${spirv.size}")
        return spirv
    }

    @Test
    fun allSimpleLitAndNoDepositMeshShadersValidateAndCompileToSpirv() {
        val all = buildMap<String, AslShaderDefinition> {
            for (clip in listOf(ClipSpace.Vulkan, ClipSpace.WebGpu)) {
                put("simpleLitTextured[$clip]", simpleLitTexturedShader(clip))
                put("simpleLitTexturedSolid[$clip]", simpleLitTexturedSolidShader(clip))
                put("simpleLitTexturedDeposit[$clip]", simpleLitTexturedShader(clip, includeSurfaceDeposit = true))
                put("simpleLitTexturedDepositSolid[$clip]", simpleLitTexturedSolidShader(clip, includeSurfaceDeposit = true))
                put("instancedSimpleLitTextured[$clip]", instancedSimpleLitTexturedShader(clip))
                put("instancedSimpleLitTexturedSolid[$clip]", instancedSimpleLitTexturedSolidShader(clip))
                put("instancedSimpleLitTexturedTinted[$clip]", instancedSimpleLitTexturedTintedShader(clip))
                put("instancedSimpleLitTexturedTintedSolid[$clip]", instancedSimpleLitTexturedTintedSolidShader(clip))
                put("texturedNoDeposit[$clip]", texturedShader(clip, includeSurfaceDeposit = false))
                put("texturedNoDepositSolid[$clip]", texturedSolidShader(clip, includeSurfaceDeposit = false))
                put("instancedTexturedNoDeposit[$clip]", instancedTexturedShader(clip, includeSurfaceDeposit = false))
                put("instancedTexturedNoDepositSolid[$clip]", instancedTexturedSolidShader(clip, includeSurfaceDeposit = false))
                put("instancedTexturedTintedNoDeposit[$clip]", instancedTexturedTintedShader(clip, includeSurfaceDeposit = false))
                put("instancedTexturedTintedNoDepositSolid[$clip]", instancedTexturedTintedSolidShader(clip, includeSurfaceDeposit = false))
                put("litShadowNoDeposit[$clip]", litShadowShader(clip, includeSurfaceDeposit = false))
                put("litShadowNoDepositSolid[$clip]", litShadowSolidShader(clip, includeSurfaceDeposit = false))
                put("instancedLitShadowNoDeposit[$clip]", instancedLitShadowShader(clip, includeSurfaceDeposit = false))
                put("instancedLitShadowNoDepositSolid[$clip]", instancedLitShadowSolidShader(clip, includeSurfaceDeposit = false))
                put("skinnedInstancedNoDeposit[$clip]", skinnedInstancedLitShadowShader(clip, includeSurfaceDeposit = false))
                put("skinnedInstancedNoDepositSolid[$clip]", skinnedInstancedLitShadowSolidShader(clip, includeSurfaceDeposit = false))
            }
            put("skinnedSimpleLitTextured", skinnedSimpleLitTexturedShader())
            put("skinnedSimpleLitTexturedSolid", skinnedSimpleLitTexturedSolidShader())
            put("skinnedSimpleLitTexturedDeposit", skinnedSimpleLitTexturedShader(includeSurfaceDeposit = true))
            put("skinnedSimpleLitTexturedDepositSolid", skinnedSimpleLitTexturedSolidShader(includeSurfaceDeposit = true))
            put("skinnedNoDeposit", skinnedShader(includeSurfaceDeposit = false))
            put("skinnedNoDepositSolid", skinnedSolidShader(includeSurfaceDeposit = false))
            put("skinnedTexturedNoDeposit", skinnedTexturedShader(includeSurfaceDeposit = false))
            put("skinnedTexturedNoDepositSolid", skinnedTexturedSolidShader(includeSurfaceDeposit = false))
            // Also re-validate deposit-enabled variants using the shared simplexNoise2dGrad helper.
            put("texturedWithSharedDeposit", texturedShader(ClipSpace.Vulkan, includeSurfaceDeposit = true))
            put("skinnedTexturedWithSharedDeposit", SkinnedTexturedShader)
            put("skinnedWithSharedDeposit", SkinnedShader)
            put("litShadowWithSharedDeposit", litShadowShader(ClipSpace.Vulkan, includeSurfaceDeposit = true))
        }

        all.forEach { (name, shader) -> compileBoth(name, shader) }
    }

    @Test
    fun simpleLitAndNoDepositShadersStrictlyShrinkSpirvSize() {
        val fullTextured = compileBoth("texturedFull", texturedShader(ClipSpace.Vulkan, includeSurfaceDeposit = true))
        val noDepositTextured = compileBoth("texturedNoDeposit", texturedShader(ClipSpace.Vulkan, includeSurfaceDeposit = false))
        val simpleTextured = compileBoth("simpleLitTextured", simpleLitTexturedShader(ClipSpace.Vulkan, includeSurfaceDeposit = false))

        assertTrue(
            noDepositTextured.size < fullTextured.size,
            "texturedNoDeposit SPIR-V (${noDepositTextured.size} B) must be smaller than texturedFull (${fullTextured.size} B)",
        )
        assertTrue(
            simpleTextured.size < noDepositTextured.size,
            "simpleLitTextured SPIR-V (${simpleTextured.size} B) must be smaller than texturedNoDeposit (${noDepositTextured.size} B)",
        )

        val fullSkinned = compileBoth("skinnedTexturedFull", skinnedTexturedShader(includeSurfaceDeposit = true))
        val noDepositSkinned = compileBoth("skinnedTexturedNoDeposit", skinnedTexturedShader(includeSurfaceDeposit = false))
        val simpleSkinned = compileBoth("skinnedSimpleLitTextured", skinnedSimpleLitTexturedShader(includeSurfaceDeposit = false))

        assertTrue(
            noDepositSkinned.size < fullSkinned.size,
            "skinnedTexturedNoDeposit SPIR-V (${noDepositSkinned.size} B) must be smaller than skinnedTexturedFull (${fullSkinned.size} B)",
        )
        assertTrue(
            simpleSkinned.size < noDepositSkinned.size,
            "skinnedSimpleLitTextured SPIR-V (${simpleSkinned.size} B) must be smaller than skinnedTexturedNoDeposit (${noDepositSkinned.size} B)",
        )
    }
}
