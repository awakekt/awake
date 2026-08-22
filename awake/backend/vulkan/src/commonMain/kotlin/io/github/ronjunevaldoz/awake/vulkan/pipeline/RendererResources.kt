// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.vulkan.pipeline

import io.github.ronjunevaldoz.awake.render.pipeline.PipelineTable as CommonPipelineTable
import io.github.ronjunevaldoz.awake.render.pipeline.UiShaderSet

/** Every 3D [RenderPipeline] a [io.github.ronjunevaldoz.awake.vulkan.renderer.Renderer] can draw with. */
typealias PipelineTable = CommonPipelineTable<RenderPipeline>

/** The 4 UI shader pairs every [io.github.ronjunevaldoz.awake.vulkan.renderer.Renderer] loads. */
typealias UiShaderPairs = UiShaderSet<ShaderPair>
