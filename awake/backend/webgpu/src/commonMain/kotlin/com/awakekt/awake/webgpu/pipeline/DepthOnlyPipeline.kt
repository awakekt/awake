/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.renderer.CascadePassUniformLayout
import com.awakekt.awake.render.renderer.SHADOW_CASCADE_PASS_GROUP
import com.awakekt.awake.render.renderer.SHADOW_DEPTH_BIAS_CONSTANT
import com.awakekt.awake.render.renderer.SHADOW_DEPTH_BIAS_SLOPE
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUFrontFace
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
 * Colorless graphics pipeline for rendering into a `DepthTarget`: no fragment output, no color
 * attachment, `Depth32Float` only. Takes the caller's shader and vertex layout, so what it
 * renders and from which viewpoint are the caller's business.
 */
class DepthOnlyPipeline(
    graphicsDevice: GraphicsDevice,
    shaderCode: ByteArray,
    val vertexFormat: VertexFormat,
    vertexEntryPoint: String = "vertexMain",
    fragmentEntryPoint: String = "fragmentMain",
    /** One slot per cascade in this pass's own group-1 block -- see Vulkan's twin for why the
     * cascade matrix cannot live in the per-draw uniform. */
    cascadeCount: Int = 0,
) {
    val pipeline: GPURenderPipeline
    val handle: WebGpuPipelineHandle

    private val device = graphicsDevice.wgpuContext.device

    /** One uniform buffer per cascade, written per frame. */
    private val cascadeBuffers: List<GPUBuffer> = List(cascadeCount) {
        device.createBuffer(
            BufferDescriptor(
                size = (CascadePassUniformLayout.total * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            ),
        )
    }

    /** Whether this pipeline's shader declares the pass-scoped cascade block. `scene_depth`
     * renders once from the camera and declares none -- and asking wgpu for the layout of a group
     * a shader never declared aborts the process rather than returning an error. */
    val hasCascadeBlock: Boolean = cascadeCount > 0

    private val cascadeBindGroups: List<WebGpuBindGroupHandle> by lazy {
        cascadeBuffers.map { buffer ->
            WebGpuBindGroupHandle(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = pipeline.getBindGroupLayout(SHADOW_CASCADE_PASS_GROUP.toUInt()),
                        entries = listOf(BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer))),
                    ),
                ),
            )
        }
    }

    /** [cascade]'s bind group, to bind at group 1 while rendering it. */
    fun cascadeBinding(cascade: Int): MaterialBinding = cascadeBindGroups[cascade]

    /** Writes [viewProjection] as the matrix [cascade] renders with; a no-op without a block. */
    fun writeCascade(cascade: Int, viewProjection: Mat4) {
        val buffer = cascadeBuffers.getOrNull(cascade) ?: return
        device.queue.writeBuffer(buffer, 0uL, fastArrayBufferOf(viewProjection.data))
    }

    init {
        val device = graphicsDevice.wgpuContext.device
        val wgslSource = shaderCode.decodeToString()
        val shaderModule = device.createShaderModule(ShaderModuleDescriptor(code = wgslSource))

        val vertexBuffers = listOf(
            VertexBufferLayout(
                arrayStride = vertexFormat.strideBytes.toULong(),
                attributes = vertexFormat.entries.map { (attribute, offsetBytes) ->
                    VertexAttribute(
                        shaderLocation = attribute.location.toUInt(),
                        offset = offsetBytes.toULong(),
                        format = attribute.format.toGpuVertexFormat(),
                    )
                },
            ),
        )

        pipeline = device.createRenderPipeline(
            RenderPipelineDescriptor(
                vertex = VertexState(
                    module = shaderModule,
                    entryPoint = vertexEntryPoint,
                    buffers = vertexBuffers,
                ),
                fragment = FragmentState(
                    module = shaderModule,
                    entryPoint = fragmentEntryPoint,
                    targets = emptyList(),
                ),
                primitive = PrimitiveState(
                    cullMode = GPUCullMode.None,
                    frontFace = GPUFrontFace.CW,
                ),
                // Bias only the cascade (light-space) pass -- same reasoning and constants as
                // Vulkan's twin: the scene-depth pass feeds depth_fog, which reads raw depth.
                depthStencil = DepthStencilState(
                    format = GPUTextureFormat.Depth32Float,
                    depthWriteEnabled = true,
                    depthCompare = GPUCompareFunction.LessEqual,
                    stencilFront = StencilFaceState(),
                    stencilBack = StencilFaceState(),
                    depthBias = if (hasCascadeBlock) SHADOW_DEPTH_BIAS_CONSTANT.toInt() else 0,
                    depthBiasSlopeScale = if (hasCascadeBlock) SHADOW_DEPTH_BIAS_SLOPE else 0f,
                ),
            ),
        )
        handle = WebGpuPipelineHandle(pipeline)
    }

    fun destroy() {
        cascadeBuffers.forEach { it.close() }
        // Everything else is garbage collected in the JS runtime
    }
}
