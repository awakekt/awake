// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.pipeline

import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat

/**
 * Common registry of 3D pipelines indexed by [VertexFormat] and variant shape.
 *
 * Parametrized by backend pipeline type [P] (e.g. Vulkan or WebGPU pipeline handle wrapper).
 */
class PipelineTable<P>(
    val primary: P,
    val byFormat: Map<VertexFormat, P> = emptyMap(),
    val wireframeByFormat: Map<VertexFormat, P> = emptyMap(),
    val backCulledByFormat: Map<VertexFormat, P> = emptyMap(),
    /** Alpha-blended, depth-tested, non-depth-writing companions -- the pipeline a
     * `DrawCall.transparent` draw resolves to. Empty means the app built none, and a transparent
     * draw falls back to its opaque pipeline rather than being dropped: it renders unblended,
     * which is wrong but visible, where dropping it looks like a missing mesh. */
    val transparentByFormat: Map<VertexFormat, P> = emptyMap(),
    val instancedByFormat: Map<VertexFormat, P> = emptyMap(),
    val skinnedInstancedByFormat: Map<VertexFormat, P> = emptyMap(),
    val particlePipelines: Map<VertexFormat, P> = emptyMap(),
)

/**
 * Common set of 4 UI shaders loaded for 2D quad, glyph, texture, and rounded-quad rendering.
 *
 * Parametrized by shader payload type [T] (e.g. `ShaderPair` for Vulkan SPIR-V pairs or `ByteArray` for WebGPU WGSL).
 */
data class UiShaderSet<T>(
    val quad: T,
    val glyph: T,
    val texture: T,
    val roundedQuad: T,
)
