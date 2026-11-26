/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.withSolidOpaque

/**
 * Returns a copy of this [RenderPlan] substituting standard full-PBR mesh shader sets with lean
 * mobile `SimpleLit` / `SkinnedSimpleLit` variants (1 albedo fetch, no screen-space TBN or unused
 * PBR channel fetches, no `pow()` SFU calls).
 *
 * When [includeSurfaceDeposit] is `false` (default), untextured mesh pipelines (`LitShadow`,
 * `Instanced`, `Skinned`, `SkinnedInstanced`) also switch to their deposit-free variants so mobile
 * shader-core register pressure stays low on clear frames. Pass `includeSurfaceDeposit = true` when
 * the scene dynamically renders snow/ash/dust deposits on mobile.
 */
fun RenderPlan.forMobileSimpleLit(includeSurfaceDeposit: Boolean = false): RenderPlan = copy(
    primary = primary.withMobileShaders(includeSurfaceDeposit),
    scenePipelines = scenePipelines.map { it.withMobileShaders(includeSurfaceDeposit) },
)

/**
 * Returns a copy of this [RenderPlan] keeping full PBR mesh shading while omitting procedural
 * 3-octave Simplex surface-deposit math from all built-in mesh pipelines for scenes that do not use
 * weather surface deposits.
 */
fun RenderPlan.withoutSurfaceDeposit(): RenderPlan = copy(
    primary = primary.withoutDepositShaders(),
    scenePipelines = scenePipelines.map { it.withoutDepositShaders() },
)

private fun ScenePipeline.withMobileShaders(includeSurfaceDeposit: Boolean): ScenePipeline {
    val mapped = mobileShaderFor(shaders, includeSurfaceDeposit)
    return if (mapped === shaders) this else copy(shaders = mapped)
}

private fun ScenePipeline.withoutDepositShaders(): ScenePipeline {
    val mapped = noDepositShaderFor(shaders)
    return if (mapped === shaders) this else copy(shaders = mapped)
}

private fun mobileShaderFor(shaders: ShaderSet, includeSurfaceDeposit: Boolean): ShaderSet {
    val table = if (includeSurfaceDeposit) MobileShadersWithDeposit else MobileShadersNoDeposit
    return table[shaders] ?: shaders
}

private fun noDepositShaderFor(shaders: ShaderSet): ShaderSet =
    NoDepositShaders[shaders] ?: shaders

private val MobileShadersNoDeposit: Map<ShaderSet, ShaderSet> by lazy {
    mapOf(
        PackShaderSets.Textured to PackShaderSets.TexturedSimpleLit,
        PackShaderSets.TexturedNoDeposit to PackShaderSets.TexturedSimpleLit,
        PackShaderSets.InstancedTextured to PackShaderSets.InstancedTexturedSimpleLit,
        PackShaderSets.InstancedTexturedNoDeposit to PackShaderSets.InstancedTexturedSimpleLit,
        PackShaderSets.InstancedTexturedTinted to PackShaderSets.InstancedTexturedTintedSimpleLit,
        PackShaderSets.InstancedTexturedTintedNoDeposit to PackShaderSets.InstancedTexturedTintedSimpleLit,
        PackShaderSets.SkinnedTextured to PackShaderSets.SkinnedTexturedSimpleLit,
        PackShaderSets.SkinnedTexturedNoDeposit to PackShaderSets.SkinnedTexturedSimpleLit,
        PackShaderSets.LitShadow to PackShaderSets.LitShadowNoDeposit,
        PackShaderSets.Instanced to PackShaderSets.InstancedNoDeposit,
        PackShaderSets.Skinned to PackShaderSets.SkinnedNoDeposit,
        PackShaderSets.SkinnedInstanced to PackShaderSets.SkinnedInstancedNoDeposit,
    )
}

private val MobileShadersWithDeposit: Map<ShaderSet, ShaderSet> by lazy {
    mapOf(
        PackShaderSets.Textured to TexturedSimpleLitDeposit,
        PackShaderSets.TexturedNoDeposit to TexturedSimpleLitDeposit,
        PackShaderSets.InstancedTextured to InstancedTexturedSimpleLitDeposit,
        PackShaderSets.InstancedTexturedNoDeposit to InstancedTexturedSimpleLitDeposit,
        PackShaderSets.InstancedTexturedTinted to InstancedTexturedTintedSimpleLitDeposit,
        PackShaderSets.InstancedTexturedTintedNoDeposit to InstancedTexturedTintedSimpleLitDeposit,
        PackShaderSets.SkinnedTextured to SkinnedTexturedSimpleLitDeposit,
        PackShaderSets.SkinnedTexturedNoDeposit to SkinnedTexturedSimpleLitDeposit,
    )
}

private val NoDepositShaders: Map<ShaderSet, ShaderSet> by lazy {
    mapOf(
        PackShaderSets.Textured to PackShaderSets.TexturedNoDeposit,
        PackShaderSets.InstancedTextured to PackShaderSets.InstancedTexturedNoDeposit,
        PackShaderSets.InstancedTexturedTinted to PackShaderSets.InstancedTexturedTintedNoDeposit,
        PackShaderSets.SkinnedTextured to PackShaderSets.SkinnedTexturedNoDeposit,
        PackShaderSets.LitShadow to PackShaderSets.LitShadowNoDeposit,
        PackShaderSets.Instanced to PackShaderSets.InstancedNoDeposit,
        PackShaderSets.Skinned to PackShaderSets.SkinnedNoDeposit,
        PackShaderSets.SkinnedInstanced to PackShaderSets.SkinnedInstancedNoDeposit,
    )
}

private val TexturedSimpleLitDeposit: ShaderSet by lazy {
    aslShaderSet { simpleLitTexturedShader(it, includeSurfaceDeposit = true) }
        .withSolidOpaque(aslShaderSet { simpleLitTexturedSolidShader(it, includeSurfaceDeposit = true) })
}

private val InstancedTexturedSimpleLitDeposit: ShaderSet by lazy {
    aslShaderSet { instancedSimpleLitTexturedShader(it, includeSurfaceDeposit = true) }
        .withSolidOpaque(aslShaderSet { instancedSimpleLitTexturedSolidShader(it, includeSurfaceDeposit = true) })
}

private val InstancedTexturedTintedSimpleLitDeposit: ShaderSet by lazy {
    aslShaderSet { instancedSimpleLitTexturedTintedShader(it, includeSurfaceDeposit = true) }
        .withSolidOpaque(aslShaderSet { instancedSimpleLitTexturedTintedSolidShader(it, includeSurfaceDeposit = true) })
}

private val SkinnedTexturedSimpleLitDeposit: ShaderSet by lazy {
    aslShaderSet(skinnedSimpleLitTexturedShader(includeSurfaceDeposit = true))
        .withSolidOpaque(aslShaderSet(skinnedSimpleLitTexturedSolidShader(includeSurfaceDeposit = true)))
}
