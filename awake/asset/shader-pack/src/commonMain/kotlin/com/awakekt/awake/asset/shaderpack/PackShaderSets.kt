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
    /** Minimal unlit colored triangle shader set. */
    val Triangle = aslShaderSet(TriangleShader)

    /** Standard lit mesh shader set with directional light and cascaded shadow maps. */
    val LitShadow = aslShaderSet(::litShadowShader)

    /** Shadow depth pass shader set for rigid static meshes. */
    val ShadowDepth = aslShaderSet(ShadowDepthShader)

    /** Depth companions whose vertex inputs differ from [ShadowDepth]. */
    val InstancedShadowDepth = aslShaderSet(InstancedShadowDepthShader)

    /** Shadow depth pass shader set for instanced textured meshes. */
    val InstancedTexturedShadowDepth = aslShaderSet(InstancedTexturedShadowDepthShader)

    /** Shadow depth pass shader set for instanced skinned skeletal meshes. */
    val SkinnedInstancedShadowDepth = aslShaderSet(SkinnedInstancedShadowDepthShader)

    /** Shadow depth pass shader set for skinned skeletal meshes. */
    val SkinnedShadowDepth = aslShaderSet(SkinnedShadowDepthShader)

    /** Shadow depth pass shader set for skinned textured skeletal meshes. */
    val SkinnedTexturedShadowDepth = aslShaderSet(SkinnedTexturedShadowDepthShader)

    /** Shadow depth pass shader set for billboard particles. */
    val ParticleShadowDepth = aslShaderSet(ParticleShadowDepthShader)

    /** Linear scene depth prepass shader set. */
    val SceneDepth = aslShaderSet(SceneDepthShader)

    /** Shadow depth pass shader set for alpha-masked textured meshes. */
    val MaskedTexturedShadowDepth = aslShaderSet(MaskedTexturedDepthShader)

    /** Shadow depth pass shader set for instanced alpha-masked textured meshes. */
    val InstancedMaskedTexturedShadowDepth = aslShaderSet(InstancedMaskedTexturedDepthShader)

    /** A masked textured skinned mesh's caster: map `DepthRenderKey(Skinned, Masked)` to it. */
    val SkinnedMaskedTexturedShadowDepth = aslShaderSet(SkinnedMaskedTexturedDepthShader)

    /** Diffuse textured static mesh shader set with lighting and shadows. */
    val Textured = aslShaderSet(::texturedShader)

    /** Instanced diffuse textured mesh shader set with lighting and shadows. */
    val InstancedTextured = aslShaderSet(::instancedTexturedShader)

    /** Instanced static mesh shader set with lighting and shadows. */
    val Instanced = aslShaderSet(::instancedLitShadowShader)

    /** Skinned skeletal mesh shader set with lighting and shadows. */
    val Skinned = aslShaderSet(SkinnedShader)

    /** Skinned skeletal mesh shader set with diffuse texturing and shadows. */
    val SkinnedTextured = aslShaderSet(SkinnedTexturedShader)

    /** Instanced skinned skeletal mesh shader set with lighting and shadows. */
    val SkinnedInstanced = aslShaderSet(::skinnedInstancedLitShadowShader)

    /** Procedural gradient skybox shader set with celestial bodies. */
    val Skybox = aslShaderSet(SkyboxShader)

    /** Sampled cubemap skybox shader set with exposure tone mapping. */
    val SkyboxCubemap = aslShaderSet(SkyboxCubemapShader)

    /** Billboard textured particle shader set with particle tinting. */
    val Particle = aslShaderSet(ParticleShader)

    /** Clipmap terrain shader set with height sampling and texture splatting. */
    val Terrain = aslShaderSet(::terrainShader)

    /** Procedural anti-aliased infinite ground grid shader set. */
    val InfiniteGrid = aslShaderSet(InfiniteGridShader)
}
