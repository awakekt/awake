/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.asset.shaders.BackgroundShaderCompile
import com.awakekt.awake.asset.shaders.resolveBytes
import com.awakekt.awake.render.pipeline.PipelineRegistry
import com.awakekt.awake.render.pipeline.PipelineSpec
import com.awakekt.awake.render.pipeline.PreparedShaderProgram
import com.awakekt.awake.render.pipeline.ShaderProgram
import com.awakekt.awake.render.pipeline.ShaderReplacement
import com.awakekt.awake.render.pipeline.ShaderReplacementException
import com.awakekt.awake.render.pipeline.ShaderSource
import com.awakekt.awake.render.pipeline.entryPoint
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * [ShaderReplacement] over the WebGPU pipelines in [registry].
 * Each pipeline is rebuilt inside its own [RenderPipeline] and swapped in place,
 * preserving its handle reference while updating the underlying `GPURenderPipeline`.
 *
 * Dependent bind groups and uniform slot buffers are invalidated via [onSwap].
 */
internal class WebGpuShaderReplacement(
    private val registry: PipelineRegistry<RenderPipeline>,
    private val device: GPUDevice,
    private val onSwap: ((oldPipeline: GPURenderPipeline) -> Unit)? = null,
    compileDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val resolveSource: suspend (ShaderSource) -> String = ::resolveWgslSource,
) : ShaderReplacement {
    /** What a pipeline runs since its last replacement; absent means its spec's own shaders. */
    private val running = HashMap<RenderPipeline, ShaderProgram>()

    private val background = BackgroundShaderCompile(compileDispatcher, ::compileProgram)

    override suspend fun prepare(new: ShaderProgram): PreparedShaderProgram =
        prepareCandidates(new, background.compile(new))

    override suspend fun replace(old: ShaderProgram, new: ShaderProgram): Int =
        prepareCandidates(new, background.compileInPlace(new)).use { swapIn(old, it) }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun prepareCandidates(new: ShaderProgram, wgsl: String): PreparedWebGpuShaderProgram {
        device.createValidated { device.createShaderModule(ShaderModuleDescriptor(code = wgsl)) }.close()
        val builders = buildMap<Any, () -> GPURenderPipeline> {
            registry.specs.filter { it.bindingsMetadataAvailable && it.bindingsByGroup == new.bindingsByGroup }
                .forEach { spec ->
                    registry[spec]?.let { pipeline ->
                        put(pipeline) { pipeline.buildPipeline(wgsl, new.vertex.entryPoint, new.fragment.entryPoint) }
                    }
                }
        }
        val candidates = LinkedHashMap<Any, Result<GPURenderPipeline>>()
        try {
            builders.forEach { (identity, build) ->
                // prepare does not know which old program will be selected. Retain failures for
                // that target, without refusing shaders usable by another pipeline with the same ABI.
                candidates[identity] = try {
                    Result.success(device.createValidated(build))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    Result.failure(ShaderReplacementException("A replacement pipeline failed to build: ${failure.message}", failure))
                }
            }
            return PreparedWebGpuShaderProgram(this, new, candidates)
        } catch (failure: Throwable) {
            candidates.values.forEach { it.getOrNull()?.close() }
            throw failure
        }
    }

    @Suppress("TooGenericExceptionCaught", "UnusedParameter")
    private suspend fun compileProgram(vertex: ShaderSource, fragment: ShaderSource): String = try {
        resolveSource(vertex)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: ShaderReplacementException) {
        throw e
    } catch (e: Throwable) {
        throw ShaderReplacementException("The replacement shaders do not compile: ${e.message}", e)
    }

    @Suppress("TooGenericExceptionCaught")
    override fun swapIn(old: ShaderProgram, prepared: PreparedShaderProgram): Int {
        val ready = (prepared as? PreparedWebGpuShaderProgram)?.takeIf { it.owner === this }
            ?: throw ShaderReplacementException("That program was not prepared by this ShaderReplacement.")
        val new = ready.program
        val targets = registry.specs.mapNotNull { spec ->
            registry[spec]?.takeIf { runs(it, spec, old) }?.let { spec to it }
        }
        targets.forEach { (spec, _) -> requireSameBindings(spec, new) }

        val identities = targets.mapTo(HashSet<Any>()) { it.second }
        val built = ready.take(identities)

        targets.forEach { (_, pipeline) ->
            val oldGpuPipeline = pipeline.swapIn(built.getValue(pipeline))
            onSwap?.invoke(oldGpuPipeline)
            oldGpuPipeline.close()
            running[pipeline] = new
        }
        val live = registry.specs.mapNotNullTo(HashSet()) { registry[it] }
        running.keys.retainAll(live)
        return targets.size
    }

    private fun runs(pipeline: RenderPipeline, spec: PipelineSpec, program: ShaderProgram): Boolean {
        val current = running[pipeline]
        return if (current != null) {
            current.vertex == program.vertex && current.fragment == program.fragment
        } else {
            spec.vertexShader == program.vertex && spec.fragmentShader == program.fragment
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

    private companion object {
        suspend fun resolveWgslSource(source: ShaderSource): String = when (source) {
            is ShaderSource.InlineText -> source.sourceCode
            is ShaderSource.ResourcePath -> {
                check(!source.path.endsWith(".spv")) {
                    "WebGPU cannot consume SPIR-V resource '${source.path}'."
                }
                source.resolveBytes().decodeToString()
            }
            is ShaderSource.PrecompiledBinary ->
                error("WebGPU cannot consume precompiled SPIR-V bytes; provide WGSL instead.")
        }
    }
}
