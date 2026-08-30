/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.pipeline

import io.github.awakelab.awake.asset.shadercompiler.NagaShaderCompiler
import io.github.awakelab.awake.asset.shaders.ResolvedShader
import io.github.awakelab.awake.asset.shaders.RuntimeShaderResolver
import io.github.awakelab.awake.render.pipeline.ShaderSource
import io.github.awakelab.awake.render.pipeline.entryPoint
import io.github.awakelab.awake.asset.shaders.resolveBytes

/** Resolves Vulkan shader sources to SPIR-V, compiling WGSL through the native Naga binding. */
class VulkanShaderResolver : RuntimeShaderResolver {
    private val compiledWgsl = mutableMapOf<String, ByteArray>()

    override suspend fun resolve(source: ShaderSource): ResolvedShader = when (source) {
        is ShaderSource.PrecompiledBinary -> ResolvedShader.SpirV(source.bytes, source.entryPoint)
        is ShaderSource.InlineText -> ResolvedShader.SpirV(
            NagaShaderCompiler.wgslToSpirv(source.sourceCode),
            source.entryPoint,
        )
        is ShaderSource.ResourcePath -> if (source.path.endsWith(".spv")) {
            ResolvedShader.SpirV(source.resolveBytes(), source.entryPoint)
        } else {
            val bytes = compiledWgsl.getOrPut(source.path) {
                NagaShaderCompiler.wgslToSpirv(source.resolveBytes().decodeToString())
            }
            ResolvedShader.SpirV(bytes, source.entryPoint)
        }
    }
}

internal fun ResolvedShader.requireSpirV(): ByteArray = when (this) {
    is ResolvedShader.SpirV -> bytes
    is ResolvedShader.Wgsl -> error("Vulkan shader resolver returned WGSL instead of SPIR-V.")
}
