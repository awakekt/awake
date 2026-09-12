/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.debug

import com.awakekt.awake.render.passes.debug.DebugLineLayout
import com.awakekt.awake.render.passes.debug.DebugLineUniformLayout
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.pipeline.WebGpuBindGroupHandle
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineHandle
import com.awakekt.awake.webgpu.pipeline.createAwakePipelineLayout
import com.awakekt.awake.webgpu.pipeline.toGpuVertexFormat
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.StencilFaceState
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState

/**
 * WebGPU mirror of Vulkan's `debug.LineRenderPipeline` -- a `GPUPrimitiveTopology.LineList`
 * pipeline for world-space debug lines (e.g. a `Frustum` wireframe), drawn in the same
 * render pass as the existing 3D `drawCalls` loop in `Renderer.draw`.
 *
 * The 3D pass (`RendererDraw3D.performDraw`) now has a real `depthStencilAttachment`
 * (`Depth32Float`, added alongside the textured-mesh pipeline) -- WebGPU requires every
 * pipeline used inside a pass to declare a matching `depthStencil` state, or the whole
 * command buffer is rejected at submit time (confirmed live: "Attachment state of
 * [RenderPipeline] is not compatible with [RenderPassEncoder]", canvas going black on any
 * page that draws the reference grid). `depthCompare = Always` + `depthWriteEnabled = false`
 * satisfies that requirement while keeping lines' original no-depth-test look (never
 * occluded by, never occludes, scene geometry).
 */
class LineRenderPipeline(graphicsDevice: GraphicsDevice, swapchainManager: SwapchainManager, shaderCode: ByteArray) {
    private val device = graphicsDevice.wgpuContext.device
    val pipeline: GPURenderPipeline
    private val mvpBuffer: GPUBuffer
    val bindGroup: GPUBindGroup

    /** [pipeline]/[bindGroup] as the shared render layer's opaque handles -- built once. */
    val handle: WebGpuPipelineHandle by lazy {
        WebGpuPipelineHandle(pipeline, bindingsByGroup = mapOf(0 to GroupBindings.UniformOnlyMaterial))
    }
    val bindGroupHandle: WebGpuBindGroupHandle by lazy { WebGpuBindGroupHandle(bindGroup) }

    init {
        val wgslSource = shaderCode.decodeToString()
        val shaderModule = device.createShaderModule(ShaderModuleDescriptor(code = wgslSource))

        pipeline = device.createRenderPipeline(
            RenderPipelineDescriptor(
                layout = device.createAwakePipelineLayout(mapOf(0 to GroupBindings.UniformOnlyMaterial)),
                vertex = VertexState(
                    module = shaderModule,
                    entryPoint = "vertexMain",
                    buffers = listOf(
                        // Derived from DebugLineLayout, not restated -- the same layout was
                        // hardcoded here and in Vulkan's line pipeline, so it existed in three
                        // places. See `awake-render-pipeline`'s "never hardcode vertex attribute
                        // descriptions" rule, which names lines specifically.
                        VertexBufferLayout(
                            arrayStride = DebugLineLayout.Format.strideBytes.toULong(),
                            attributes = DebugLineLayout.Format.entries.map { entry ->
                                VertexAttribute(
                                    shaderLocation = entry.attribute.location.toUInt(),
                                    offset = entry.offsetBytes.toULong(),
                                    format = entry.attribute.format.toGpuVertexFormat(),
                                )
                            },
                        ),
                    ),
                ),
                fragment = FragmentState(
                    module = shaderModule,
                    entryPoint = "fragmentMain",
                    targets = listOf(ColorTargetState(format = swapchainManager.imageFormatWebGpu)),
                ),
                primitive = PrimitiveState(topology = GPUPrimitiveTopology.LineList),
                depthStencil = DepthStencilState(
                    format = GPUTextureFormat.Depth32Float,
                    depthWriteEnabled = false,
                    depthCompare = GPUCompareFunction.Always,
                    stencilFront = StencilFaceState(),
                    stencilBack = StencilFaceState(),
                ),
            ),
        )

        mvpBuffer = device.createBuffer(
            BufferDescriptor(
                size = (DebugLineUniformLayout.total * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            ),
        )
        bindGroup = device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = mvpBuffer)),
                ),
            ),
        )
    }

    /** Lines are already in world space (no per-line model matrix), so their MVP is exactly
     * the frame's viewProjection. */
    fun writeMvp(mvp: FloatArray) {
        device.queue.writeBuffer(mvpBuffer, 0uL, fastArrayBufferOf(mvp))
    }

    fun destroy() {
        mvpBuffer.close()
    }
}
