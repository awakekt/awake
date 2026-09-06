/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.render.pipeline.UiShaderSet
import com.awakekt.awake.render.pipeline.PipelineTable as CommonPipelineTable

/** Every 3D [RenderPipeline] a WebGPU [com.awakekt.awake.webgpu.renderer.Renderer] can draw with. */
typealias PipelineTable = CommonPipelineTable<RenderPipeline>

/** The 4 UI shader byte arrays loaded for UI rendering in WebGPU. */
typealias UiShaderSources = UiShaderSet<ByteArray>
