/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.sample.composeshowcase.app

import io.github.awakelab.awake.asset.shaderpack.PackShaderSets
import io.github.awakelab.awake.asset.shaders.RenderPlan
import io.github.awakelab.awake.asset.shaders.ScenePipeline
import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.render.pipeline.PipelineKey

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
