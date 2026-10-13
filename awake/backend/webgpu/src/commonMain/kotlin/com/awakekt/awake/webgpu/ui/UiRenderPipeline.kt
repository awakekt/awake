/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.core.math.Vec4
import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.font.samplingInfo
import com.awakekt.awake.render.passes2d.UiPipelineKind
import com.awakekt.awake.render.passes2d.UiUniformLayouts
import com.awakekt.awake.render.passes2d.uiFontInfo
import com.awakekt.awake.render.passes2d.uiUniformFloats
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.UiPipelineDescriptor
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.pipeline.createAwakePipelineLayout
import com.awakekt.awake.webgpu.pipeline.toGpuVertexFormat
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.writeBufferData
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
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUMipmapFilterMode
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
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
    private val swapchainManager: SwapchainManager,
    shaderCode: ByteArray,
    /**
     * Which UI primitive this pipeline draws, which fixes its vertex layout and the shape of its
     * group 0.
     */
    val kind: UiPipelineKind = UiPipelineKind.Quad,
    private val blendMode: BlendMode = BlendMode.SourceOver,
    private val premultiplied: Boolean = false,
) {
    /** The screen half of every uniform block this pipeline writes; each font holds its own other half. */
    private var screenToNdc = Vec4()

    constructor(
        graphicsDevice: GraphicsDevice,
        swapchainManager: SwapchainManager,
        shaderCode: ByteArray,
        descriptor: UiPipelineDescriptor,
    ) : this(
        graphicsDevice = graphicsDevice,
        swapchainManager = swapchainManager,
        shaderCode = shaderCode,
        kind = when (descriptor.variant) {
            com.awakekt.awake.render.pipeline.UiPipelineVariant.Quad -> UiPipelineKind.Quad
            com.awakekt.awake.render.pipeline.UiPipelineVariant.RoundedQuad -> UiPipelineKind.RoundedQuad
            com.awakekt.awake.render.pipeline.UiPipelineVariant.Glyph -> UiPipelineKind.Glyph
            com.awakekt.awake.render.pipeline.UiPipelineVariant.Texture -> UiPipelineKind.Texture
            com.awakekt.awake.render.pipeline.UiPipelineVariant.TargetComposite -> UiPipelineKind.Texture
        },
        blendMode = descriptor.blendMode,
        premultiplied = descriptor.isPremultiplied,
    )

    private val device = graphicsDevice.wgpuContext.device

    /**
     * The current `GPURenderPipeline`. It is replaced when the shader is rebuilt, so read it per
     * draw rather than caching it.
     */
    var pipeline: GPURenderPipeline
        private set
    private val screenSizeBuffer: GPUBuffer

    /**
     * Group-0 bind group holding the screen-size uniform. It is not built for texture or glyph
     * pipelines, whose bind groups come per material and per font, so reading it there throws.
     */
    lateinit var bindGroup: GPUBindGroup
        private set

    /** Alias of [bindGroup], named for the uniform it carries. */
    val screenSizeBindGroup: GPUBindGroup get() = bindGroup
    private val materialBindGroups = HashMap<Material, GPUBindGroup>()

    /** One font's atlas, its own uniform block, and the bind group over both. */
    internal class FontBinding(
        val texture: GPUTexture,
        val view: GPUTextureView,
        val sampler: GPUSampler,
        val uniforms: GPUBuffer,
        val fontInfo: Vec4,
    ) {
        var bindGroup: GPUBindGroup? = null
    }

    /**
     * Every font drawn through this glyph pipeline, by identity: uploaded on its first draw and kept
     * until [destroy]. A font no longer drawn keeps its atlas; there is no eviction yet.
     */
    internal val fontBindings = HashMap<UiFont, FontBinding>()

    init {
        val initialWgslSource = shaderCode.decodeToString()
        pipeline = buildPipeline(initialWgslSource, vertexEntryPoint = "vertexMain", fragmentEntryPoint = "fragmentMain")

        screenSizeBuffer = device.createUiUniformBuffer()

        recreateBindGroup()

        val renderingContext = graphicsDevice.wgpuContext.renderingContext
        writeScreenSize(renderingContext.width.toFloat(), renderingContext.height.toFloat())
    }

    private fun recreateBindGroup() {
        if (kind == UiPipelineKind.Quad || kind == UiPipelineKind.RoundedQuad) {
            bindGroup = device.createBindGroup(
                BindGroupDescriptor(
                    layout = pipeline.getBindGroupLayout(0u),
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = screenSizeBuffer)),
                    ),
                ),
            )
        }
    }

    internal fun buildPipeline(
        wgslSource: String,
        vertexEntryPoint: String = "vertexMain",
        fragmentEntryPoint: String = "fragmentMain",
    ): GPURenderPipeline {
        val processedSource = if (kind != UiPipelineKind.Texture || !premultiplied) {
            wgslSource
        } else {
            wgslSource.replace(
                "const sourceIsPremultiplied: bool = false;",
                "const sourceIsPremultiplied: bool = true;",
            )
        }
        val shaderModule = device.createShaderModule(ShaderModuleDescriptor(code = processedSource))
        val groupBindings = mapOf(
            0 to when (kind) {
                UiPipelineKind.Glyph, UiPipelineKind.Texture -> GroupBindings.TexturedMaterial
                UiPipelineKind.Quad, UiPipelineKind.RoundedQuad -> GroupBindings.UniformOnlyMaterial
            },
        )
        try {
            return device.createRenderPipeline(
                RenderPipelineDescriptor(
                    layout = device.createAwakePipelineLayout(groupBindings),
                    vertex = VertexState(
                        module = shaderModule,
                        entryPoint = vertexEntryPoint,
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = kind.vertexFormat.strideBytes.toULong(),
                                attributes = createVertexAttributes(),
                            ),
                        ),
                    ),
                    fragment = FragmentState(
                        module = shaderModule,
                        entryPoint = fragmentEntryPoint,
                        targets = listOf(
                            ColorTargetState(
                                format = swapchainManager.imageFormatWebGpu,
                                blend = createBlendState(),
                            ),
                        ),
                    ),
                    primitive = PrimitiveState(topology = GPUPrimitiveTopology.TriangleList),
                ),
            )
        } finally {
            shaderModule.close()
        }
    }

    private fun createVertexAttributes(): List<VertexAttribute> =
        kind.vertexFormat.entries.map { entry ->
            VertexAttribute(
                shaderLocation = entry.attribute.location.toUInt(),
                offset = entry.offsetBytes.toULong(),
                format = entry.attribute.format.toGpuVertexFormat(),
            )
        }

    private fun createBlendState(): BlendState {
        val srcFactor = if (premultiplied) GPUBlendFactor.One else GPUBlendFactor.SrcAlpha
        val dstFactor = when (blendMode) {
            BlendMode.SourceOver -> GPUBlendFactor.OneMinusSrcAlpha
            BlendMode.Plus -> GPUBlendFactor.One
            BlendMode.Screen, BlendMode.Overlay -> error("$blendMode requires a sampled target composite pass.")
        }
        return BlendState(
            color = BlendComponent(srcFactor = srcFactor, dstFactor = dstFactor),
            alpha = BlendComponent(srcFactor = GPUBlendFactor.One, dstFactor = dstFactor),
        )
    }

    internal fun swapIn(newPipeline: GPURenderPipeline): GPURenderPipeline {
        val old = pipeline
        pipeline = newPipeline
        materialBindGroups.clear()
        fontBindings.values.forEach { it.bindGroup = null }
        recreateBindGroup()
        return old
    }

    /** Call once at construction and again whenever the canvas resizes. */
    fun writeScreenSize(width: Float, height: Float) {
        screenToNdc = Vec4(2f / width, -2f / height, -1f, 1f)
        writeUniforms()
    }

    private fun writeUniforms() {
        device.writeUiUniforms(screenSizeBuffer, screenToNdc, NO_FONT_INFO)
        for (binding in fontBindings.values) device.writeUiUniforms(binding.uniforms, screenToNdc, binding.fontInfo)
    }

    /**
     * The group-0 bind group that samples [font]'s atlas with its own `fontInfo`. The atlas is
     * uploaded and the group built the first time [font] is drawn; after that a font switch is one
     * `setBindGroup`, never a pipeline rebuild or a second upload.
     */
    fun fontBindGroup(font: UiFont): GPUBindGroup {
        check(kind == UiPipelineKind.Glyph) { "fontBindGroup requires a glyph UiRenderPipeline." }
        val binding = fontBindings.getOrPut(font) {
            device.uploadFont(font).also { device.writeUiUniforms(it.uniforms, screenToNdc, it.fontInfo) }
        }
        return binding.bindGroup ?: device.createBindGroup(
            BindGroupDescriptor(
                layout = pipeline.getBindGroupLayout(0u),
                entries = listOf(
                    BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = binding.uniforms)),
                    BindGroupEntry(binding = 1u, resource = binding.view),
                    BindGroupEntry(binding = 2u, resource = binding.sampler),
                ),
            ),
        ).also { binding.bindGroup = it }
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

    /**
     * Drops the cached per-material bind groups and closes every font's atlas and uniform buffer and
     * the screen-size uniform buffer.
     */
    fun destroy() {
        materialBindGroups.clear()
        fontBindings.values.forEach {
            it.texture.close()
            it.uniforms.close()
        }
        fontBindings.clear()
        screenSizeBuffer.close()
    }
}

