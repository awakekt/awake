/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaderpack

import io.github.awakelab.awake.asset.shaderdsl.TriangleShader
import io.github.awakelab.awake.asset.shaders.aslShaderSet

/**
 * The content shaders this pack ships, each carrying its own WGSL.
 *
 * Emitted from the ASL definition rather than loaded from a `.wgsl` beside it, which is what
 * `EngineShaderSets` already does for the engine's own shaders. Same three consequences: no
 * resource to sync into each app, nothing for a drift test to keep in step with the definition,
 * and a shader that works unchanged on iOS and wasm, whose loaders cannot see a library's own
 * resources.
 *
 * A `RenderPlan` names one of these instead of `shaderSet("lit_shadow")`.
 */
object PackShaderSets {
    val Triangle = aslShaderSet(TriangleShader)
    val LitShadow = aslShaderSet(::litShadowShader)
    val ShadowDepth = aslShaderSet(ShadowDepthShader)
    val SceneDepth = aslShaderSet(SceneDepthShader)
    val Textured = aslShaderSet(TexturedShader)
    val Instanced = aslShaderSet(InstancedShader)
    val Skinned = aslShaderSet(SkinnedShader)
    val SkinnedTextured = aslShaderSet(SkinnedTexturedShader)
    val SkinnedInstanced = aslShaderSet(SkinnedInstancedShader)
    val Skybox = aslShaderSet(SkyboxShader)
    val Particle = aslShaderSet(ParticleShader)
    val Terrain = aslShaderSet(TerrainShader)
}
