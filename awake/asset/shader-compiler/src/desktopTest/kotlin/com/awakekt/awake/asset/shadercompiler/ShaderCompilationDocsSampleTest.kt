/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import com.awakekt.awake.asset.shaderdsl.TriangleShader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** The "Shader compilation" guide: validating WGSL, compiling it to SPIR-V, and the error a bad shader raises. */
class ShaderCompilationDocsSampleTest {

    private val wgsl = TriangleShader.emitWgsl()

    @Test
    fun validWgslValidatesAndCompiles() {
        // --8<-- [start:compile]
        val problem: String? = NagaShaderCompiler.validate(wgsl)
        val spirv: ByteArray = NagaShaderCompiler.wgslToSpirv(wgsl)
        // --8<-- [end:compile]

        assertNull(problem)
        assertEquals(0x07230203, spirv.littleEndianWord(), "SPIR-V magic number")
    }

    @Test
    fun invalidWgslReportsWhatIsWrong() {
        var reported: String? = null
        // --8<-- [start:error]
        try {
            NagaShaderCompiler.wgslToSpirv("fn broken( {")
        } catch (failure: NagaException) {
            reported = failure.message // naga's diagnostic, with the line it points at
        }
        // --8<-- [end:error]

        assertNotNull(reported)
        assertNotNull(NagaShaderCompiler.validate("@fragment fn main() -> f32 { return 1; }"))
    }

    private fun ByteArray.littleEndianWord(): Int =
        (this[0].toInt() and 0xFF) or ((this[1].toInt() and 0xFF) shl 8) or
            ((this[2].toInt() and 0xFF) shl 16) or ((this[3].toInt() and 0xFF) shl 24)
}
