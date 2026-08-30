/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.pipeline

import io.github.awakelab.awake.asset.shaders.ResolvedShader
import io.github.awakelab.awake.asset.shaders.RuntimeShaderResolver
import io.github.awakelab.awake.render.pipeline.ShaderSource
import io.github.awakelab.awake.render.pipeline.entryPoint
import io.github.awakelab.awake.asset.shaders.resolveBytes

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
