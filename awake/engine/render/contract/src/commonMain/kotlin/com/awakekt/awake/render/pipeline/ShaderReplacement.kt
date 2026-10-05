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
     * Compiles [new], touching no pipeline. It compiles off the calling thread and suspends until it
     * is done, so a render thread can ask for it between frames and keep drawing meanwhile; the
     * caller resumes on its own dispatcher, so hand the result to the render thread and [swapIn] it
     * there, as the next frame begins. Compiles queue, one at a time.
     *
     * @throws ShaderReplacementException when [new] does not compile. Nothing has changed.
     */
    suspend fun prepare(new: ShaderProgram): PreparedShaderProgram

    /**
     * Rebuilds every pipeline now running [old]'s shaders with [prepared]'s, and returns how many.
     *
     * Call on the render thread between frames, as content features are attached. Every replacement
     * pipeline is built before anything changes; then the GPU is drained, the replacements swapped
     * in and the old pipelines destroyed. Everything holding a pipeline draws with [prepared]'s
     * program from the next frame, and that program is what a later call replaces. This is the only
     * step that must be on the render thread, and it does not compile.
     *
     * @throws ShaderReplacementException when [prepared] binds differently from a pipeline running
     * [old], or came from another [ShaderReplacement]. Nothing has changed.
     */
    fun swapIn(old: ShaderProgram, prepared: PreparedShaderProgram): Int

    /**
     * [prepare] then [swapIn], all on the calling thread: it compiles where it is called, which holds up
     * a frame for a large shader. Convenient for a tool that is not drawing; a live preview wants
     * [prepare] elsewhere and [swapIn] between frames.
     *
     * @throws ShaderReplacementException when [new] does not compile, or binds differently from a
     * pipeline running [old]. Nothing has changed.
     */
    suspend fun replace(old: ShaderProgram, new: ShaderProgram): Int = swapIn(old, prepare(new))

    /** Capability identifier key for querying [ShaderReplacement] support from a [GpuDevice]. */
    companion object : GpuCapabilityKind<ShaderReplacement>
}

/** A [ShaderProgram] that has been compiled and is ready for [ShaderReplacement.apply]; opaque to callers. */
interface PreparedShaderProgram {
    /** The program that was compiled. */
    val program: ShaderProgram
}

/**
 * A pipeline's two shader stages, and what each of its groups binds.
 *
 * @property vertex Vertex shader stage source reference.
 * @property fragment Fragment shader stage source reference.
 * @property bindingsByGroup The resources each group declares, or null when unknown. A replacement must bind exactly what
 * the pipeline it replaces binds, since the pipeline keeps its layout; an unknown one is refused.
 */
data class ShaderProgram(
    /** Vertex shader stage source reference. */
    val vertex: ShaderSource,
    /** Fragment shader stage source reference. */
    val fragment: ShaderSource,
    /**
     * The resources each group declares, or null when unknown. A replacement must bind exactly what
     * the pipeline it replaces binds, since the pipeline keeps its layout; an unknown one is refused.
     */
    val bindingsByGroup: Map<Int, GroupBindings>?,
)

/** A [ShaderReplacement] that was refused; the pipelines kept their shaders. */
class ShaderReplacementException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)
