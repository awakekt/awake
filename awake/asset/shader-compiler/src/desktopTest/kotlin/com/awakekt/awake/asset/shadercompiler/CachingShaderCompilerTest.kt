/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shadercompiler

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/** Counts how often the real compiler would have run, which is what the cache exists to cut. */
class CachingShaderCompilerTest {

    private class CountingCompiler : RuntimeShaderCompiler {
        var compiles = 0
            private set
        var validations = 0
            private set

        /** Text that makes [wgslToSpirv] fail, as naga does for a shader it rejects. */
        var rejects: String? = null

        override fun wgslToSpirv(wgsl: String): ByteArray {
            compiles++
            if (wgsl == rejects) throw NagaException("rejected: $wgsl")
            return wgsl.encodeToByteArray()
        }

        override fun validate(wgsl: String): String? {
            validations++
            return if (wgsl == rejects) "rejected: $wgsl" else null
        }
    }

    private val counting = CountingCompiler()
    private val cache = CachingShaderCompiler(counting, maxEntries = 3)

    @Test
    fun compilingTheSameTextTwiceRunsTheCompilerOnce() {
        val first = cache.wgslToSpirv("fn a() {}")
        val second = cache.wgslToSpirv("fn a() {}")

        assertEquals(1, counting.compiles, "the second request must be served from the cache")
        assertSame(first, second)
    }

    @Test
    fun aVertexAndFragmentStageCutFromOneSourceCompileOnce() {
        val source = "@vertex fn vertexMain() {} @fragment fn fragmentMain() {}"

        repeat(2) { cache.wgslToSpirv(source) }

        assertEquals(1, counting.compiles)
    }

    @Test
    fun differentTextsAreCompiledSeparately() {
        val a = cache.wgslToSpirv("fn a() {}")
        val b = cache.wgslToSpirv("fn b() {}")

        assertEquals(2, counting.compiles)
        assertContentEquals("fn a() {}".encodeToByteArray(), a)
        assertContentEquals("fn b() {}".encodeToByteArray(), b)
    }

    @Test
    fun textsThatDifferByOneCharacterNeverShareAnEntry() {
        cache.wgslToSpirv("fn a() { let x = 1; }")
        val other = cache.wgslToSpirv("fn a() { let x = 2; }")

        assertEquals(2, counting.compiles)
        assertContentEquals("fn a() { let x = 2; }".encodeToByteArray(), other)
    }

    @Test
    fun theLeastRecentlyUsedTextIsEvictedFirst() {
        listOf("a", "b", "c").forEach(cache::wgslToSpirv)
        cache.wgslToSpirv("a") // a is now the most recently used; b is the oldest
        cache.wgslToSpirv("d") // evicts b
        assertEquals(4, counting.compiles)
        assertEquals(3, cache.size)

        cache.wgslToSpirv("a")
        cache.wgslToSpirv("c")
        cache.wgslToSpirv("d")
        assertEquals(4, counting.compiles, "a, c and d are still cached")

        cache.wgslToSpirv("b")
        assertEquals(5, counting.compiles, "b was evicted, so it compiles again")
    }

    @Test
    fun aRejectedTextIsNotCachedAndFailsEveryTime() {
        counting.rejects = "fn broken( {"

        repeat(2) { assertFailsWith<NagaException> { cache.wgslToSpirv("fn broken( {") } }

        assertEquals(2, counting.compiles, "a failure must be retried, not remembered")
        assertEquals(0, cache.size)
    }

    @Test
    fun aFixedSourceCompilesAfterTheBrokenOneFailed() {
        counting.rejects = "fn broken( {"
        assertFailsWith<NagaException> { cache.wgslToSpirv("fn broken( {") }

        val fixed = cache.wgslToSpirv("fn fixed() {}")

        assertContentEquals("fn fixed() {}".encodeToByteArray(), fixed)
    }

    @Test
    fun validationAlwaysAsksTheRealCompiler() {
        counting.rejects = "bad"

        assertNull(cache.validate("good"))
        assertEquals("rejected: bad", cache.validate("bad"))
        cache.validate("good")

        assertEquals(3, counting.validations)
        assertEquals(0, counting.compiles, "validating never compiles")
    }

    @Test
    fun theCacheKeepsAtLeastOneEntry() {
        assertFailsWith<IllegalArgumentException> { CachingShaderCompiler(counting, maxEntries = 0) }
    }

    @Test
    fun theRealNagaCompilerIsOnlyInvokedOncePerText() {
        val real = CachingShaderCompiler(NagaShaderCompiler)
        val wgsl = "@fragment fn fragmentMain() -> @location(0) vec4<f32> { return vec4<f32>(1.0); }"

        val first = real.wgslToSpirv(wgsl)
        val second = real.wgslToSpirv(wgsl)

        assertSame(first, second, "the same SPIR-V comes back without a second naga run")
        assertEquals(1, real.size)
    }
}
