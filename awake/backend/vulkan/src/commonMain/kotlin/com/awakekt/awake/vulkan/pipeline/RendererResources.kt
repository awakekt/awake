/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.render.pipeline.UiShaderSet
import com.awakekt.awake.render.pipeline.PipelineTable as CommonPipelineTable

/** Every 3D [RenderPipeline] a [com.awakekt.awake.vulkan.renderer.Renderer] can draw with. */
typealias PipelineTable = CommonPipelineTable<RenderPipeline>

/** The 4 UI shader pairs every [com.awakekt.awake.vulkan.renderer.Renderer] loads. */
typealias UiShaderPairs = UiShaderSet<ShaderPair>
