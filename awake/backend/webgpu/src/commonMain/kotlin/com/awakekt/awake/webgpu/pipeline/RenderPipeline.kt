/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.core.geometry.GpuDataShape
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.command.UniformBlockOwner
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.FrontFace
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.renderer.UniformLayout
import com.awakekt.awake.render.renderer.UniformWriter
import com.awakekt.awake.webgpu.WebGpuHandles
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.handles.DescriptorSetLayoutHandle
import com.awakekt.awake.webgpu.swapchain.SwapchainManager
import com.awakekt.awake.webgpu.texture.Texture
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUFrontFace
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.GPUVertexStepMode
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.StencilFaceState
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState

/**
 * Phase 2.5 milestone 2 slice 1 (see docs/mvp-plan.md): real wgpu4k implementation.
 * [descriptorSetLayout] is unused -- WebGPU derives the bind group layout from the shader when
 * no declaration metadata is available. ASL pipelines carry explicit group metadata and use an
 * authored pipeline layout, so bind groups cannot silently drift from the selected entry points.
 * [vertShaderCode] is decoded as UTF-8 WGSL source text (not SPIR-V bytecode) containing
 * both a `vertexMain` and `fragmentMain` entry point -- one shader module is used for both
 * pipeline stages, matching how WGSL is normally authored. [fragShaderCode] is unused (the
 * combined source already has both stages). [renderPass]/[pipelineCache] have no WebGPU
 * equivalent and stay 0.
 *
 * The vertex buffer layout is derived from [vertexFormat]'s own attributes/offsets (rather
 * than a hardcoded three-attribute table plus a bare stride), so any format -- including
 * `PositionNormalColorUv`'s four attributes -- maps without editing this class.
 *
 * [topology] defaults to `TriangleList`; a `LineList` companion pipeline (built with the same
 * shader/vertex layout, just this one field different) is how `Renderer.wireframe` is
 * implemented on this backend -- see `webgpu.renderer.Renderer`'s own doc comment for why
 * (WebGPU has no `VK_POLYGON_MODE_LINE` equivalent).
 */
