/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import com.awakekt.awake.asset.shaderdocument.ShaderDocumentException
import com.awakekt.awake.asset.shaderdocument.ShaderDocuments
import com.awakekt.awake.core.math.ClipSpace
import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every shader document that passes its checks emits WGSL naga accepts, in both clip spaces.
 *
 * This is the guarantee that matters for WebGPU, which does not report a shader it rejects until the
 * frame that draws it: a published project's document has to be valid by construction. Hand-written
 * documents cover each surface; seeded random ones cover combinations nobody would write by hand.
 */
class ShaderDocumentCompileTest {
    private fun assertValid(wgsl: String, what: String) {
        assertNull(NagaShaderCompiler.validate(wgsl), "naga rejected $what:\n$wgsl")
        assertTrue(NagaShaderCompiler.wgslToSpirv(wgsl).isNotEmpty(), "no SPIR-V for $what")
    }

    @Test
    fun handWrittenDocumentsValidateForEveryClipSpace() {
        DOCUMENTS.forEach { (name, json) ->
            val compiled = ShaderDocuments.compile(json)
            ClipSpace.entries.forEach { clipSpace -> assertValid(compiled.emitWgsl(clipSpace), "'$name' for $clipSpace") }
        }
    }

    @Test
    fun randomDocumentsThatPassTheirChecksValidate() = SEEDS.forEach { seed -> randomDocumentsValidate(seed) }

    private fun randomDocumentsValidate(seed: Int) {
        val generator = RandomShaderDocuments(seed)
        var compiled = 0
        repeat(RANDOM_DOCUMENTS) { i ->
            val document = generator.next()
            val result = try {
                ShaderDocuments.compile(document)
            } catch (expected: ShaderDocumentException) {
                // A random document can fold a constant to a division by zero, or exceed a limit; the
                // checker refusing it is the right outcome, and nothing reaches naga.
                null
            }
            result?.let { ok ->
                compiled++
                ClipSpace.entries.forEach { clipSpace ->
                    assertValid(ok.emitWgsl(clipSpace), "random document $i of seed $seed for $clipSpace:\n${ShaderDocuments.encode(document)}")
                }
            }
        }
        assertTrue(compiled > RANDOM_DOCUMENTS / 2, "only $compiled of $RANDOM_DOCUMENTS random documents of seed $seed compiled, so naga saw too few")
    }

    private companion object {
        const val RANDOM_DOCUMENTS = 400

        /** Seeds whose documents once found a bug: 7 and 42 generated a loop counter named `i64` and `i32`. */
        val SEEDS = listOf(7, 42, 1_337)

        val DOCUMENTS = mapOf(
            "sky" to """
                {"name":"Sky","surface":"background",
                 "parameters":[{"name":"top","type":"color","default":[0.1,0.3,0.8,1]},{"name":"bottom","type":"color","default":[0.9,0.5,0.3,1]}],
                 "fragment":{"color":{"op":"call","fn":"mix","args":[{"op":"param","name":"bottom"},{"op":"param","name":"top"},
                   {"op":"call","fn":"saturate","args":[{"op":"swizzle","value":{"op":"input","input":"viewDirection"},"components":"y"}]}]}}}
            """,
            "tint" to """{"name":"Tint","surface":"overlay","fragment":{"color":{"op":"const","value":[1,0,0,0.25]}}}""",
            "one parameter" to """
                {"name":"One","surface":"overlay","parameters":[{"name":"alpha","type":"float","default":[0.5]}],
                 "fragment":{"color":{"op":"vec4","args":[{"op":"input","input":"screenUv"},{"op":"const","value":[0]},{"op":"param","name":"alpha"}]}}}
            """,
            "water" to """
                {"name":"Water","surface":"plane","blend":"additive","plane":{"size":[10,6],"segments":8},
                 "parameters":[{"name":"amplitude","type":"float","default":[0.2]},{"name":"tint","type":"color","default":[0,0.3,0.6,0.8]}],
                 "textures":[{"name":"ripples"},{"name":"foam"}],
                 "vertex":{"displacement":{"op":"mul","a":{"op":"call","fn":"sin","args":[{"op":"add","a":{"op":"input","input":"time"},
                   "b":{"op":"swizzle","value":{"op":"input","input":"worldPosition"},"components":"x"}}]},"b":{"op":"param","name":"amplitude"}}},
                 "fragment":{"statements":[
                   {"statement":"var","name":"glow","value":{"op":"const","value":[0]}},
                   {"statement":"for","counter":"i","from":0,"until":3,"body":[
                     {"statement":"set","name":"glow","value":{"op":"add","a":{"op":"local","name":"glow"},
                       "b":{"op":"swizzle","value":{"op":"sample","texture":"foam","uv":{"op":"mul","a":{"op":"input","input":"uv"},"b":{"op":"local","name":"i"}}},"components":"r"}}}]},
                   {"statement":"discard_if","condition":{"op":"or","a":{"op":"lt","a":{"op":"local","name":"glow"},"b":{"op":"const","value":[0.01]}},
                     "b":{"op":"not","value":{"op":"gt","a":{"op":"call","fn":"dot","args":[{"op":"neg","value":{"op":"input","input":"viewDirection"}},{"op":"input","input":"normal"}]},"b":{"op":"const","value":[0]}}}}}],
                 "color":{"op":"mul","a":{"op":"param","name":"tint"},"b":{"op":"sample","texture":"ripples","uv":{"op":"input","input":"screenUv"}}}}}
            """,
        )
    }
}
