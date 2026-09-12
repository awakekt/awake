/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.sample.uishowcase.app

import com.awakekt.awake.asset.shaders.RenderPlan
import com.awakekt.awake.asset.shaders.ScenePipeline
import com.awakekt.awake.asset.shaders.ShaderSet
import com.awakekt.awake.asset.shaders.ShaderStages
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineKey
import com.awakekt.awake.render.pipeline.ShaderSource

/**
 * What the UI showcase renders with: one triangle pipeline, because everything it shows is drawn
 * by the UI pass rather than the scene.
 *
 * Declared once for both backends. Its two bootstraps used to each name the shader and vertex
 * format, and the wasmJs one had been calling a `WebGpuEngine` constructor that no longer existed
 * -- nothing compiles that source set on its own, so nothing said so.
 */
private val UI_TRIANGLE_SHADERS = ShaderSet(
    vulkan = uiTriangleStages(),
    webGpu = uiTriangleStages(),
)

private fun uiTriangleStages(): ShaderStages = ShaderStages.graphics(
    vertex = ShaderSource.InlineText(UI_TRIANGLE_WGSL, entryPoint = "vertexMain"),
    fragment = ShaderSource.InlineText(UI_TRIANGLE_WGSL, entryPoint = "fragmentMain"),
    bindingsByGroup = mapOf(0 to GroupBindings.UniformOnlyMaterial),
)

internal val UiShowcaseRenderPlan = RenderPlan(
    primary = ScenePipeline(
        key = PipelineKey.Primary,
        // Keep this two-attribute sample shader inline. A resource-path shader would require a
        // second browser asset deployment step and left the UI-only WebGPU app suspended while
        // fetching a file that is not emitted by the production executable bundle.
        shaders = UI_TRIANGLE_SHADERS,
        vertexFormat = VertexFormat.PositionColor,
        materialBindings = GroupBindings.UniformOnlyMaterial,
    ),
)

private const val UI_TRIANGLE_WGSL = """
struct Uniforms {
  mvp : mat4x4<f32>,
}
@binding(0) @group(0) var<uniform> uniforms : Uniforms;

struct VertexOutput {
  @builtin(position) position : vec4f,
  @location(0) color : vec3f,
}

@vertex
fn vertexMain(
  @location(0) inPosition : vec3f,
  @location(1) inColor : vec3f,
) -> VertexOutput {
  var output : VertexOutput;
  output.position = uniforms.mvp * vec4f(inPosition, 1.0);
  output.color = inColor;
  return output;
}

@fragment
fn fragmentMain(@location(0) color : vec3f) -> @location(0) vec4f {
  return vec4f(color, 1.0);
}
"""
