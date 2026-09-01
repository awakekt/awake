/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.ui

import io.github.awakelab.awake.core.text.font.UiFont
import io.github.awakelab.awake.core.graphics2d.BlendMode
import io.github.awakelab.awake.render.passes2d.UiPipelineKind
import io.github.awakelab.awake.render.pipeline.UiPipelineDescriptor
import io.github.awakelab.awake.webgpu.device.GraphicsDevice
import io.github.awakelab.awake.webgpu.fastArrayBufferOf
import io.github.awakelab.awake.webgpu.material.Material
import io.github.awakelab.awake.webgpu.pipeline.toGpuVertexFormat
import io.github.awakelab.awake.webgpu.swapchain.SwapchainManager
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
    private val blendMode: BlendMode = BlendMode.SourceOver,
    private val premultiplied: Boolean = false,
) {
    /** The uniform block's two halves, kept so either can be rewritten without dropping the other. */
    private var screenToNdc = floatArrayOf(0f, 0f, 0f, 0f)
    private var fontInfo = floatArrayOf(0f, 0f)

    constructor(
        graphicsDevice: GraphicsDevice,
        swapchainManager: SwapchainManager,
        shaderCode: ByteArray,
        descriptor: UiPipelineDescriptor,
        font: UiFont? = null,
    ) : this(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        shaderCode = shaderCode,
        kind = when (descriptor.variant) {
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.Quad -> UiPipelineKind.Quad
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.RoundedQuad -> UiPipelineKind.RoundedQuad
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.Glyph -> UiPipelineKind.Glyph
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.Texture -> UiPipelineKind.Texture
            io.github.awakelab.awake.render.pipeline.UiPipelineVariant.TargetComposite -> UiPipelineKind.Texture
        },
        font = font,
        blendMode = descriptor.blendMode,
        premultiplied = descriptor.isPremultiplied,
    )

    private val device = graphicsDevice.wgpuContext.device
    val pipeline: GPURenderPipeline
    private val screenSizeBuffer: GPUBuffer
    private var fontTexture: GPUTexture? = null
    lateinit var bindGroup: GPUBindGroup
        private set
    val screenSizeBindGroup: GPUBindGroup get() = bindGroup
    private val materialBindGroups = HashMap<Material, GPUBindGroup>()

    init {
        val wgslSource = shaderCode.decodeToString().let { source ->
            if (kind != UiPipelineKind.Texture || !premultiplied) source else source.replace(
                "const sourceIsPremultiplied: bool = false;",
                "const sourceIsPremultiplied: bool = true;",
            )
        }
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
                                    // ui_texture.wgsl normalizes both uploaded images and render
                                    // targets to premultiplied output before this fixed blend
                                    // stage, so those pipelines take the colour as it comes.
                                    //
                                    // The others -- quads, rounded quads and glyphs -- emit
                                    // straight alpha, and their colour has to be scaled by coverage
                                    // here or a half-covered pixel paints at full strength. That is
                                    // what turned web text into blobs: this factor was `One` for
                                    // every kind, while Vulkan has always picked per-kind.
                                    srcFactor = if (premultiplied) {
                                        GPUBlendFactor.One
                                    } else {
                                        GPUBlendFactor.SrcAlpha
                                    },
                                    dstFactor = when (blendMode) {
                                        BlendMode.SourceOver -> GPUBlendFactor.OneMinusSrcAlpha
                                        BlendMode.Plus -> GPUBlendFactor.One
                                        BlendMode.Screen, BlendMode.Overlay -> error("$blendMode requires a sampled target composite pass.")
                                    },
                                ),
                                alpha = BlendComponent(
                                    // Alpha is source-over as a coverage value, not a colour
                                    // component: SrcAlpha would square a translucent layer.
                                    srcFactor = GPUBlendFactor.One,
                                    dstFactor = when (blendMode) {
                                        BlendMode.SourceOver -> GPUBlendFactor.OneMinusSrcAlpha
                                        BlendMode.Plus -> GPUBlendFactor.One
                                        BlendMode.Screen, BlendMode.Overlay -> error("$blendMode requires a sampled target composite pass.")
                                    },
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
                size = (8 * Float.SIZE_BYTES).toULong(),
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
                dataLayout = TexelCopyBufferLayout(
                    bytesPerRow = (font.atlasWidth * 4).toUInt(),
                    rowsPerImage = font.atlasHeight.toUInt(),
                ),
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
        } else if (kind != UiPipelineKind.Texture) {
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
        screenToNdc = floatArrayOf(2f / width, -2f / height, -1f, 1f)
        writeUniforms()
    }

    /**
     * Sets the glyph shader's font fields.
     *
     * The glyph shader reads:
     *   fontInfo.x — 1.0 for a distance-field atlas, 0.0 for a coverage-alpha atlas. Zero takes
     *                the coverage branch, which samples an MTSDF atlas's alpha as if it were ink
     *                and turns every glyph edge into a wide linear ramp.
     *   fontInfo.y — distanceFieldRangePx, in atlas texels. Zero collapses `screenPxRange` to 1,
     *                which is the same soft ramp by a different route.
     *
     * Call once when the glyph pipeline is built or the font changes.
     */
    fun writeFontInfo(isDistanceField: Boolean, rangePx: Float) {
        fontInfo = floatArrayOf(if (isDistanceField) 1f else 0f, rangePx)
        writeUniforms()
    }

    /**
     * Writes the whole uniform block from offset zero, the way the Vulkan pipeline does.
     *
     * Not two partial writes at their own offsets: a partial write of `fontInfo` alone reached the
     * buffer but never the shader, so glyphs took the coverage branch and rendered two pixels wider
     * on every side than Vulkan's. Holding both halves and writing them together costs one extra
     * `writeBuffer` at startup and removes the whole class of question.
     */
    private fun writeUniforms() {
        device.queue.writeBuffer(
            screenSizeBuffer,
            0uL,
            fastArrayBufferOf(
                floatArrayOf(
                    screenToNdc[0],
                    screenToNdc[1],
                    screenToNdc[2],
                    screenToNdc[3],
                    fontInfo[0],
                    fontInfo[1],
                    0f,
                    0f,
                ),
            ),
        )
    }

    /** Builds (once) and returns the bind group for [material]'s `previewTextureView`/`previewSampler`. */
    fun bindGroupFor(material: Material): GPUBindGroup {
        // Graphics-layer slots replace their render-target material on resize. Material.destroy()
        // clears both preview handles; prune those dead keys before adding a live one so this
        // pipeline never retains a retired layer and its backend resources.
        materialBindGroups.entries.removeAll { (cachedMaterial, _) ->
            cachedMaterial.previewTextureView == null || cachedMaterial.previewSampler == null
        }
        return materialBindGroups.getOrPut(material) {
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
    }

    fun destroy() {
        materialBindGroups.clear()
        fontTexture?.close()
        screenSizeBuffer.close()
    }
}
