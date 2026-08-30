/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shadercompiler

import awake.naga.awake_naga_free
import awake.naga.awake_naga_free_string
import awake.naga.awake_naga_validate
import awake.naga.awake_naga_wgsl_to_spirv
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.toKString
import kotlinx.cinterop.value
import platform.posix.size_tVar

/** iOS cinterop actual over the static library's C ABI (see `include/awake_naga.h`). */
@OptIn(ExperimentalForeignApi::class)
actual object NagaShaderCompiler : RuntimeShaderCompiler {
    actual override fun wgslToSpirv(wgsl: String): ByteArray = memScoped {
        val outLen = alloc<size_tVar>()
        val outError = alloc<CPointerVar<ByteVar>>()
        val buffer = awake_naga_wgsl_to_spirv(wgsl, outLen.ptr, outError.ptr)
        if (buffer == null) {
            val message = outError.value?.toKString() ?: "unknown naga error"
            awake_naga_free_string(outError.value)
            throw NagaException(message)
        }
        val bytes = buffer.readBytes(outLen.value.toInt())
        awake_naga_free(buffer, outLen.value)
        bytes
    }

    actual override fun validate(wgsl: String): String? {
        val message = awake_naga_validate(wgsl) ?: return null
        val text = message.toKString()
        awake_naga_free_string(message)
        return text
    }
}
