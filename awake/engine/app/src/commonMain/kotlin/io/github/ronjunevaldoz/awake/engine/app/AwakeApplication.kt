// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.engine.app

import io.github.ronjunevaldoz.awake.engine.platform.lifecycle.AwakeAppLifecycle
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.asset.shaders.ShaderSet

/**
 * The one public entry point a consumer builds against: picks `VulkanEngine`
 * (android/ios/desktop) or `WebGpuEngine` (wasmJs) per target, so `commonMain` never
 * imports a backend module directly. See
 * docs/tasks/2026-08-09-application-seam-and-module-naming-plan.md, Part 1.
 *
 * [additionalPipelines] is honored on both backends -- the wasmJs `actual` forwards it to
 * `WebGpuEngine`'s own per-vertex-format registry.
 *
 * [shadowShaderSet] is Vulkan-only. It is forwarded, not ignored, and `WebGpuEngine` rejects a
 * non-null value: that backend records a shadow depth pass but has no shader able to sample it,
 * so accepting one would cost a pass per frame and change nothing on screen. A `commonMain` call
 * site that also targets web must leave it `null`.
 */
expect class AwakeApplication(
    shaderSet: ShaderSet,
    vertexFormat: VertexFormat = VertexFormat.PositionColorUv,
    appLifecycle: AwakeAppLifecycle,
    additionalPipelines: Map<VertexFormat, ShaderSet> = emptyMap(),
    wireframeSupport: Boolean = false,
    shadowShaderSet: ShaderSet? = null,
)
