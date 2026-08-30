/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shadercompiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Real JNI round-trips through the Rust naga library -- no mocks, the actual dylib. */
class NagaShaderCompilerTest {

    private val validWgsl = """
        @vertex fn vertexMain(@location(0) p : vec3f) -> @builtin(position) vec4f {
          return vec4f(p, 1.0);
        }
        @fragment fn fragmentMain() -> @location(0) vec4f { return vec4f(1.0); }
    """.trimIndent()

    @Test
    fun compilesValidWgslToSpirv() {
        val spirv = NagaShaderCompiler.wgslToSpirv(validWgsl)
        assertTrue(spirv.size > 20, "module too small: ${spirv.size} bytes")
        assertEquals(0, spirv.size % 4, "SPIR-V is a stream of 32-bit words")
        val magic = (spirv[0].toInt() and 0xFF) or
            ((spirv[1].toInt() and 0xFF) shl 8) or
            ((spirv[2].toInt() and 0xFF) shl 16) or
            ((spirv[3].toInt() and 0xFF) shl 24)
        assertEquals(0x07230203, magic, "SPIR-V magic number, little-endian")
    }

    @Test
    fun invalidWgslThrowsWithNagaDiagnostic() {
        val failure = assertFailsWith<NagaException> {
            NagaShaderCompiler.wgslToSpirv("fn broken( {")
        }
        assertTrue(failure.message!!.isNotBlank())
    }

    @Test
    fun validateReturnsNullForValidAndDiagnosticForInvalid() {
        assertNull(NagaShaderCompiler.validate(validWgsl))
        val diagnostic = NagaShaderCompiler.validate("@fragment fn f() -> f32 { return 1; }")
        assertTrue(!diagnostic.isNullOrBlank())
    }
}
