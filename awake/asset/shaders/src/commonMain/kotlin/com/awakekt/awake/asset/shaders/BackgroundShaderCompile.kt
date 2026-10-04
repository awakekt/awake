/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.asset.shaders

import com.awakekt.awake.render.pipeline.ShaderProgram
import com.awakekt.awake.render.pipeline.ShaderSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Compiles a [ShaderProgram]'s two stages on [dispatcher], one program at a time, so a large shader
 * never holds up the thread that draws.
 *
 * The caller suspends while [compile] runs elsewhere and resumes on its own dispatcher with the
 * result, so a render thread can ask for a compile between frames and keep drawing until it
 * finishes. Compiles are serialised: [compile] may hold state that is not thread-safe, such as a
 * compiled-shader cache, and an editor re-submitting a shader on every keystroke queues behind the
 * one in flight and not alongside it.
 *
 * A failing compile rethrows what [compile] threw, and the next one still runs.
 */
class BackgroundShaderCompile<T>(
    private val dispatcher: CoroutineDispatcher,
    private val compile: suspend (vertex: ShaderSource, fragment: ShaderSource) -> T,
) {
    private val turn = Mutex()

    /** Compiles [program] on the dispatcher, after any compile already running. */
    suspend fun compile(program: ShaderProgram): T = turn.withLock {
        withContext(dispatcher) { compile(program.vertex, program.fragment) }
    }

    /**
     * Compiles [program] on the calling thread, taking its turn with the others. This holds the caller
     * up for as long as the compile takes, which is what [compile] exists to avoid.
     */
    suspend fun compileInPlace(program: ShaderProgram): T = turn.withLock {
        compile(program.vertex, program.fragment)
    }
}
