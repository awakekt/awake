/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaderdsl.TriangleShader
import com.awakekt.awake.asset.shaders.aslShaderSet

/**
 * The content shaders this pack ships, each carrying its own WGSL.
 *
 * Emitted from the ASL definition rather than loaded from a `.wgsl` beside it, which is what
 * `EngineShaderSets` already does for the engine's own shaders. Same three consequences: no
 * resource to sync into each app, nothing for a drift test to keep in step with the definition,
 * and a shader that works unchanged on iOS and wasm, whose loaders cannot see a library's own
 * resources.
 *
 * A `RenderPlan` names one of these instead of a resource-path shader set without binding metadata.
 */
object PackShaderSets {
    val Triangle = aslShaderSet(TriangleShader)
    val LitShadow = aslShaderSet(::litShadowShader)
    val ShadowDepth = aslShaderSet(ShadowDepthShader)

    /** Depth companions whose vertex inputs differ from [ShadowDepth]. */
    val InstancedShadowDepth = aslShaderSet(InstancedShadowDepthShader)
    val SkinnedInstancedShadowDepth = aslShaderSet(SkinnedInstancedShadowDepthShader)
    val SkinnedShadowDepth = aslShaderSet(SkinnedShadowDepthShader)
    val ParticleShadowDepth = aslShaderSet(ParticleShadowDepthShader)
    val SceneDepth = aslShaderSet(SceneDepthShader)
    val MaskedTexturedShadowDepth = aslShaderSet(MaskedTexturedDepthShader)
    val Textured = aslShaderSet(TexturedShader)
    val Instanced = aslShaderSet(::instancedLitShadowShader)
    val Skinned = aslShaderSet(SkinnedShader)
    val SkinnedTextured = aslShaderSet(SkinnedTexturedShader)
    val SkinnedInstanced = aslShaderSet(::skinnedInstancedLitShadowShader)
    val Skybox = aslShaderSet(SkyboxShader)
    val Particle = aslShaderSet(ParticleShader)
    val Terrain = aslShaderSet(TerrainShader)
    val TerrainSplat = aslShaderSet(TerrainSplatShader)
    val InfiniteGrid = aslShaderSet(InfiniteGridShader)
}
