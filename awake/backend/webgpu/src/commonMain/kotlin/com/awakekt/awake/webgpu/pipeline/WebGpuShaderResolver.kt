/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.asset.shaders.ResolvedShader
import com.awakekt.awake.asset.shaders.RuntimeShaderResolver
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.pipeline.entryPoint

/** Resolves WebGPU shader sources to WGSL text, which WebGPU compiles natively. */
class WebGpuShaderResolver : RuntimeShaderResolver {
    override suspend fun resolve(source: ShaderSource): ResolvedShader = when (source) {
        is ShaderSource.InlineText -> ResolvedShader.Wgsl(source.sourceCode, source.entryPoint)
        is ShaderSource.ResourcePath -> {
            check(!source.path.endsWith(".spv")) {
                "WebGPU cannot consume SPIR-V resource '${source.path}'."
            }
            ResolvedShader.Wgsl(source.resolveBytes().decodeToString(), source.entryPoint)
        }
        is ShaderSource.PrecompiledBinary ->
            error("WebGPU cannot consume precompiled SPIR-V bytes; provide WGSL instead.")
    }
}
