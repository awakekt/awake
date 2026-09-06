/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.asset.shadercompiler.NagaShaderCompiler
import com.awakekt.awake.asset.shaders.ResolvedShader
import com.awakekt.awake.asset.shaders.RuntimeShaderResolver
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.pipeline.entryPoint

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
