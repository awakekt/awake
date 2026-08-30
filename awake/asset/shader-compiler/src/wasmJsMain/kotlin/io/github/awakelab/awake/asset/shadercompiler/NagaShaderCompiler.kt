/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shadercompiler

/** wasmJs actual: unsupported by design -- the browser's WebGPU takes WGSL text directly
 * (`createShaderModule`), so a SPIR-V compiler has nothing to do here. */
actual object NagaShaderCompiler : RuntimeShaderCompiler {
    actual override fun wgslToSpirv(wgsl: String): ByteArray =
        throw UnsupportedOperationException(
            "wasmJs has no SPIR-V path -- pass WGSL to createShaderModule directly.",
        )

    actual override fun validate(wgsl: String): String? =
        throw UnsupportedOperationException(
            "wasmJs has no naga binding -- the browser validates WGSL itself.",
        )
}
