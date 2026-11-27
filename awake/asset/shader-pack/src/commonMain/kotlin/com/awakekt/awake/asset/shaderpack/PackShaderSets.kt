/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.aslShaderSet
import com.awakekt.awake.asset.shaders.withSolidOpaque

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
    /** Minimal unshadowed lit colored mesh shader set. */
    val Triangle = aslShaderSet(LitShader)

    /** Unlit atlas quad with straight alpha and an explicit frame and tint. */
    val Sprite = aslShaderSet(SpriteShader)

    /** Distance-field text placed in a scene, with its edge width from screen-space derivatives. */
    val WorldText = aslShaderSet(WorldTextShader)

    /** A trail: an unlit strip of point pairs in its vertices' own colours, optionally textured. */
    val Trail = aslShaderSet(TrailShader)

    /** Standard lit mesh shader set with directional light and cascaded shadow maps. */
    val LitShadow = aslShaderSet(::litShadowShader).withSolidOpaque(aslShaderSet(::litShadowSolidShader))

    /** [LitShadow] without procedural surface-deposit Simplex noise for scenes/tiers without weather deposits. */
    val LitShadowNoDeposit = aslShaderSet { litShadowShader(it, 0.08f, includeSurfaceDeposit = false) }
        .withSolidOpaque(aslShaderSet { litShadowSolidShader(it, 0.08f, includeSurfaceDeposit = false) })

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
    val Textured = aslShaderSet(::texturedShader).withSolidOpaque(aslShaderSet(::texturedSolidShader))

    /** [Textured] without procedural surface-deposit Simplex noise. */
    val TexturedNoDeposit = aslShaderSet { texturedShader(it, includeSurfaceDeposit = false) }
        .withSolidOpaque(aslShaderSet { texturedSolidShader(it, includeSurfaceDeposit = false) })

    /** Lean mobile textured static mesh shader set (1 albedo fetch, no TBN/PBR fetches, no deposit noise). */
    val TexturedSimpleLit = aslShaderSet { simpleLitTexturedShader(it, includeSurfaceDeposit = false) }
        .withSolidOpaque(aslShaderSet { simpleLitTexturedSolidShader(it, includeSurfaceDeposit = false) })

    /** Instanced diffuse textured mesh shader set with lighting and shadows. */
    val InstancedTextured = aslShaderSet(::instancedTexturedShader).withSolidOpaque(aslShaderSet(::instancedTexturedSolidShader))

    /** [InstancedTextured] without procedural surface-deposit Simplex noise. */
    val InstancedTexturedNoDeposit = aslShaderSet { instancedTexturedShader(it, includeSurfaceDeposit = false) }
        .withSolidOpaque(aslShaderSet { instancedTexturedSolidShader(it, includeSurfaceDeposit = false) })

    /** Lean mobile instanced textured mesh shader set (1 albedo fetch, no TBN/PBR fetches). */
    val InstancedTexturedSimpleLit = aslShaderSet { instancedSimpleLitTexturedShader(it, includeSurfaceDeposit = false) }
        .withSolidOpaque(aslShaderSet { instancedSimpleLitTexturedSolidShader(it, includeSurfaceDeposit = false) })

    /** [InstancedTextured] with a tint and fade per instance, for `PipelineVariant.InstancedTinted`. */
    val InstancedTexturedTinted =
        aslShaderSet(::instancedTexturedTintedShader).withSolidOpaque(aslShaderSet(::instancedTexturedTintedSolidShader))

    /** [InstancedTexturedTinted] without procedural surface-deposit Simplex noise. */
    val InstancedTexturedTintedNoDeposit = aslShaderSet { instancedTexturedTintedShader(it, includeSurfaceDeposit = false) }
        .withSolidOpaque(aslShaderSet { instancedTexturedTintedSolidShader(it, includeSurfaceDeposit = false) })

    /** Lean mobile instanced tinted textured mesh shader set (1 albedo fetch, no TBN/PBR fetches). */
    val InstancedTexturedTintedSimpleLit = aslShaderSet { instancedSimpleLitTexturedTintedShader(it, includeSurfaceDeposit = false) }
        .withSolidOpaque(aslShaderSet { instancedSimpleLitTexturedTintedSolidShader(it, includeSurfaceDeposit = false) })

    /** Instanced static mesh shader set with lighting and shadows. */
    val Instanced = aslShaderSet(::instancedLitShadowShader).withSolidOpaque(aslShaderSet(::instancedLitShadowSolidShader))

    /** [Instanced] without procedural surface-deposit Simplex noise. */
    val InstancedNoDeposit = aslShaderSet { instancedLitShadowShader(it, includeSurfaceDeposit = false) }
        .withSolidOpaque(aslShaderSet { instancedLitShadowSolidShader(it, includeSurfaceDeposit = false) })

    /** Skinned skeletal mesh shader set with lighting and shadows. */
    val Skinned = aslShaderSet(SkinnedShader).withSolidOpaque(aslShaderSet(SkinnedSolidShader))

    /** [Skinned] without procedural surface-deposit Simplex noise. */
    val SkinnedNoDeposit = aslShaderSet(skinnedShader(includeSurfaceDeposit = false))
        .withSolidOpaque(aslShaderSet(skinnedSolidShader(includeSurfaceDeposit = false)))

    /** Skinned skeletal mesh shader set with diffuse texturing and shadows. */
    val SkinnedTextured = aslShaderSet(SkinnedTexturedShader).withSolidOpaque(aslShaderSet(SkinnedTexturedSolidShader))

    /** [SkinnedTextured] without procedural surface-deposit Simplex noise. */
    val SkinnedTexturedNoDeposit = aslShaderSet(skinnedTexturedShader(includeSurfaceDeposit = false))
        .withSolidOpaque(aslShaderSet(skinnedTexturedSolidShader(includeSurfaceDeposit = false)))

    /** Lean mobile skinned textured mesh shader set (1 albedo fetch, no TBN/PBR fetches, no deposit noise). */
    val SkinnedTexturedSimpleLit = aslShaderSet(skinnedSimpleLitTexturedShader(includeSurfaceDeposit = false))
        .withSolidOpaque(aslShaderSet(skinnedSimpleLitTexturedSolidShader(includeSurfaceDeposit = false)))

    /** Instanced skinned skeletal mesh shader set with lighting and shadows. */
    val SkinnedInstanced =
        aslShaderSet(::skinnedInstancedLitShadowShader).withSolidOpaque(aslShaderSet(::skinnedInstancedLitShadowSolidShader))

    /** [SkinnedInstanced] without procedural surface-deposit Simplex noise. */
    val SkinnedInstancedNoDeposit = aslShaderSet { skinnedInstancedLitShadowShader(it, includeSurfaceDeposit = false) }
        .withSolidOpaque(aslShaderSet { skinnedInstancedLitShadowSolidShader(it, includeSurfaceDeposit = false) })

    /** Procedural gradient skybox shader set with celestial bodies. */
    val Skybox = aslShaderSet(SkyboxShader)

    /** Sampled cubemap skybox shader set with exposure tone mapping. */
    val SkyboxCubemap = aslShaderSet(SkyboxCubemapShader)

    /** The sun and moon drawn over a sky: see [CelestialShader]. */
    val Celestial = aslShaderSet(CelestialShader)

    /** Depth-occluded optical sun flare drawn after geometry: see [sunFlareShader]. */
    val SunFlare = aslShaderSet { sunFlareShader(it, samplesDepth = true) }

    /** Unoccluded optical sun flare for plans without a scene-depth pre-pass: see [sunFlareShader]. */
    val SunFlareUnoccluded = aslShaderSet { sunFlareShader(it, samplesDepth = false) }

    /** Billboard textured particle shader set with particle tinting. */
    val Particle = aslShaderSet(ParticleShader)

    /** Clipmap terrain shader set with height sampling and texture splatting. */
    val Terrain = aslShaderSet(::terrainShader).withSolidOpaque(aslShaderSet(::terrainCoreShader))

    /** Lean mobile clipmap terrain shader set (`TerrainQuality.Fast`, no surface-deposit noise or `pow()`). */
    val TerrainFast = aslShaderSet { terrainFastShader(it) }.withSolidOpaque(aslShaderSet { terrainFastCoreShader(it) })

    /** Lean mobile paged clipmap terrain shader set (`TerrainQuality.Fast`, single-tap lookup, no deposit noise). */
    val PagedTerrainFast =
        aslShaderSet { pagedTerrainFastShader(it) }.withSolidOpaque(aslShaderSet { pagedTerrainFastCoreShader(it) })

    /** The clipmap rings' cascade depth, for any surface shader on the clipmap stage. */
    val TerrainShadowDepth = aslShaderSet(TerrainShadowDepthShader)

    /** Procedural anti-aliased infinite ground grid shader set. */
    val InfiniteGrid = aslShaderSet(InfiniteGridShader)

    /** Procedural stylized water surface shader set without scene-depth sampling: see [stylizedWaterShader]. */
    val StylizedWater =
        aslShaderSet { stylizedWaterShader(it, samplesDepth = false) }.withSolidOpaque(aslShaderSet(StylizedWaterFastShader))

    /** Procedural stylized water surface shader set with scene-depth shoreline and obstacle foam: see [stylizedWaterShader]. */
    val StylizedWaterDepth =
        aslShaderSet { stylizedWaterShader(it, samplesDepth = true) }.withSolidOpaque(aslShaderSet(StylizedWaterFastShader))

    /** Single-pass procedural cloud layer shader set with Fast and Standard tiers: see [CloudLayerShader]. */
    val CloudLayer = aslShaderSet(CloudLayerShader).withSolidOpaque(aslShaderSet(CloudLayerFastShader))
}
