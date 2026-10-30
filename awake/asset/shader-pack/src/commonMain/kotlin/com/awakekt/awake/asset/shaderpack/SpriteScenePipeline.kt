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
import kotlin.jvm.JvmOverloads

/**
 * Unlit atlas family with alpha blending (plus an optional additive companion pipeline when
 * [buildAdditive] is `true`), depth testing and no depth writes or shadow casting.
 */
@JvmOverloads
fun spriteScenePipeline(buildAdditive: Boolean = true): ScenePipeline = ScenePipeline(
    key = PipelineKey.Format(VertexFormat.PositionUv),
    shaders = PackShaderSets.Sprite,
    vertexFormat = VertexFormat.PositionUv,
    variant = PipelineVariant.AlphaBlended,
    materialBindings = GroupBindings.TexturedMaterial,
    buildAdditive = buildAdditive,
)

/**
 * Distance-field text placed in a scene: glyph quads in [VertexFormat.PositionUvColor], alpha
 * blended over the scene, depth tested and not written, so text sorts among the frame's other
 * transparent draws and hides behind what stands in front of it.
 */
fun worldTextScenePipeline(): ScenePipeline = ScenePipeline(
    key = PipelineKey.Format(VertexFormat.PositionUvColor),
    shaders = PackShaderSets.WorldText,
    vertexFormat = VertexFormat.PositionUvColor,
    variant = PipelineVariant.AlphaBlended,
    materialBindings = GroupBindings.TexturedMaterial,
)
