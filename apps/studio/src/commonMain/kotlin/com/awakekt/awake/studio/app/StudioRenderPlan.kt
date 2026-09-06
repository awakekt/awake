/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.studio.app

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaderpack.skyboxContentFeature
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.PipelineKey

private val StudioShaders = PackShaderSets.LitShadow
private val StudioShadowShaders = PackShaderSets.ShadowDepth
private val StudioSkyboxShaders = PackShaderSets.Skybox
private val StudioSkinnedShaders = PackShaderSets.Skinned
private val StudioTexturedShaders = PackShaderSets.Textured

/**
 * The renderer contract for Studio's editor fixture and generic editor gizmos.
 *
 * Declared once here rather than per bootstrap. It supports standard PBR models, skinned glTF
 * meshes, textured meshes, and editor viewport features.
 */
internal val StudioRenderPlan = RenderPlan(
    primary = ScenePipeline(
        key = PipelineKey.Primary,
        shaders = StudioShaders,
        vertexFormat = VertexFormat.PositionNormalColor,
    ),
    contentFeatures = listOf(skyboxContentFeature(StudioSkyboxShaders)),
    depthPrePassShaderSet = StudioShadowShaders,
    scenePipelines = listOf(
        ScenePipeline(
            key = PipelineKey.Format(VertexFormat.PositionNormalColorSkin),
            shaders = StudioSkinnedShaders,
            vertexFormat = VertexFormat.PositionNormalColorSkin,
        ),
        ScenePipeline(
            key = PipelineKey.Format(VertexFormat.PositionNormalColorUv),
            shaders = StudioTexturedShaders,
            vertexFormat = VertexFormat.PositionNormalColorUv,
        ),
    ),
)
