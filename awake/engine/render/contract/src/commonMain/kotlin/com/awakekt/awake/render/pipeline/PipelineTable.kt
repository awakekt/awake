/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.pipeline.CullMode

/**
 * Common registry of 3D pipelines indexed by [VertexFormat] and variant shape.
 *
 * Parametrized by backend pipeline type [P] (e.g. Vulkan or WebGPU pipeline handle wrapper).
 */
class PipelineTable<P>(
    val primary: P,
    /** Which [VertexFormat] [primary] draws. Stated rather than derived because [P] is a
     * backend's own pipeline type and this layer cannot ask it. [resolve] needs it to treat the
     * primary pipeline as the fill entry for its own format. */
    val primaryFormat: VertexFormat,
    val byFormat: Map<VertexFormat, P> = emptyMap(),
    val wireframeByFormat: Map<VertexFormat, P> = emptyMap(),
    val backCulledByFormat: Map<VertexFormat, P> = emptyMap(),
    /** Alpha-blended, depth-tested, non-depth-writing companions -- the pipeline a
     * `RenderDrawCommand.transparent` draw resolves to. Empty means the app built none, and a transparent
     * draw falls back to its opaque pipeline rather than being dropped: it renders unblended,
     * which is wrong but visible, where dropping it looks like a missing mesh. */
    val transparentByFormat: Map<VertexFormat, P> = emptyMap(),
    val instancedByFormat: Map<VertexFormat, P> = emptyMap(),
    val skinnedInstancedByFormat: Map<VertexFormat, P> = emptyMap(),
    val particlePipelines: Map<VertexFormat, P> = emptyMap(),
)

/**
 * Common set of UI shaders loaded for 2D quad, glyph, texture, rounded-quad, and sampled-target rendering.
 *
 * Parametrized by shader payload type [T] (e.g. `ShaderPair` for Vulkan SPIR-V pairs or `ByteArray` for WebGPU WGSL).
 */
data class UiShaderSet<T>(
    val quad: T,
    val glyph: T,
    val texture: T,
    val roundedQuad: T,
    /** The two-sampler full-target pass used by destination-colour layer composites. */
    val targetComposite: T? = null,
)

/**
 * The pipeline a draw resolves to, or null when this table has no entry for [format].
 *
 * The single source of pipeline-selection precedence, shared by every backend. Both used to
 * answer this independently -- Vulkan in one `when`, WebGPU across two open-coded call sites --
 * which is precisely the class of duplicated decision the RHI boundary exists to remove. Getting
 * it wrong is invisible: the draw still lands in the right place at the right size, only with
 * the wrong state.
 *
 * Null means "not drawn", deliberately. A format with no entry is skipped rather than forced
 * through the primary pipeline: rendering wrong-format vertex data through the wrong pipeline is
 * worse than rendering nothing.
 *
 * @param P The backend's own pipeline type.
 * @receiver The frame's pipeline registry.
 * @param format The mesh's own vertex format -- the key everything here is indexed by.
 * @param cullMode The draw's per-mesh winding preference, from `MeshRenderer.cullMode`.
 * @param transparent Per-draw, from `RenderDrawCommand.transparent`. Ahead of [cullMode] because a
 * surface's blending matters more than its winding, and in practice the two never combine.
 * @param wireframe The renderer-wide debug override. Wins outright because it is something the
 * user asked for explicitly, and seeing a transparent surface's edges beats seeing it blended.
 * @return The pipeline to bind, or null when this table has no entry for [format].
 */
fun <P> PipelineTable<P>.resolve(
    format: VertexFormat,
    cullMode: CullMode = CullMode.None,
    transparent: Boolean = false,
    wireframe: Boolean = false,
): P? {
    val fill = if (format == primaryFormat) primary else byFormat[format]
    return when {
        wireframe -> wireframeByFormat[format] ?: fill
        transparent -> transparentByFormat[format] ?: fill
        cullMode == CullMode.Back -> backCulledByFormat[format] ?: fill
        else -> fill
    }
}
