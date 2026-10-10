/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.passes.SHADOW_DEPTH_BIAS_CONSTANT
import com.awakekt.awake.render.passes.SHADOW_DEPTH_BIAS_SLOPE
import com.awakekt.awake.render.passes.uniforms.CascadePassUniformLayout
import com.awakekt.awake.render.passes.uniforms.SHADOW_CASCADE_PASS_GROUP
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.FrontFace
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.PipelineVariant
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUCullMode
import io.ygdrasil.webgpu.GPUFrontFace
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
 * Colorless graphics pipeline for rendering into a `DepthTarget`: no fragment output, no color
 * attachment, `Depth32Float` only. Takes the caller's shader and vertex layout, so what it
 * renders and from which viewpoint are the caller's business.
 */
class DepthOnlyPipeline(
    graphicsDevice: GraphicsDevice,
    shaderCode: ByteArray,
    /**
     * The vertex layout this pipeline reads. A caster is drawn through it only when its mesh format
     * matches.
     */
    val vertexFormat: VertexFormat,
    vertexEntryPoint: String = "vertexMain",
    fragmentEntryPoint: String = "fragmentMain",
    /** One slot per cascade in this pass's own group-1 block -- see Vulkan's twin for why the
     * cascade matrix cannot live in the per-draw uniform. */
    cascadeCount: Int = 0,
    private val variant: PipelineVariant = PipelineVariant.Opaque,
    /**
     * Which triangle winding this pipeline treats as front-facing; it must agree with the winding
     * of the geometry it renders.
     */
    val frontFace: FrontFace = FrontFace.CounterClockwise,
    /** ABI declared by the selected depth shader, carried from the shared shader set. */
    private val bindingsByGroup: Map<Int, GroupBindings> = emptyMap(),
    /** Whether [bindingsByGroup] is authoritative, including an explicitly empty layout. */
    bindingsMetadataAvailable: Boolean = false,
    /** Another pipeline's group-0 layout to use as this one's, so that pipeline's bind group
     * binds here unchanged; null builds group 0 from [bindingsByGroup]. */
    groupZeroLayout: GPUBindGroupLayout? = null,
    /**
     * Whether a pipeline with a cascade block biases its depth against shadow acne. The scene-depth
     * pass places its casters by a pass matrix too, but feeds raw depth to depth fog: it passes false.
     */
    shadowBias: Boolean = true,
) {
    /** The colorless `Depth32Float` render pipeline built from the caller's shader. */
    val pipeline: GPURenderPipeline

    /**
     * [pipeline] with its bind-group metadata, as the shared render layer's opaque handle. Group 0
     * is assumed present when no shader metadata was supplied, and otherwise only when the depth
     * shader declares it.
     */
    val handle: WebGpuPipelineHandle

    private val device = graphicsDevice.wgpuContext.device

    /** Whether this pipeline's shader declares the pass-scoped cascade block. `scene_depth`
     * renders once from the camera and declares none -- and asking wgpu for the layout of a group
     * a shader never declared aborts the process rather than returning an error. */
    val hasCascadeBlock: Boolean = cascadeCount > 0 && SHADOW_CASCADE_PASS_GROUP in bindingsByGroup

    /** One uniform buffer per declared cascade, written per frame. A count without a matching
     * metadata group is treated as a construction mismatch and does not allocate unreachable
     * buffers or later request a nonexistent bind-group layout. */
    private val cascadeBuffers: List<GPUBuffer> = List(if (hasCascadeBlock) cascadeCount else 0) {
        device.createBuffer(
            BufferDescriptor(
                size = (CascadePassUniformLayout.total * Float.SIZE_BYTES).toULong(),
                usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
            ),
        )
    }

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

    private val materialBindGroups = mutableMapOf<GPUBuffer, WebGpuBindGroupHandle>()
    private val paletteBindGroups = mutableMapOf<GPUBuffer, WebGpuBindGroupHandle>()

    /** Rebinds a prepared draw's uniform buffer against this depth pipeline's own explicit layout.
     * WebGPU bind groups are pipeline-specific even when both groups contain one uniform buffer. */
    fun materialBinding(buffer: GPUBuffer): MaterialBinding? {
        if (!handle.hasBindingGroup(0)) return null
        return materialBindGroups.getOrPut(buffer) {
            WebGpuBindGroupHandle(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = pipeline.getBindGroupLayout(0u),
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = buffer)),
                        ),
                    ),
                ),
            )
        }
    }

    /** Uses the material's textures with the prepared per-draw uniform buffer for keyed
     * alpha-tested depth variants. */
    fun materialBinding(
        material: com.awakekt.awake.webgpu.material.Material,
        uniformBuffer: GPUBuffer,
    ): MaterialBinding? {
        if (!handle.hasBindingGroup(0)) return null
        // This shader's own group 0, which may use fewer of the material's textures than a scene
        // pipeline declares; a group with more entries than its layout is invalid.
        val declared = bindingsByGroup[0] ?: GroupBindings.StandardMaterial
        return WebGpuBindGroupHandle(material.bindGroupFor(pipeline, uniformBuffer, declared))
    }

    /**
     * Builds, once per [buffer], the joint-palette bind group for skinned casters.
     *
     * @param buffer The per-instance joint-palette storage buffer, bound at binding 0.
     * @return The cached bind group, or `null` when this depth shader declares no joint-palette
     * group.
     */
    fun paletteBinding(buffer: GPUBuffer): MaterialBinding? {
        val group = BindingLayout.Standard.slot(BindingSemantic.JointPalette)
        if (!handle.hasBindingGroup(group)) return null
        return paletteBindGroups.getOrPut(buffer) {
            WebGpuBindGroupHandle(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = pipeline.getBindGroupLayout(group.toUInt()),
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
        device.queue.writeBufferData(buffer, 0uL, fastArrayBufferOf(viewProjection.data))
    }

    init {
        check(bindingsMetadataAvailable) {
            "WebGPU DepthOnlyPipeline requires explicit shader binding metadata; " +
                "declare bindingsByGroup on the shared depth shader definition."
        }
        val device = graphicsDevice.wgpuContext.device
        val wgslSource = shaderCode.decodeToString()
        val shaderModule = device.createShaderModule(ShaderModuleDescriptor(code = wgslSource))

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
            val firstLocation = (vertexFormat.attributes.maxOfOrNull { it.location } ?: -1) + 1
            vertexBuffers += VertexBufferLayout(
                arrayStride = (4 * 4 * Float.SIZE_BYTES).toULong(),
                stepMode = GPUVertexStepMode.Instance,
                attributes = (0 until 4).map { row ->
                    VertexAttribute(
                        shaderLocation = (firstLocation + row).toUInt(),
                        offset = (row * 4 * Float.SIZE_BYTES).toULong(),
                        format = GPUVertexFormat.Float32x4,
                    )
                },
            )
            if (variant.instanceAlpha) {
                vertexBuffers += VertexBufferLayout(
                    arrayStride = (4 * Float.SIZE_BYTES).toULong(),
                    stepMode = GPUVertexStepMode.Instance,
                    attributes = listOf(
                        VertexAttribute(
                            shaderLocation = (firstLocation + 4).toUInt(),
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
                            shaderLocation = (firstLocation + 5).toUInt(),
                            offset = 0uL,
                            format = GPUVertexFormat.Float32,
                        ),
                    ),
                )
            }
        }

        pipeline = device.createRenderPipeline(
            RenderPipelineDescriptor(
                layout = device.createAwakePipelineLayout(bindingsByGroup, groupZeroLayout),
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
                    // Mesh geometry is authored counter-clockwise when viewed from its outward-facing side.
                    frontFace = when (frontFace) {
                        FrontFace.CounterClockwise -> GPUFrontFace.CCW
                        FrontFace.Clockwise -> GPUFrontFace.CW
                    },
                ),
                // Bias only the cascade (light-space) pass -- same reasoning and constants as
                // Vulkan's twin: the scene-depth pass feeds depth_fog, which reads raw depth.
                depthStencil = DepthStencilState(
                    format = GPUTextureFormat.Depth32Float,
                    depthWriteEnabled = true,
                    depthCompare = GPUCompareFunction.LessEqual,
                    stencilFront = StencilFaceState(),
                    stencilBack = StencilFaceState(),
                    depthBias = if (hasCascadeBlock && shadowBias) SHADOW_DEPTH_BIAS_CONSTANT.toInt() else 0,
                    depthBiasSlopeScale = if (hasCascadeBlock && shadowBias) SHADOW_DEPTH_BIAS_SLOPE else 0f,
                ),
            ),
        )
        handle = WebGpuPipelineHandle(
            pipeline,
            hasGroupZeroBindings = !bindingsMetadataAvailable || 0 in bindingsByGroup,
            bindingsByGroup = bindingsByGroup,
        )
    }

    /**
     * Closes the per-cascade uniform buffers and drops the cached bind groups. The pipeline object
     * itself has no explicit release.
     */
    fun destroy() {
        cascadeBuffers.forEach { it.close() }
        materialBindGroups.clear()
        paletteBindGroups.clear()
        // Everything else is garbage collected in the JS runtime
    }
}
