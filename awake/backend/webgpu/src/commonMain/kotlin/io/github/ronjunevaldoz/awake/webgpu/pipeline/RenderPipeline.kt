// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.pipeline

import io.github.ronjunevaldoz.awake.core.geometry.GpuDataShape
import io.github.ronjunevaldoz.awake.core.geometry.VertexFormat
import io.github.ronjunevaldoz.awake.render.pipeline.PipelineVariant
import io.github.ronjunevaldoz.awake.webgpu.WebGpuHandles
import io.github.ronjunevaldoz.awake.webgpu.device.GraphicsDevice
import io.github.ronjunevaldoz.awake.webgpu.handles.DescriptorSetLayoutHandle
import io.github.ronjunevaldoz.awake.webgpu.swapchain.SwapchainManager
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUFrontFace
import io.ygdrasil.webgpu.GPUPrimitiveTopology
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
 * [descriptorSetLayout] is unused -- WebGPU derives the bind group layout from the shader
 * itself ("auto" pipeline layout), unlike Vulkan's explicit `VkDescriptorSetLayout`.
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
    vertexEntryPoint: String = DEFAULT_VERTEX_ENTRY_POINT,
    fragmentEntryPoint: String = DEFAULT_FRAGMENT_ENTRY_POINT,
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
) {
    var renderPass: Long = 0
    var pipelineLayout: Long = 0
    var pipelineCache: Long = 0
    var graphicsPipeline: LongArray

    /** This pipeline as the shared render layer's opaque handle. One per pipeline object and
     * stable across frames, which is what lets the shared feature group draws by identity. */
    val handle: WebGpuPipelineHandle by lazy {
        WebGpuPipelineHandle(WebGpuHandles.resolve(graphicsPipeline[0]))
    }

    init {
        val device = graphicsDevice.wgpuContext.device
        val wgslSource = vertShaderCode.decodeToString()
        val shaderModule = device.createShaderModule(ShaderModuleDescriptor(code = wgslSource))

        val vertexBuffers = mutableListOf(
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
        if (variant.instanced) {
            val firstLocation = vertexFormat.attributes.maxOf { it.location } + 1
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
                    frontFace = GPUFrontFace.CW,
                ),
                depthStencil = DepthStencilState(
                    format = GPUTextureFormat.Depth32Float,
                    depthWriteEnabled = variant.depthWriteEnabled,
                    depthCompare = GPUCompareFunction.Less,
                    stencilFront = StencilFaceState(),
                    stencilBack = StencilFaceState(),
                ),
            ),
        )
        graphicsPipeline = longArrayOf(WebGpuHandles.register(pipeline))
    }

    fun bind(commandBuffer: Long) {
        TODO("WebGPU render-pass binding happens in Renderer.draw() directly, see docs/mvp-plan.md")
    }

    fun destroy() {
        WebGpuHandles.release(graphicsPipeline[0])
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
