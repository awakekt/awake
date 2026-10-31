/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.texture

import com.awakekt.awake.asset.shaderdsl.bindingsByGroup
import com.awakekt.awake.asset.shaderpack.MIP_DOWNSAMPLE_TRIANGLE
import com.awakekt.awake.asset.shaderpack.MipDownsampleShader
import com.awakekt.awake.asset.shaderpack.MipDownsampleWgsl
import com.awakekt.awake.webgpu.device.submitAndRelease
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.pipeline.createAwakePipelineLayout
import com.awakekt.awake.webgpu.pipeline.createBindGroupReleasingLayout
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState

/** How many levels a full mip chain of a [width]x[height] image has, down to 1x1, as `mipChain` builds. */
internal fun mipLevelCount(width: Int, height: Int): Int {
    var levels = 1
    var w = width
    var h = height
    while (w > 1 || h > 1) {
        w = (w / 2).coerceAtLeast(1)
        h = (h / 2).coerceAtLeast(1)
        levels++
    }
    return levels
}

/**
 * Fills mip levels 1 and up of an `RGBA8Unorm` 2D texture from its level 0, on the GPU: one render
 * pass per level, each sampling the level above with a linear filter. It is the GPU counterpart of
 * the CPU `mipChain`, for a texture whose pixels never reach the CPU. The texture needs
 * `RenderAttachment` and `TextureBinding` usage.
 *
 * Within one unit per channel of the CPU chain on even sizes: a bilinear sample at the texel centre is
 * the same 2x2 box, rounded where the CPU truncates. The pipeline is built on first use and kept.
 */
internal class GpuMipmaps(private val device: GPUDevice) : AutoCloseable {
    private var pipeline: GPURenderPipeline? = null
    private var sampler: GPUSampler? = null
    private var triangle: GPUBuffer? = null

    /** Records and submits the passes that fill levels `1 until levelCount` of [texture]. */
    fun generate(texture: GPUTexture, levelCount: Int) {
        if (levelCount <= 1) return
        val pipeline = pipeline ?: createPipeline().also { pipeline = it }
        val sampler = sampler ?: device.createSampler(
            SamplerDescriptor(magFilter = GPUFilterMode.Linear, minFilter = GPUFilterMode.Linear),
        ).also { sampler = it }
        val triangle = triangle ?: createTriangle().also { triangle = it }
        val views = List(levelCount) { level ->
            texture.createView(TextureViewDescriptor(baseMipLevel = level.toUInt(), mipLevelCount = 1u))
        }
        val encoder = device.createCommandEncoder()
        for (level in 1 until levelCount) {
            val bindGroup = device.createBindGroupReleasingLayout(
                BindGroupDescriptor(
                    layout = pipeline.getBindGroupLayout(0u),
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = views[level - 1]),
                        BindGroupEntry(binding = 1u, resource = sampler),
                    ),
                ),
            )
            val pass = encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(view = views[level], loadOp = GPULoadOp.Clear, storeOp = GPUStoreOp.Store),
                    ),
                ),
            )
            pass.setPipeline(pipeline)
            pass.setBindGroup(0u, bindGroup)
            pass.setVertexBuffer(0u, triangle)
            pass.draw(TRIANGLE_VERTICES.toUInt())
            pass.end()
            bindGroup.close()
        }
        device.queue.submitAndRelease(encoder)
        views.forEach { it.close() }
    }

    override fun close() {
        pipeline?.close()
        sampler?.close()
        triangle?.close()
        pipeline = null
        sampler = null
        triangle = null
    }

    private fun createPipeline(): GPURenderPipeline {
        val module = device.createShaderModule(ShaderModuleDescriptor(code = MipDownsampleWgsl))
        val layout = device.createAwakePipelineLayout(MipDownsampleShader.bindingsByGroup())
        return try {
            device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = layout,
                    vertex = VertexState(
                        module = module,
                        entryPoint = "vertexMain",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = (FLOATS_PER_VERTEX * Float.SIZE_BYTES).toULong(),
                                attributes = listOf(
                                    VertexAttribute(format = GPUVertexFormat.Float32x2, offset = 0uL, shaderLocation = 0u),
                                    VertexAttribute(format = GPUVertexFormat.Float32x2, offset = UV_OFFSET, shaderLocation = 1u),
                                ),
                            ),
                        ),
                    ),
                    fragment = FragmentState(
                        module = module,
                        entryPoint = "fragmentMain",
                        targets = listOf(ColorTargetState(format = GPUTextureFormat.RGBA8Unorm)),
                    ),
                    primitive = PrimitiveState(topology = GPUPrimitiveTopology.TriangleList),
                ),
            )
        } finally {
            layout.close()
            module.close()
        }
    }

    private fun createTriangle(): GPUBuffer = device.createBuffer(
        BufferDescriptor(
            size = (MIP_DOWNSAMPLE_TRIANGLE.size * Float.SIZE_BYTES).toULong(),
            usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
        ),
    ).also { device.queue.writeBufferData(it, 0uL, fastArrayBufferOf(MIP_DOWNSAMPLE_TRIANGLE)) }

    private companion object {
        const val FLOATS_PER_VERTEX = 4
        const val TRIANGLE_VERTICES = 3
        const val UV_OFFSET = 8uL
    }
}
