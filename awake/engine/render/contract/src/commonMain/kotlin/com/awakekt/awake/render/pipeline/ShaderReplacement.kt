/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.pipeline

/**
 * Replaces running pipelines' shaders with shaders built at runtime, with no restart: a shader
 * editor's live preview, a material variant picked at runtime.
 *
 * Optional: query it with `device.capability(ShaderReplacement)`. Vulkan has it; WebGPU does not
 * yet, because its pipelines cannot be swapped in place.
 */
interface ShaderReplacement : GpuCapability {
    /**
     * Rebuilds every pipeline now running [old]'s shaders with [new]'s, and returns how many.
     *
     * Call on the render thread between frames, as content features are attached. [new] compiles and
     * every replacement pipeline is built before anything changes; then the GPU is drained, the
     * replacements swapped in and the old pipelines destroyed. Everything holding a pipeline draws
     * with [new] from the next frame. [new] is what a later call replaces.
     *
     * @throws ShaderReplacementException when [new] does not compile, or binds differently from a
     * pipeline running [old]. Nothing has changed.
     */
    suspend fun replace(old: ShaderProgram, new: ShaderProgram): Int

    companion object : GpuCapabilityKind<ShaderReplacement>
}

/** A pipeline's two shader stages, and what each of its groups binds. */
data class ShaderProgram(
    val vertex: ShaderSource,
    val fragment: ShaderSource,
    /**
     * The resources each group declares, or null when unknown. A replacement must bind exactly what
     * the pipeline it replaces binds, since the pipeline keeps its layout; an unknown one is refused.
     */
    val bindingsByGroup: Map<Int, GroupBindings>?,
)

/** A [ShaderReplacement] that was refused; the pipelines kept their shaders. */
class ShaderReplacementException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
