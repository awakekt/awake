/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.render.pipeline.ShaderSource

/** A shader payload after it has been resolved for a graphics backend. */
sealed interface ResolvedShader {
    /** Entry point function name executed by the GPU pipeline. */
    val entryPoint: String

    /**
     * Precompiled SPIR-V binary bytecode for Vulkan backends.
     *
     * @property bytes Raw binary SPIR-V instructions.
     * @property entryPoint Entry point function name executed by the pipeline.
     */
    data class SpirV(
        /** Raw binary SPIR-V instructions. */
        val bytes: ByteArray,
        override val entryPoint: String,
    ) : ResolvedShader

    /**
     * WebGPU Shading Language (WGSL) text source for WebGPU backends.
     *
     * @property source Raw WGSL source text.
     * @property entryPoint Entry point function name executed by the pipeline.
     */
    data class Wgsl(
        /** Raw WGSL source text. */
        val source: String,
        override val entryPoint: String,
    ) : ResolvedShader
}

/** Resolves a source into the representation accepted by one graphics backend. */
interface RuntimeShaderResolver {
    /**
     * Resolves an abstract [ShaderSource] into the backend's native bytecode or text representation.
     *
     * @param source The abstract shader source reference.
     * @return Resolved shader payload ready for hardware pipeline compilation.
     */
    suspend fun resolve(source: ShaderSource): ResolvedShader
}
