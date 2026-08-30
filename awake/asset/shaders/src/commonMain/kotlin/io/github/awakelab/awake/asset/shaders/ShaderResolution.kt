/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.asset.shaders

import io.github.awakelab.awake.render.pipeline.ShaderSource

/** A shader payload after it has been resolved for a graphics backend. */
sealed interface ResolvedShader {
    val entryPoint: String

    data class SpirV(
        val bytes: ByteArray,
        override val entryPoint: String,
    ) : ResolvedShader

    data class Wgsl(
        val source: String,
        override val entryPoint: String,
    ) : ResolvedShader
}

/** Resolves a source into the representation accepted by one graphics backend. */
interface RuntimeShaderResolver {
    suspend fun resolve(source: ShaderSource): ResolvedShader
}
