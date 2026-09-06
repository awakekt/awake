/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.showcase.app

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.depthFogContentFeature
import com.awakekt.awake.asset.shaderpack.skyboxContentFeature
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineVariant

private val lit = PackShaderSets.LitShadow
private val skinned = PackShaderSets.Skinned
private val textured = PackShaderSets.Textured
private val shadow = PackShaderSets.ShadowDepth
private val instanced = PackShaderSets.Instanced
private val skinnedInstanced = PackShaderSets.SkinnedInstanced
private val skybox = PackShaderSets.Skybox
private val particle = PackShaderSets.Particle

/**
 * Fog colour and, in its alpha, density per world unit. Thin enough to read as depth at the
 * showcase's scene scale rather than as a wash over it.
 */
private val fogColor = Color(r = 0.62f, g = 0.68f, b = 0.76f, a = 0.02f)

/** Complete capability plan for the independently runnable engine demonstrations. */
internal val EngineShowcaseRenderPlan = RenderPlan(
    primary = ScenePipeline(PipelineKey.Primary, lit, VertexFormat.PositionNormalColor),
    contentFeatures = listOf(skyboxContentFeature(skybox), depthFogContentFeature(fogColor)),
    depthPrePassShaderSet = shadow,
    sceneDepthShaderSet = PackShaderSets.SceneDepth,
    scenePipelines = listOf(
        ScenePipeline(PipelineKey.Format(VertexFormat.PositionNormalColorSkin), skinned, VertexFormat.PositionNormalColorSkin),
        ScenePipeline(PipelineKey.Format(VertexFormat.PositionNormalColorUv), textured, VertexFormat.PositionNormalColorUv),
        ScenePipeline(PipelineKey.Instanced, instanced, VertexFormat.PositionNormalColor, variant = PipelineVariant.Instanced),
        ScenePipeline(PipelineKey.SkinnedInstanced, skinnedInstanced, VertexFormat.PositionNormalColorSkin, variant = PipelineVariant.Instanced),
        ScenePipeline(PipelineKey.Particle, particle, VertexFormat.PositionUv, variant = PipelineVariant.AlphaBlendedParticle),
    ),
)
