/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.asset.shadercompiler.RuntimeShaderCompiler
import com.awakekt.awake.asset.shaders.ResolvedShader
import com.awakekt.awake.render.pipeline.ShaderSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** A compiler that counts runs, so these assert on how often naga would have been invoked. */
class VulkanShaderResolverTest {

    private class CountingCompiler : RuntimeShaderCompiler {
        var compiles = 0
            private set

        override fun wgslToSpirv(wgsl: String): ByteArray {
            compiles++
            return wgsl.encodeToByteArray()
        }

        override fun validate(wgsl: String): String? = null
    }

    private val compiler = CountingCompiler()
    private val resolver = VulkanShaderResolver(compiler)

    private val program = "@vertex fn vertexMain() {} @fragment fn fragmentMain() {}"

    @Test
    fun theVertexAndFragmentStagesOfOneInlineSourceCompileOnce() = runTest {
        val vertex = resolver.resolve(ShaderSource.InlineText(program, entryPoint = "vertexMain"))
        val fragment = resolver.resolve(ShaderSource.InlineText(program, entryPoint = "fragmentMain"))

        assertEquals(1, compiler.compiles, "both stages share one compile of the same text")
        assertContentEquals(assertIs<ResolvedShader.SpirV>(vertex).bytes, assertIs<ResolvedShader.SpirV>(fragment).bytes)
    }

    @Test
    fun eachStageKeepsItsOwnEntryPointWhileSharingTheCompile() = runTest {
        val vertex = assertIs<ResolvedShader.SpirV>(
            resolver.resolve(ShaderSource.InlineText(program, entryPoint = "vertexMain")),
        )
        val fragment = assertIs<ResolvedShader.SpirV>(
            resolver.resolve(ShaderSource.InlineText(program, entryPoint = "fragmentMain")),
        )

        assertEquals("vertexMain", vertex.entryPoint)
        assertEquals("fragmentMain", fragment.entryPoint)
    }

    @Test
    fun resolvingAnUnchangedPreviewAgainSkipsTheCompiler() = runTest {
        repeat(3) { resolver.resolve(ShaderSource.InlineText(program, entryPoint = "fragmentMain")) }

        assertEquals(1, compiler.compiles)
    }

    @Test
    fun anEditedSourceCompilesAgain() = runTest {
        resolver.resolve(ShaderSource.InlineText(program, entryPoint = "fragmentMain"))
        val edited = resolver.resolve(ShaderSource.InlineText("$program // tweaked", entryPoint = "fragmentMain"))

        assertEquals(2, compiler.compiles, "a changed text is a different shader")
        assertContentEquals("$program // tweaked".encodeToByteArray(), assertIs<ResolvedShader.SpirV>(edited).bytes)
    }

    @Test
    fun precompiledBinariesNeverTouchTheCompiler() = runTest {
        val bytes = byteArrayOf(1, 2, 3)

        val resolved = resolver.resolve(ShaderSource.PrecompiledBinary(bytes, entryPoint = "main"))

        assertContentEquals(bytes, assertIs<ResolvedShader.SpirV>(resolved).bytes)
        assertEquals(0, compiler.compiles)
    }
}
