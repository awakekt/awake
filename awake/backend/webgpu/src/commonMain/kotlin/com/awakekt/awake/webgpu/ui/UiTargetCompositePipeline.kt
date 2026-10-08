/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

import com.awakekt.awake.render.passes2d.UiTargetCompositeUniformLayout
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.renderer.UiTargetCompositeMode
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.pipeline.createAwakePipelineLayout
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.texture.OffscreenRenderTarget
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.VertexState

/** A full-target sampled composite. Its shader owns the alpha-correct blend equations once. */
internal class UiTargetCompositePipeline(
    graphicsDevice: GraphicsDevice,
    private val swapchainManager: SwapchainManager,
    shaderCode: ByteArray,
    mode: UiTargetCompositeMode,
) {
    private val device = graphicsDevice.wgpuContext.device
    private val sampler = device.createSampler(SamplerDescriptor(minFilter = GPUFilterMode.Nearest, magFilter = GPUFilterMode.Nearest))
    private val modeBuffer = device.createBuffer(
        BufferDescriptor(
            size = (UiTargetCompositeUniformLayout.total * Float.SIZE_BYTES).toULong(),
            usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
        ),
    )
    private val bindGroups = HashMap<Pair<OffscreenRenderTarget, OffscreenRenderTarget>, GPUBindGroup>()
    var pipeline: GPURenderPipeline
        private set

    init {
        device.queue.writeBufferData(modeBuffer, 0uL, fastArrayBufferOf(intArrayOf(mode.ordinal)))
        pipeline = buildPipeline(shaderCode.decodeToString(), vertexEntryPoint = "vertexMain", fragmentEntryPoint = "fragmentMain")
    }

    internal fun buildPipeline(
        wgslSource: String,
        vertexEntryPoint: String = "vertexMain",
        fragmentEntryPoint: String = "fragmentMain",
    ): GPURenderPipeline {
        val module = device.createShaderModule(
            ShaderModuleDescriptor(
                code = wgslSource,
            ),
        )
        try {
            return device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = device.createAwakePipelineLayout(mapOf(0 to GroupBindings.UiTargetComposite)),
                    vertex = VertexState(module = module, entryPoint = vertexEntryPoint),
                    fragment = FragmentState(
                        module = module,
                        entryPoint = fragmentEntryPoint,
                        targets = listOf(ColorTargetState(format = swapchainManager.imageFormatWebGpu)),
                    ),
                    primitive = PrimitiveState(topology = GPUPrimitiveTopology.TriangleList),
                ),
            )
        } finally {
            module.close()
        }
    }

    internal fun swapIn(newPipeline: GPURenderPipeline): GPURenderPipeline {
        val old = pipeline
        pipeline = newPipeline
        bindGroups.clear()
        return old
    }

    fun bindGroupFor(source: OffscreenRenderTarget, destination: OffscreenRenderTarget): GPUBindGroup {
        bindGroups.entries.removeAll { (targets, _) -> targets.first.isDestroyed || targets.second.isDestroyed }
        return bindGroups.getOrPut(source to destination) {
            device.createBindGroup(
                BindGroupDescriptor(
                    layout = pipeline.getBindGroupLayout(0u),
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = source.colorView),
                        BindGroupEntry(binding = 1u, resource = sampler),
                        BindGroupEntry(binding = 2u, resource = destination.colorView),
                        BindGroupEntry(binding = 3u, resource = sampler),
                        BindGroupEntry(
                            binding = 4u,
                            resource = BufferBinding(
                                buffer = modeBuffer,
                                offset = 0uL,
                                size = (UiTargetCompositeUniformLayout.total * Float.SIZE_BYTES).toULong(),
                            ),
                        ),
                    ),
                ),
            )
        }
    }

    fun destroy() {
        bindGroups.clear()
        modeBuffer.close()
        sampler.close()
    }
}
