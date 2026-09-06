/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/**
 * Where one shader stage's code comes from.
 *
 * Here rather than in `awake:asset:shaders` because [PipelineSpec] holds one: a spec used to name
 * a resource path as a `String`, which meant a shader carrying its own source could not be
 * described at all. That module depends on this one, so the type has to live on this side for
 * the spec to reference it.
 *
 * `resolveBytes` turns any variant into the raw bytes a backend's shader-module call needs, and
 * stays in `awake:asset:shaders` because reading a resource is that module's concern.
 */
sealed interface ShaderSource {
    /** Read from disk/assets at load time. */
    data class ResourcePath(
        val path: String,
        val entryPoint: String = "main",
    ) : ShaderSource

    /**
     * Bytes a caller already holds.
     *
     * Identity-compared, unlike its two siblings -- a `ByteArray` has no structural equality. A
     * [PipelineSpec] built from one therefore never dedups in `PipelineRegistry`, which is
     * acceptable only because nothing constructs a spec this way today.
     */
    class PrecompiledBinary(
        val bytes: ByteArray,
        val entryPoint: String = "main",
    ) : ShaderSource

    /**
     * WGSL held in memory rather than loaded -- an ASL definition's `emitWgsl()`, so a shader
     * ships as compiled-in Kotlin instead of a resource. The only variant that needs no file on
     * any platform, which is what lets iOS and wasm skip the per-app resource copy their loaders
     * would otherwise require.
     */
    data class InlineText(
        val sourceCode: String,
        val entryPoint: String = "main",
    ) : ShaderSource
}

/** [ShaderSource]'s own entry point, regardless of variant -- so a caller reading it needs no
 * `when` per variant. */
val ShaderSource.entryPoint: String
    get() = when (this) {
        is ShaderSource.ResourcePath -> entryPoint
        is ShaderSource.PrecompiledBinary -> entryPoint
        is ShaderSource.InlineText -> entryPoint
    }
