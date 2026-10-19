/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

import com.awakekt.awake.core.geometry.VertexFormats2D
import com.awakekt.awake.core.graphics2d.BackdropBlurQuality
import com.awakekt.awake.core.graphics2d.BackdropRegion
import com.awakekt.awake.core.graphics2d.DualKawaseBlur
import com.awakekt.awake.render.passes2d.BackdropBlurPass
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.pipeline.bindGroup
import com.awakekt.awake.webgpu.pipeline.createAwakePipelineLayout
import com.awakekt.awake.webgpu.pipeline.toGpuVertexFormat
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMipmapFilterMode
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.Origin3D
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState

/**
 * Everything a backdrop blur needs on WebGPU beyond the UI pass: a full-resolution copy of the
 * backdrop (level 0, since a pass cannot sample the attachment it draws into and a surface texture
 * cannot be bound for sampling), the reduced-resolution levels, the dual-filter pipeline and a
 * linear clamp-to-edge sampler.
 *
 * Levels only grow, as on Vulkan: the shaders address them in texels and read their size from the
 * texture, so a larger level serves a smaller backdrop.
 */
internal class BackdropBlurResources(
    graphicsDevice: GraphicsDevice,
    private val format: GPUTextureFormat,
    shaderCode: ByteArray,
) {
    private val device = graphicsDevice.wgpuContext.device

    private class Level(val texture: GPUTexture, val view: GPUTextureView)

    /** Linear and clamp-to-edge, so a tap between texels blends them and none wraps around. */
    val sampler = device.createSampler(
        SamplerDescriptor(
            addressModeU = GPUAddressMode.ClampToEdge,
            addressModeV = GPUAddressMode.ClampToEdge,
            addressModeW = GPUAddressMode.ClampToEdge,
            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear,
            mipmapFilter = GPUMipmapFilterMode.Nearest,
        ),
    )

    private val pipeline: GPURenderPipeline = buildPipeline(shaderCode.decodeToString())
    private var levels: Array<Level?> = emptyArray()
    private var sourceGroups: Array<GPUBindGroup?> = emptyArray()
    private var levelsWidth = 0
    private var levelsHeight = 0

    /**
     * Makes sure level 0 and every level a blur can reach hold a [width] by [height] backdrop.
     *
     * @return true when the levels were rebuilt, so bind groups over the old ones are stale.
     */
    fun ensureLevels(width: Int, height: Int): Boolean {
        if (fits(width, height)) return false
        destroyLevels()
        val grownWidth = maxOf(width, levelsWidth)
        val grownHeight = maxOf(height, levelsHeight)
        levels = Array(MAX_LEVEL + 1) { level ->
            val usage = if (level == 0) {
                GPUTextureUsage.CopyDst or GPUTextureUsage.TextureBinding
            } else {
                GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding
            }
            val texture = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(
                        width = DualKawaseBlur.levelSize(grownWidth, level).toUInt(),
                        height = DualKawaseBlur.levelSize(grownHeight, level).toUInt(),
                    ),
                    format = format,
                    usage = usage,
                    dimension = GPUTextureDimension.TwoD,
                ),
            )
            Level(texture, texture.createView(TextureViewDescriptor()))
        }
        sourceGroups = arrayOfNulls(MAX_LEVEL + 1)
        levelsWidth = grownWidth
        levelsHeight = grownHeight
        return true
    }

    /** Whether the levels can hold a blur of a [width] by [height] backdrop. */
    fun fits(width: Int, height: Int): Boolean = levels.isNotEmpty() && width <= levelsWidth && height <= levelsHeight

    /** The view of [level] for a composite to sample. */
    fun levelView(level: Int): GPUTextureView = requireNotNull(levels[level]).view

    /** Copies [region] of [source], the target the UI pass drew into, into level 0, outside any pass. */
    fun capture(encoder: GPUCommandEncoder, source: GPUTexture, region: BackdropRegion) {
        val origin = Origin3D(x = region.left.toUInt(), y = region.top.toUInt())
        encoder.copyTextureToTexture(
            TexelCopyTextureInfo(texture = source, origin = origin),
            TexelCopyTextureInfo(texture = requireNotNull(levels[0]).texture, origin = origin),
            Extent3D(width = region.width.toUInt(), height = region.height.toUInt()),
        )
    }

    /**
     * Records [pass] of a blur in a render pass of its own: [mesh]'s triangle into its target
     * level, scissored to the texels it writes, sampling its source level.
     */
    fun recordPass(encoder: GPUCommandEncoder, pass: BackdropBlurPass, mesh: DynamicMesh, stats: RenderStatsCounter) {
        val target = requireNotNull(levels[pass.targetLevel])
        val rect = pass.target
        val renderPass = encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(view = target.view, loadOp = GPULoadOp.Load, storeOp = GPUStoreOp.Store),
                ),
            ),
        )
        renderPass.setScissorRect(rect.left.toUInt(), rect.top.toUInt(), rect.width.toUInt(), rect.height.toUInt())
        renderPass.setPipeline(pipeline)
        renderPass.bindGroup(0u, sourceGroup(pass.sourceLevel))
        renderPass.setVertexBuffer(0u, mesh.vertexBufferRef())
        renderPass.setIndexBuffer(mesh.indexBufferRef(), DynamicMesh.indexFormat)
        renderPass.drawIndexed(mesh.drawIndexCount.toUInt())
        stats.recordDraw(mesh.drawIndexCount)
        renderPass.end()
    }

    private fun sourceGroup(level: Int): GPUBindGroup = sourceGroups[level] ?: device.createBindGroup(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = listOf(
                BindGroupEntry(binding = 0u, resource = levelView(level)),
                BindGroupEntry(binding = 1u, resource = sampler),
            ),
        ),
    ).also { sourceGroups[level] = it }

    private fun buildPipeline(wgsl: String): GPURenderPipeline {
        val module = device.createShaderModule(ShaderModuleDescriptor(code = wgsl))
        val vertexFormat = VertexFormats2D.BackdropBlurPass
        try {
            return device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = device.createAwakePipelineLayout(mapOf(0 to GroupBindings.UiBackdropBlur)),
                    vertex = VertexState(
                        module = module,
                        entryPoint = "vertexMain",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = vertexFormat.strideBytes.toULong(),
                                attributes = vertexFormat.entries.map { entry ->
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
                        module = module,
                        entryPoint = "fragmentMain",
                        targets = listOf(ColorTargetState(format = format)),
                    ),
                    primitive = PrimitiveState(topology = GPUPrimitiveTopology.TriangleList),
                ),
            )
        } finally {
            module.close()
        }
    }

    private fun destroyLevels() {
        levels.forEach { it?.texture?.close() }
        levels = emptyArray()
        sourceGroups = emptyArray()
    }

    fun destroy() {
        destroyLevels()
        sampler.close()
    }

    private companion object {
        /** The deepest level any quality reaches. */
        val MAX_LEVEL: Int = BackdropBlurQuality.entries.maxOf { it.maxIterations }
    }
}
