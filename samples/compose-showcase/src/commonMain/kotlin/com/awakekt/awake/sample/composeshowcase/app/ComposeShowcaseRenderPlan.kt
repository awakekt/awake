/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.composeshowcase.app

import com.awakekt.awake.asset.shaderpack.PackShaderSets
import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.PipelineKey

/**
 * Everything this sample shows is drawn by the UI pass, not the scene -- one triangle pipeline,
 * ASL-authored (see `PackShaderSets.Triangle`) rather than a hand-written `.wgsl` file.
 */
internal val ComposeShowcaseRenderPlan = RenderPlan(
    primary = ScenePipeline(
        key = PipelineKey.Primary,
        shaders = PackShaderSets.Triangle,
        vertexFormat = VertexFormat.PositionNormalColor,
    ),
)
