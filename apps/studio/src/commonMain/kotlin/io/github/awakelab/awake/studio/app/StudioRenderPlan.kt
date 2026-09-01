/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.studio.app

import io.github.awakelab.awake.asset.shaderpack.skyboxContentFeature
import io.github.awakelab.awake.asset.shaders.RenderPlan
import io.github.awakelab.awake.asset.shaders.ScenePipeline
import io.github.awakelab.awake.asset.shaderpack.PackShaderSets
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.render.pipeline.PipelineKey

private val StudioShaders = PackShaderSets.LitShadow
private val StudioShadowShaders = PackShaderSets.ShadowDepth
private val StudioSkyboxShaders = PackShaderSets.Skybox

/**
 * The minimal renderer contract for Studio's editor fixture and generic editor gizmos.
 *
 * Declared once here rather than per bootstrap. It used to be written out in both
 * `StudioVulkanBootstrap.kt` (appMain) and `StudioWebGpuBootstrap.kt` (wasmJsMain) -- source sets
 * that cannot see each other -- and the two had already drifted apart. Demonstration-specific
 * pipelines belong to `samples:engine-showcase`.
 */
internal val StudioRenderPlan = RenderPlan(
    primary = ScenePipeline(
        key = PipelineKey.Primary,
        shaders = StudioShaders,
        vertexFormat = VertexFormat.PositionNormalColor,
    ),
    // Sample consumer opt-in: the procedural sky is viewport policy, so the editor supplies the
    // feature explicitly. `Renderer.showEnvironment` then controls whether it draws each frame.
    contentFeatures = listOf(skyboxContentFeature(StudioSkyboxShaders)),
    depthPrePassShaderSet = StudioShadowShaders,
)
