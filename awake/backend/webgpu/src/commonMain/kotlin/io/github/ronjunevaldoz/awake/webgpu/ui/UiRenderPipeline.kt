// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.ui

import io.github.ronjunevaldoz.awake.render.passes2d.UiPipelineKind
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormats2D
import io.github.ronjunevaldoz.awake.ui.font.UiFont
import io.github.ronjunevaldoz.awake.webgpu.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.webgpu.fastArrayBufferOf
import io.github.ronjunevaldoz.awake.webgpu.material.Material
import io.github.ronjunevaldoz.awake.webgpu.pipeline.toGpuVertexFormat
import io.github.ronjunevaldoz.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUMipmapFilterMode
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.TextureViewDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState


/**
 * Unified UI overlay pipeline for WebGPU -- handles colored quads, rounded quads, font glyphs, and textures.
 */
class UiRenderPipeline(
    graphicsDevice: GraphicsDevice,
    swapchainManager: SwapchainManager,
    shaderCode: ByteArray,
    val kind: UiPipelineKind = UiPipelineKind.Quad,
    font: UiFont? = null,
) {
    private val device = graphicsDevice.wgpuContext.device
    val pipeline: GPURenderPipeline
    private val screenSizeBuffer: GPUBuffer
    private var fontTexture: GPUTexture? = null
    var bindGroup: GPUBindGroup
        private set
    val screenSizeBindGroup: GPUBindGroup get() = bindGroup
    private val materialBindGroups = HashMap<Material, GPUBindGroup>()

    init {
        val wgslSource = shaderCode.decodeToString()
        val shaderModule = device.createShaderModule(ShaderModuleDescriptor(code = wgslSource))

        val vertexAttributes = kind.vertexFormat.entries.map { entry ->
            VertexAttribute(
                shaderLocation = entry.attribute.location.toUInt(),
                offset = entry.offsetBytes.toULong(),
                format = entry.attribute.format.toGpuVertexFormat(),
            )
        }

        pipeline = device.createRenderPipeline(
            RenderPipelineDescriptor(
                vertex = VertexState(
                    module = shaderModule,
                    entryPoint = "vertexMain",
                    buffers = listOf(
                        VertexBufferLayout(
                            arrayStride = kind.vertexFormat.strideBytes.toULong(),
                            attributes = vertexAttributes,
                        ),
                    ),
                ),
                fragment = FragmentState(
                    module = shaderModule,
                    entryPoint = "fragmentMain",
                    targets = listOf(
                        ColorTargetState(
                            format = swapchainManager.imageFormatWebGpu,
                            blend = BlendState(
                                color = BlendComponent(
                                    srcFactor = GPUBlendFactor.SrcAlpha,
                                    dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                                ),
                                alpha = BlendComponent(
                                    srcFactor = GPUBlendFactor.SrcAlpha,
                                    dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                                ),
                            ),
                        ),
                    ),
                ),
                primitive = PrimitiveState(topology = GPUPrimitiveTopology.TriangleList),
            ),
        )

        screenSizeBuffer = device.createBuffer(
            BufferDescriptor(
                size = (4 * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            ),
        )

        if (kind == UiPipelineKind.Glyph && font != null) {
            val tex = device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = font.atlasWidth.toUInt(), height = font.atlasHeight.toUInt()),
                    format = GPUTextureFormat.RGBA8Unorm,
                    usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                    dimension = GPUTextureDimension.TwoD,
                ),
            )
            fontTexture = tex
            device.queue.writeTexture(
                destination = TexelCopyTextureInfo(texture = tex),
                data = fastArrayBufferOf(font.atlasPixelsRgba),
                dataLayout = TexelCopyBufferLayout(bytesPerRow = (font.atlasWidth * 4).toUInt()),
                size = Extent3D(width = font.atlasWidth.toUInt(), height = font.atlasHeight.toUInt()),
            )
            val fontTextureView = tex.createView(TextureViewDescriptor())
            val fontSampler = device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    addressModeW = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Linear,
                    minFilter = GPUFilterMode.Linear,
                    mipmapFilter = GPUMipmapFilterMode.Linear,
                ),
            )
            bindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    layout = pipeline.getBindGroupLayout(0u),
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = screenSizeBuffer)),
                        BindGroupEntry(binding = 1u, resource = fontTextureView),
                        BindGroupEntry(binding = 2u, resource = fontSampler),
                    ),
                ),
            )
        } else {
            bindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    layout = pipeline.getBindGroupLayout(0u),
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = screenSizeBuffer)),
                    ),
                ),
            )
        }

        val renderingContext = graphicsDevice.wgpuContext.renderingContext
        writeScreenSize(renderingContext.width.toFloat(), renderingContext.height.toFloat())
    }

    /** Call once at construction and again whenever the canvas resizes. */
    fun writeScreenSize(width: Float, height: Float) {
        device.queue.writeBuffer(
            screenSizeBuffer,
            0uL,
            fastArrayBufferOf(
                floatArrayOf(
                    width,
                    height,
                    0f,
                    0f,
                ),
            ),
        )
    }

    /** Builds (once) and returns the bind group for [material]'s `previewTextureView`/`previewSampler`. */
    fun bindGroupFor(material: Material): GPUBindGroup = materialBindGroups.getOrPut(material) {
        device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = screenSizeBuffer)),
                    BindGroupEntry(
                        binding = 1u,
                        resource = requireNotNull(material.previewTextureView) {
                            "Material has no previewTextureView -- was it built via createMaterial(renderTarget = ...)?"
                        },
                    ),
                    BindGroupEntry(
                        binding = 2u,
                        resource = requireNotNull(material.previewSampler) {
                            "Material has no previewSampler -- was it built via createMaterial(renderTarget = ...)?"
                        },
                    ),
                ),
            ),
        )
    }

    fun destroy() {
        fontTexture?.close()
        screenSizeBuffer.close()
    }
}
