// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.engine.app

import io.github.ronjunevaldoz.awake.asset.shaders.ShaderSet
import io.github.ronjunevaldoz.awake.engine.platform.lifecycle.AwakeAppLifecycle
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.webgpu.application.WebGpuEngine

actual class AwakeApplication actual constructor(
    shaderSet: ShaderSet,
    vertexFormat: VertexFormat,
    appLifecycle: AwakeAppLifecycle,
    additionalPipelines: Map<VertexFormat, ShaderSet>,
    wireframeSupport: Boolean,
    shadowShaderSet: ShaderSet?,
) : WebGpuEngine(
    shaderSet = shaderSet,
    vertexFormat = vertexFormat,
    appLifecycle = appLifecycle,
    wireframeSupport = wireframeSupport,
    additionalPipelines = additionalPipelines,
    shadowShaderSet = shadowShaderSet,
)
