/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.project.runtime

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.skyboxContentFeature
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineVariant

/**
 * Everything a played project can draw, on Vulkan and WebGPU alike: lit and shadowed meshes,
 * skinned and textured ones, instanced props, particles and the skybox. A host that plays a project
 * hands this to its backend, so a scene component that needs a pipeline gains it here, beside the
 * systems [runProject] runs for it.
 *
 * A host that draws more, such as an editor's debug views, adds its own content features to a copy.
 */
val ProjectRenderPlan: RenderPlan = RenderPlan(
    primary = ScenePipeline(
        key = PipelineKey.Primary,
        shaders = PackShaderSets.LitShadow,
        vertexFormat = VertexFormat.PositionNormalColor,
    ),
    contentFeatures = listOf(skyboxContentFeature(PackShaderSets.Skybox)),
    depthPrePassShaderSet = PackShaderSets.ShadowDepth,
    depthPrePassVariants = mapOf(DepthCasterKind.Instanced to PackShaderSets.InstancedShadowDepth),
    // Cut-out foliage casts through its texture's alpha; a masked caster never falls back to the
    // opaque shader, so without these it casts nothing.
    depthPrePassKeyedVariants = mapOf(
        DepthRenderKey(DepthCasterKind.Ordinary, AlphaMode.Masked) to PackShaderSets.MaskedTexturedShadowDepth,
        DepthRenderKey(DepthCasterKind.Instanced, AlphaMode.Masked) to PackShaderSets.InstancedMaskedTexturedShadowDepth,
        DepthRenderKey(DepthCasterKind.Skinned, AlphaMode.Masked) to PackShaderSets.SkinnedMaskedTexturedShadowDepth,
    ),
    scenePipelines = listOf(
        // A skinned part casts through a depth shader that reads its joint palette.
        ScenePipeline(
            key = PipelineKey.Format(VertexFormat.PositionNormalColorSkin),
            shaders = PackShaderSets.Skinned,
            vertexFormat = VertexFormat.PositionNormalColorSkin,
            depthShaders = PackShaderSets.SkinnedShadowDepth,
        ),
        // Textured meshes drawn transparent or additive, like glowing effect parts, need their
        // blended twins; without them they fall back to the opaque pipeline.
        ScenePipeline(
            key = PipelineKey.Format(VertexFormat.PositionNormalColorUv),
            shaders = PackShaderSets.Textured,
            vertexFormat = VertexFormat.PositionNormalColorUv,
            buildTransparent = true,
            buildAdditive = true,
        ),
        ScenePipeline(
            key = PipelineKey.Format(VertexFormat.PositionNormalColorUvSkin),
            shaders = PackShaderSets.SkinnedTextured,
            vertexFormat = VertexFormat.PositionNormalColorUvSkin,
            depthShaders = PackShaderSets.SkinnedTexturedShadowDepth,
        ),
        // Repeated props: Core folds copies of one mesh and material into a single instanced draw.
        ScenePipeline(
            key = PipelineKey.Instanced,
            shaders = PackShaderSets.Instanced,
            vertexFormat = VertexFormat.PositionNormalColor,
            variant = PipelineVariant.Instanced,
        ),
        ScenePipeline(
            key = PipelineKey.InstancedFormat(VertexFormat.PositionNormalColorUv),
            shaders = PackShaderSets.InstancedTextured,
            vertexFormat = VertexFormat.PositionNormalColorUv,
            variant = PipelineVariant.Instanced,
            depthShaders = PackShaderSets.InstancedTexturedShadowDepth,
        ),
        // A scene's particle_emitters, blended or, for glows, added.
        ScenePipeline(
            key = PipelineKey.Particle,
            shaders = PackShaderSets.Particle,
            vertexFormat = VertexFormat.PositionUv,
            variant = PipelineVariant.AlphaBlendedParticle,
            materialBindings = GroupBindings.ParticleMaterial,
            buildAdditive = true,
        ),
    ),
)