class RenderPipeline(
    graphicsDevice: GraphicsDevice,
    swapchainManager: SwapchainManager,
    descriptorSetLayout: DescriptorSetLayoutHandle,
    vertShaderCode: ByteArray,
    fragShaderCode: ByteArray,
    val vertexFormat: VertexFormat,
    private val vertexEntryPoint: String = DEFAULT_VERTEX_ENTRY_POINT,
    private val fragmentEntryPoint: String = DEFAULT_FRAGMENT_ENTRY_POINT,
    topology: GPUPrimitiveTopology = GPUPrimitiveTopology.TriangleList,
    /** See [PipelineVariant]'s own doc comment. Defaults to [PipelineVariant.Opaque] -- the
     * pipeline this class always built before any variant existed. Shared with Vulkan so a
     * pipeline shape is described once and each backend only translates it. */
    variant: PipelineVariant = PipelineVariant.Opaque,
    /** `GPUCullMode.None` (default) draws both triangle faces always -- byte-for-byte what this
     * class always built. `GPUCullMode.Back` builds a back-culled companion pipeline for a
     * correctly-wound solid mesh -- see `render.renderer.CullMode`'s own doc comment. Mirrors
     * Vulkan's `RenderPipeline.cullMode`. */
    cullMode: GPUCullMode = GPUCullMode.None,
    val frontFace: FrontFace = FrontFace.CounterClockwise,
    /** Non-null builds this pipeline its OWN uniform buffer plus bind group -- see
     * `PipelineSpec.uniforms`. Mirrors Vulkan's identical parameter; this backend needs no
     * frames-in-flight count because it runs one. */
    uniforms: UniformLayout? = null,
    val bindingLayout: BindingLayout = BindingLayout.Standard,
    /** What this pipeline's own group holds, when it owns one -- see `PipelineSpec
     * .materialBindings`. This decides the entries written against its declared layout. */
    internal val materialBindings: GroupBindings? = null,
    val usesMaterialGroup: Boolean = true,
    /** Exact shader resource ABI keyed by bind-group index, carried from ASL when available. */
    private val bindingsByGroup: Map<Int, GroupBindings> = emptyMap(),
    /** Whether [bindingsByGroup] is authoritative, including an explicitly empty layout. */
    private val bindingsMetadataAvailable: Boolean = false,
) : UniformBlockOwner {
    var renderPass: Long = 0
    var pipelineLayout: Long = 0
    var pipelineCache: Long = 0
    var graphicsPipeline: LongArray

    /** This pipeline as the shared render layer's opaque handle. One per pipeline object and
     * stable across frames, which is what lets the shared feature group draws by identity. */
    val handle: WebGpuPipelineHandle by lazy {
        WebGpuPipelineHandle(
            WebGpuHandles.resolve(graphicsPipeline[0]),
            bindingLayout,
            materialBindings,
            hasGroupZeroBindings = hasDeclaredGroupZeroBindings,
            bindingsByGroup = bindingsByGroup,
        )
    }

    /** Group 0 is present only when the authoritative shader ABI declares it. */
    private val hasDeclaredGroupZeroBindings: Boolean
        get() = if (bindingsMetadataAvailable) 0 in bindingsByGroup else usesMaterialGroup

    private val device = graphicsDevice.wgpuContext.device
    private val uniformBuffer: GPUBuffer? = uniforms?.takeIf { hasDeclaredGroupZeroBindings }?.let {
        device.createBuffer(
            BufferDescriptor(
                size = (it.total * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            ),
        )
    }

    /**
     * This pipeline's group-0 entries: the uniform buffer, plus whatever [materialBindings]
     * declares alongside it.
     *
     * Without a declaration this is the single buffer entry it always was. With one, every
     * declared sampled texture takes its supplied image and every declared sampler takes the
     * lowest-numbered texture's -- the same rule Vulkan's `PerFrameUniformSlots.writeTextures`
     * applies, and for the same reason: `Texture` gives them all identical descriptors today.
     * Missing resources are an authoring error and fail here instead of creating an incomplete
     * bind group that only reports the problem asynchronously during command encoding.
     */
    private fun contentBindGroupEntries(buffer: GPUBuffer): List<BindGroupEntry> {
        val declared = materialBindings ?: return listOf(
            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer)),
        )
        require(declared.entries.any { it.kind == ResourceKind.UniformBuffer }) {
            "Pipeline '$vertexEntryPoint/$fragmentEntryPoint' owns a uniform block but its " +
                "group-0 binding metadata declares no uniform buffer."
        }
        val sharedSampler = contentTextures.entries.minByOrNull { it.key }?.value?.sampler
        return declared.entries.map { entry ->
            val resource = when (entry.kind) {
                ResourceKind.UniformBuffer -> BufferBinding(buffer = buffer)
                ResourceKind.SampledTexture -> requireNotNull(contentTextures[entry.binding]?.view) {
                    "Pipeline '$vertexEntryPoint/$fragmentEntryPoint' declares sampled texture " +
                        "binding ${entry.binding}, but no content texture was supplied."
                }
                ResourceKind.Sampler -> requireNotNull(sharedSampler) {
                    "Pipeline '$vertexEntryPoint/$fragmentEntryPoint' declares sampler binding " +
                        "${entry.binding}, but no content texture supplied a sampler."
                }
                // ContentFeature rejects a storage buffer before it reaches a backend.
                ResourceKind.StorageBuffer -> error(
                    "Pipeline '$vertexEntryPoint/$fragmentEntryPoint' declares unsupported " +
                        "storage binding ${entry.binding} in its material group.",
                )
            }
            BindGroupEntry(binding = entry.binding.toUInt(), resource = resource)
        }
    }

    /** A content feature's textures, supplied by the engine after the registry compiled this
     * pipeline -- see [writeContentTextures]. Read when the bind group is first built. */
    private var contentTextures: Map<Int, Texture> = emptyMap()

    /** Set once the bind group exists, after which [contentTextures] can no longer affect it --
     * a `GPUBindGroup` is immutable, unlike a Vulkan descriptor set. */
    private var bindGroupBuilt = false

    /**
     * Supplies a content feature's textures before the bind group is built.
     *
     * Vulkan's counterpart writes descriptors into sets that already exist; a `GPUBindGroup`
     * cannot be rewritten, so here the textures have to arrive first. They do: the engine
     * supplies them right after the registry lookup, and nothing records a draw until every
     * feature is built. The check makes that ordering a failure rather than a silently
     * texture-less bind group if it ever stops holding.
     */
    fun writeContentTextures(textures: Map<Int, Texture>) {
        if (textures.isEmpty()) return
        check(!bindGroupBuilt) {
            "This pipeline's bind group was already built, and a GPUBindGroup is immutable -- " +
                "content textures have to be supplied before anything binds it."
        }
        contentTextures = textures
    }

    override val uniformBlock: UniformBlock? = uniformBuffer?.takeIf { usesMaterialGroup }?.let { buffer ->
        val layout = requireNotNull(uniforms)
        object : UniformBlock {
            // One bind group, not one per frame: this backend runs a single frame in flight, so
            // Vulkan's per-frame slot array has no counterpart to mirror here.
            private val group by lazy {
                bindGroupBuilt = true
                WebGpuBindGroupHandle(
                    device.createBindGroup(
                        BindGroupDescriptor(
                            layout = WebGpuHandles.resolve<GPURenderPipeline>(graphicsPipeline[0])
                                .getBindGroupLayout(0u),
                            entries = contentBindGroupEntries(buffer),
                        ),
                    ),
                )
            }

            override fun binding(frameIndex: Int): MaterialBinding = group

            override fun write(frameIndex: Int, fill: UniformWriter.() -> Unit) {
                val floats = UniformWriter(layout).apply(fill).build()
                device.queue.writeBuffer(buffer, 0uL, fastArrayBufferOf(floats))
            }
        }
    }

    init {
        check(bindingsMetadataAvailable) {
            "WebGPU RenderPipeline requires explicit shader binding metadata; " +
                "declare bindingsByGroup on the shared PipelineSpec."
        }
        val device = graphicsDevice.wgpuContext.device
        val shaderModule = device.createShaderModule(ShaderModuleDescriptor(code = vertShaderCode.decodeToString()))
        val explicitLayout = device.createAwakePipelineLayout(bindingsByGroup)

        // No layout at all for VertexFormat.None -- a stride-0 buffer no attribute reads is not the
        // same thing as declaring the pipeline takes no vertex buffer. Mirrors Vulkan's
        // vertexInputState.
        val vertexBuffers = mutableListOf<VertexBufferLayout>()
        if (vertexFormat.attributes.isNotEmpty()) {
            vertexBuffers += VertexBufferLayout(
                arrayStride = vertexFormat.strideBytes.toULong(),
                attributes = vertexFormat.entries.map { (attribute, offsetBytes) ->
                    VertexAttribute(
                        shaderLocation = attribute.location.toUInt(),
                        offset = offsetBytes.toULong(),
                        format = attribute.format.toGpuVertexFormat(),
                    )
                },
            )
        }
        if (variant.instanced) {
            // maxOfOrNull, not maxOf: see Vulkan's vertexInputState for why.
            val firstLocation = (vertexFormat.attributes.maxOfOrNull { it.location } ?: -1) + 1
            vertexBuffers += VertexBufferLayout(
                arrayStride = INSTANCE_MATRIX_BYTES.toULong(),
                stepMode = GPUVertexStepMode.Instance,
                attributes = (0 until MATRIX_ROWS).map { row ->
                    VertexAttribute(
                        shaderLocation = (firstLocation + row).toUInt(),
                        offset = (row * VEC4_BYTES).toULong(),
                        format = GPUVertexFormat.Float32x4,
                    )
                },
            )
            if (variant.instanceAlpha) {
                vertexBuffers += VertexBufferLayout(
                    arrayStride = VEC4_BYTES.toULong(),
                    stepMode = GPUVertexStepMode.Instance,
                    attributes = listOf(
                        VertexAttribute(
                            shaderLocation = (firstLocation + MATRIX_ROWS).toUInt(),
                            offset = 0uL,
                            format = GPUVertexFormat.Float32x4,
                        ),
                    ),
                )
            }
            if (variant.instanceFrame) {
                vertexBuffers += VertexBufferLayout(
                    arrayStride = Float.SIZE_BYTES.toULong(),
                    stepMode = GPUVertexStepMode.Instance,
                    attributes = listOf(
                        VertexAttribute(
                            shaderLocation = (firstLocation + MATRIX_ROWS + 1).toUInt(),
                            offset = 0uL,
                            format = GPUVertexFormat.Float32,
                        ),
                    ),
                )
            }
        }

        val pipeline = device.createRenderPipeline(
            RenderPipelineDescriptor(
                layout = explicitLayout,
                vertex = VertexState(
                    module = shaderModule,
                    entryPoint = vertexEntryPoint,
                    buffers = vertexBuffers,
                ),
                fragment = FragmentState(
                    module = shaderModule,
                    entryPoint = fragmentEntryPoint,
                    targets = listOf(
                        ColorTargetState(
                            format = swapchainManager.imageFormatWebGpu,
                            blend = if (variant.blendEnabled) {
                                BlendState(
                                    color = BlendComponent(
                                        srcFactor = GPUBlendFactor.SrcAlpha,
                                        dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                                    ),
                                    alpha = BlendComponent(
                                        srcFactor = GPUBlendFactor.SrcAlpha,
                                        dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
                                    ),
                                )
                            } else {
                                null
                            },
                        ),
                    ),
                ),
                primitive = PrimitiveState(
                    topology = topology,
                    cullMode = cullMode,
                    // Mesh geometry in Awake is authored counter-clockwise when viewed from its
                    // outward-facing side. WebGPU's +Y-up NDC preserves that convention at
                    // rasterization; treating CW as front-facing culls camera-facing surfaces.
                    frontFace = when (frontFace) {
                        FrontFace.CounterClockwise -> GPUFrontFace.CCW
                        FrontFace.Clockwise -> GPUFrontFace.CW
                    },
                ),
                depthStencil = DepthStencilState(
                    format = GPUTextureFormat.Depth32Float,
                    depthWriteEnabled = variant.depthWriteEnabled,
                    // Always is this backend's "test off": WebGPU has no depthTestEnable flag, and
                    // the depthStencil block itself stays mandatory because the pass has a real
                    // Depth32Float attachment. Vulkan spells the same thing depthTestEnable=false.
                    depthCompare = if (variant.depthTestEnabled) {
                        GPUCompareFunction.Less
                    } else {
                        GPUCompareFunction.Always
                    },
                    stencilFront = StencilFaceState(),
                    stencilBack = StencilFaceState(),
                ),
            ),
        )
        graphicsPipeline = longArrayOf(WebGpuHandles.register(pipeline))
    }

    fun destroy() {
        WebGpuHandles.release(graphicsPipeline[0])
        // Inside destroy(), not a separate call a teardown path has to remember: forgetting one
        // companion is exactly how every vertex format leaked a transparent pipeline before
        // PipelineSet.all existed. Vulkan's destroy() drops its slots the same way.
        uniformBuffer?.close()
    }

    private companion object {
        const val DEFAULT_VERTEX_ENTRY_POINT = "vertexMain"
        const val DEFAULT_FRAGMENT_ENTRY_POINT = "fragmentMain"

        /** See the `instanced` constructor parameter -- one `mat4` = 4 `vec4` rows = 64 bytes. */
        const val MATRIX_ROWS = 4
        const val VEC4_BYTES = 16
        const val INSTANCE_MATRIX_BYTES = MATRIX_ROWS * VEC4_BYTES
    }
}

internal fun GpuDataShape.toGpuVertexFormat(): GPUVertexFormat = when (this) {
    GpuDataShape.Float -> GPUVertexFormat.Float32
    GpuDataShape.Vec2 -> GPUVertexFormat.Float32x2
    GpuDataShape.Vec3 -> GPUVertexFormat.Float32x3
    GpuDataShape.Vec4 -> GPUVertexFormat.Float32x4
    GpuDataShape.UInt4 -> GPUVertexFormat.Uint32x4
    GpuDataShape.Mat4 -> error("Mat4 is not a valid vertex-attribute format.")
}
