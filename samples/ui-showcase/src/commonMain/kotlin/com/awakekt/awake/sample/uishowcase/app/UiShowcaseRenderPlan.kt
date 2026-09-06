/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.app

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.asset.shaders.shaderSet
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.PipelineKey

/**
 * What the UI showcase renders with: one triangle pipeline, because everything it shows is drawn
 * by the UI pass rather than the scene.
 *
 * Declared once for both backends. Its two bootstraps used to each name the shader and vertex
 * format, and the wasmJs one had been calling a `WebGpuEngine` constructor that no longer existed
 * -- nothing compiles that source set on its own, so nothing said so.
 */
internal val UiShowcaseRenderPlan = RenderPlan(
    primary = ScenePipeline(
        key = PipelineKey.Primary,
        shaders = shaderSet("triangle"),
        vertexFormat = VertexFormat.PositionColor,
    ),
)
