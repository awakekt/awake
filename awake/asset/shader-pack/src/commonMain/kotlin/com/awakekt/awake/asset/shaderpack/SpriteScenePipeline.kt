/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaderpack

import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.PipelineVariant

/** Unlit atlas family with alpha blending, depth testing and no depth writes or shadow casting. */
fun spriteScenePipeline(): ScenePipeline = ScenePipeline(
    key = PipelineKey.Format(VertexFormat.PositionUv),
    shaders = PackShaderSets.Sprite,
    vertexFormat = VertexFormat.PositionUv,
    variant = PipelineVariant.AlphaBlended,
    materialBindings = GroupBindings.TexturedMaterial,
)
