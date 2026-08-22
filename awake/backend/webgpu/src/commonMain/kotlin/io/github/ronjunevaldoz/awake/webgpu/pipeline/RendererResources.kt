// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.pipeline

import io.github.ronjunevaldoz.awake.render.pipeline.PipelineTable as CommonPipelineTable
import io.github.ronjunevaldoz.awake.render.pipeline.UiShaderSet

/** Every 3D [RenderPipeline] a WebGPU [io.github.ronjunevaldoz.awake.webgpu.renderer.Renderer] can draw with. */
typealias PipelineTable = CommonPipelineTable<RenderPipeline>

/** The 4 UI shader byte arrays loaded for UI rendering in WebGPU. */
typealias UiShaderSources = UiShaderSet<ByteArray>