/** The `fontInfo` lanes of a uniform block no glyph reads. */
private val NO_FONT_INFO = Vec4(0f, 0f, 0f, 0f)

/**
 * Writes a whole UI uniform block from offset zero, the way the Vulkan pipeline does.
 *
 * Not two partial writes at their own offsets: a partial write of `fontInfo` alone reached the
 * buffer but never the shader, so glyphs took the coverage branch and rendered two pixels wider
 * on every side than Vulkan's. Writing both halves together removes the whole class of question.
 */
private fun GPUDevice.writeUiUniforms(buffer: GPUBuffer, screenToNdc: Vec4, fontInfo: Vec4) {
    queue.writeBufferData(buffer, 0uL, fastArrayBufferOf(uiUniformFloats(screenToNdc, fontInfo)))
}

/** A buffer sized for one UI uniform block. */
private fun GPUDevice.createUiUniformBuffer(): GPUBuffer = createBuffer(
    BufferDescriptor(
        size = (UiUniformLayouts.Buffer.total * Float.SIZE_BYTES).toULong(),
        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
    ),
)

/** Uploads [font]'s atlas once, with a uniform block of its own carrying its `fontInfo`. */
private fun GPUDevice.uploadFont(font: UiFont): UiRenderPipeline.FontBinding {
    val size = Extent3D(width = font.atlasWidth.toUInt(), height = font.atlasHeight.toUInt())
    val texture = createTexture(
        TextureDescriptor(
            size = size,
            format = GPUTextureFormat.RGBA8Unorm,
            usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
            dimension = GPUTextureDimension.TwoD,
        ),
    )
    queue.writeTexture(
        destination = TexelCopyTextureInfo(texture = texture),
        data = fastArrayBufferOf(font.atlasPixelsRgba),
        dataLayout = TexelCopyBufferLayout(
            bytesPerRow = (font.atlasWidth * 4).toUInt(),
            rowsPerImage = font.atlasHeight.toUInt(),
        ),
        size = size,
    )
    val sampler = createSampler(
        SamplerDescriptor(
            addressModeU = GPUAddressMode.ClampToEdge,
            addressModeV = GPUAddressMode.ClampToEdge,
            addressModeW = GPUAddressMode.ClampToEdge,
            magFilter = GPUFilterMode.Linear,
            minFilter = GPUFilterMode.Linear,
            mipmapFilter = GPUMipmapFilterMode.Linear,
        ),
    )
    return UiRenderPipeline.FontBinding(
        texture = texture,
        view = texture.createView(TextureViewDescriptor()),
        sampler = sampler,
        uniforms = createUiUniformBuffer(),
        fontInfo = uiFontInfo(font.samplingInfo),
    )
}
