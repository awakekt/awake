/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.render.pipeline.ShaderProgram
import com.awakekt.awake.render.pipeline.ShaderSource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The compile runs on its own dispatcher, never on the caller's, and one at a time. The background
 * dispatcher here holds what it is given until the test runs it, which stands in for a compile that
 * is still going while the caller keeps drawing frames.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundShaderCompileTest {

    /** A dispatcher that queues work and runs it only when [runPending] is called. */
    private class ManualDispatcher : CoroutineDispatcher() {
        private val queue = ArrayDeque<Runnable>()
        val pending: Int get() = queue.size

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            queue.addLast(block)
        }

        fun runPending() {
            while (queue.isNotEmpty()) queue.removeFirst().run()
        }
    }

    private val background = ManualDispatcher()

    private fun program(name: String) = ShaderProgram(
        vertex = ShaderSource.InlineText("// $name vertex", "vertexMain"),
        fragment = ShaderSource.InlineText("// $name fragment", "fragmentMain"),
        bindingsByGroup = null,
    )

    private fun ShaderSource.text() = (this as ShaderSource.InlineText).sourceCode

    @Test
    fun theCallerKeepsRunningWhileTheCompileIsInFlight() = runTest {
        var frames = 0
        val compiled = mutableListOf<String>()
        val compiler = BackgroundShaderCompile(background) { vertex, _ ->
            compiled += vertex.text()
            "spirv"
        }

        val result = async { compiler.compile(program("big")) }
        runCurrent() // the caller has asked for the compile; the background has not started it
        repeat(10) { frames++ } // frames drawn while it is in flight

        assertEquals(10, frames, "the caller was never blocked")
        assertFalse(result.isCompleted, "the compile has not finished")
        assertEquals(emptyList(), compiled, "and it has not run on the caller's side either")
        assertEquals(1, background.pending, "it is waiting on the background dispatcher")

        background.runPending() // the background gets to run
        runCurrent()
        assertEquals("spirv", result.await())
        assertEquals(listOf("// big vertex"), compiled)
    }

    @Test
    fun theCompileRunsOnTheBackgroundDispatcherNotTheCallers() = runTest {
        var ranOnBackground = false
        val compiler = BackgroundShaderCompile(background) { _, _ ->
            ranOnBackground = currentCoroutineContext()[ContinuationInterceptor] === background
            "spirv"
        }

        val result = async { compiler.compile(program("a")) }
        runCurrent()
        background.runPending()
        runCurrent()
        result.await()

        assertTrue(ranOnBackground)
    }

    @Test
    fun compilesRunOneAtATime() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val started = mutableListOf<String>()
        val compiler = BackgroundShaderCompile(background) { vertex, _ ->
            started += vertex.text()
            if (vertex.text().contains("first")) firstGate.await()
            vertex.text()
        }

        val first = async { compiler.compile(program("first")) }
        val second = async { compiler.compile(program("second")) }
        runCurrent()
        background.runPending()
        runCurrent()

        assertEquals(listOf("// first vertex"), started, "the second waits for the first")
        assertFalse(second.isCompleted)

        firstGate.complete(Unit)
        repeat(3) {
            runCurrent()
            background.runPending()
        }
        runCurrent()

        assertEquals("// first vertex", first.await())
        assertEquals("// second vertex", second.await())
        assertEquals(listOf("// first vertex", "// second vertex"), started)
    }

    @Test
    fun anInPlaceCompileRunsOnTheCallerAndWaitsItsTurnBehindABackgroundOne() = runTest {
        val gate = CompletableDeferred<Unit>()
        val order = mutableListOf<String>()
        var inPlaceOnBackground = true
        val compiler = BackgroundShaderCompile(background) { vertex, _ ->
            val name = vertex.text()
            order += "start $name"
            if (name.contains("slow")) gate.await()
            if (name.contains("here")) inPlaceOnBackground = currentCoroutineContext()[ContinuationInterceptor] === background
            order += "end $name"
            name
        }

        val slow = async { compiler.compile(program("slow")) }
        runCurrent()
        background.runPending()
        runCurrent()
        val here = async { compiler.compileInPlace(program("here")) }
        runCurrent()

        assertEquals(listOf("start // slow vertex"), order, "the in-place compile waits for the one in flight")

        gate.complete(Unit)
        repeat(3) {
            background.runPending()
            runCurrent()
        }
        slow.await()
        here.await()

        assertFalse(inPlaceOnBackground, "it compiled on the caller's side, not the background dispatcher")
        assertEquals(
            listOf("start // slow vertex", "end // slow vertex", "start // here vertex", "end // here vertex"),
            order,
        )
    }

    @Test
    fun aFailingCompileRethrowsAndTheNextOneStillRuns() = runTest {
        var calls = 0
        val compiler = BackgroundShaderCompile(background) { _, _ ->
            calls++
            if (calls == 1) error("does not compile")
            "spirv"
        }

        val broken = async { runCatching { compiler.compile(program("broken")) } }
        runCurrent()
        background.runPending()
        runCurrent()
        assertFailsWith<IllegalStateException> { broken.await().getOrThrow() }

        val fixed = async { compiler.compile(program("fixed")) }
        runCurrent()
        background.runPending()
        runCurrent()
        assertEquals("spirv", fixed.await(), "a failed compile must not leave the next one waiting")
    }
}
