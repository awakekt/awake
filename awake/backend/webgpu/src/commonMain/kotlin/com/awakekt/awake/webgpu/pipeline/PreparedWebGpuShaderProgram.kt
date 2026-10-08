/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.render.pipeline.PreparedShaderProgram
import com.awakekt.awake.render.pipeline.ShaderProgram
import com.awakekt.awake.render.pipeline.ShaderReplacementException
import io.ygdrasil.webgpu.GPURenderPipeline

/** Owns validated candidates until a swap transfers them, or the caller abandons preparation. */
internal class PreparedWebGpuShaderProgram(
    val owner: WebGpuShaderReplacement,
    override val program: ShaderProgram,
    private val candidates: MutableMap<Any, Result<GPURenderPipeline>>,
) : PreparedShaderProgram {
    private var closed = false

    fun take(targets: Set<Any>): Map<Any, GPURenderPipeline> {
        if (closed) throw ShaderReplacementException("That preparation was already consumed or closed.")
        // Check every target before transferring any resource, so a failed candidate is atomic.
        val selected = targets.associateWith { identity ->
            val candidate = candidates[identity]
                ?: throw ShaderReplacementException("A pipeline was instantiated after preparation; prepare the program again.")
            candidate.getOrThrow()
        }
        targets.forEach { candidates.remove(it) }
        close()
        return selected
    }

    override fun close() {
        candidates.values.forEach { it.getOrNull()?.close() }
        candidates.clear()
        closed = true
    }
}
