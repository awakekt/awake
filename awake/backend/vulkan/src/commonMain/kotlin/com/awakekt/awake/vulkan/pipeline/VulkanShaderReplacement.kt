/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.asset.shadercompiler.NagaException
import com.awakekt.awake.asset.shaders.BackgroundShaderCompile
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.pipeline.PreparedShaderProgram
import com.awakekt.awake.render.pipeline.ShaderProgram
import com.awakekt.awake.render.pipeline.ShaderReplacement
import com.awakekt.awake.render.pipeline.ShaderReplacementException
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.pipeline.entryPoint
import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.utils.VkResultException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * An instantiated UI graphics pipeline target that can compile and swap in new SPIR-V shader pairs.
 */
internal interface VulkanUiPipelineTarget {
    val identity: Any
    val program: ShaderProgram
    val bindingsByGroup: Map<Int, GroupBindings>
    fun buildPipeline(shaders: ShaderPair, vertexEntryPoint: String, fragmentEntryPoint: String): Long
    fun swapIn(newPipeline: Long)
    fun destroyPipeline(handle: Long)
}

/**
 * [ShaderReplacement] over the pipelines in [registry] and any active UI pipeline targets. Each one is rebuilt inside its own
 * [RenderPipeline] or [VulkanUiPipelineTarget], so the registry, the renderer's pipeline table, content features, and UI
 * drawing all draw with the new shaders without being told.
 *
 * The registry keeps each pipeline under the spec it was built from, so a content feature still
 * detaches by that spec; what a pipeline runs after a replacement is tracked here instead.
 * Depth-only and debug-line pipelines are not in the registry and are never replaced.
 *
 * [prepare] compiles on [compileDispatcher], off whichever thread asks, one program at a time.
 * [compile] is therefore called from that dispatcher and from [replace]'s caller, never at once, so
 * it may keep a cache that is not thread-safe, but that cache must not be shared with code that
 * compiles on the render thread: give it a resolver of its own.
 */
internal class VulkanShaderReplacement(
    private val graphicsDevice: GraphicsDevice,
    private val registry: PipelineRegistry<RenderPipeline>,
    private val uiTargets: () -> List<VulkanUiPipelineTarget> = { emptyList() },
    compileDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val compile: suspend (vertex: ShaderSource, fragment: ShaderSource) -> ShaderPair,
) : ShaderReplacement {
    /** What a pipeline runs since its last replacement; absent means its spec's own shaders. */
    private val running = HashMap<RenderPipeline, ShaderProgram>()
    private val uiRunning = HashMap<Any, ShaderProgram>()

    private val background = BackgroundShaderCompile(compileDispatcher, ::compileProgram)

    /** A program compiled by this replacement, which is what [swapIn] accepts. */
    private class Prepared(override val program: ShaderProgram, val shaders: ShaderPair) : PreparedShaderProgram

    override suspend fun prepare(new: ShaderProgram): PreparedShaderProgram =
        Prepared(new, background.compile(new))

    /** Compiles here and now, on the caller's thread, as before [prepare] existed. */
    override suspend fun replace(old: ShaderProgram, new: ShaderProgram): Int =
        swapIn(old, Prepared(new, background.compileInPlace(new)))

    private suspend fun compileProgram(vertex: ShaderSource, fragment: ShaderSource): ShaderPair = try {
        compile(vertex, fragment)
    } catch (e: NagaException) {
        throw ShaderReplacementException("The replacement shaders do not compile: ${e.message}", e)
    }

    override fun swapIn(old: ShaderProgram, prepared: PreparedShaderProgram): Int {
        val ready = prepared as? Prepared
            ?: throw ShaderReplacementException("That program was not prepared by this ShaderReplacement.")
        val new = ready.program
        val shaders = ready.shaders
        val targets = registry.specs.mapNotNull { spec -> registry[spec]?.takeIf { runs(it, spec, old) }?.let { spec to it } }
        targets.forEach { (spec, _) -> requireSameBindings(spec, new) }

        val activeUi = uiTargets().filter { runsUi(it, old) }
        activeUi.forEach { requireSameBindings(it, new) }

        val built = ArrayList<Long>(targets.size)
        val builtUi = ArrayList<Long>(activeUi.size)
        try {
            targets.forEach { (_, pipeline) ->
                built += pipeline.buildPipeline(shaders, new.vertex.entryPoint, new.fragment.entryPoint)
            }
            activeUi.forEach { target ->
                builtUi += target.buildPipeline(shaders, new.vertex.entryPoint, new.fragment.entryPoint)
            }
        } catch (e: VkResultException) {
            built.forEachIndexed { i, handle -> targets[i].second.destroyPipeline(handle) }
            builtUi.forEachIndexed { i, handle -> activeUi[i].destroyPipeline(handle) }
            throw ShaderReplacementException("A replacement pipeline failed to build: ${e.message}", e)
        }

        if (targets.isNotEmpty() || activeUi.isNotEmpty()) VulkanBuffers.vkDeviceWaitIdle(graphicsDevice.device)
        targets.forEachIndexed { i, (_, pipeline) ->
            pipeline.swapIn(built[i])
            running[pipeline] = new
        }
        activeUi.forEachIndexed { i, target ->
            target.swapIn(builtUi[i])
            uiRunning[target.identity] = new
        }
        val live = registry.specs.mapNotNullTo(HashSet()) { registry[it] }
        running.keys.retainAll(live)
        val liveUi = uiTargets().mapTo(HashSet()) { it.identity }
        uiRunning.keys.retainAll(liveUi)
        return targets.size + activeUi.size
    }

    private fun runs(pipeline: RenderPipeline, spec: PipelineSpec, program: ShaderProgram): Boolean {
        val current = running[pipeline]
        return if (current != null) {
            current.vertex == program.vertex && current.fragment == program.fragment
        } else {
            spec.vertexShader == program.vertex && spec.fragmentShader == program.fragment
        }
    }

    private fun runsUi(target: VulkanUiPipelineTarget, program: ShaderProgram): Boolean {
        val current = uiRunning[target.identity]
        return if (current != null) {
            current.vertex == program.vertex && current.fragment == program.fragment
        } else {
            target.program.vertex == program.vertex && target.program.fragment == program.fragment
        }
    }

    private fun requireSameBindings(spec: PipelineSpec, new: ShaderProgram) {
        if (!spec.bindingsMetadataAvailable || new.bindingsByGroup == null) {
            throw ShaderReplacementException(
                "A pipeline's bindings are not known, so the replacement cannot be checked against its layout.",
            )
        }
        if (new.bindingsByGroup != spec.bindingsByGroup) {
            throw ShaderReplacementException(
                "The replacement binds ${new.bindingsByGroup} but the pipeline binds ${spec.bindingsByGroup}. " +
                    "A pipeline keeps its layout when its shaders are replaced; changing bindings needs a new pipeline.",
            )
        }
    }

    private fun requireSameBindings(target: VulkanUiPipelineTarget, new: ShaderProgram) {
        if (new.bindingsByGroup == null) {
            throw ShaderReplacementException(
                "A pipeline's bindings are not known, so the replacement cannot be checked against its layout.",
            )
        }
        if (new.bindingsByGroup != target.bindingsByGroup) {
            throw ShaderReplacementException(
                "The replacement binds ${new.bindingsByGroup} but the pipeline binds ${target.bindingsByGroup}. " +
                    "A pipeline keeps its layout when its shaders are replaced; changing bindings needs a new pipeline.",
            )
        }
    }
}
