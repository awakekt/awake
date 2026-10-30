/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.graphics2d.BlendMode
import com.awakekt.awake.render.imagepass.ImagePassAddressMode
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.imagepass.ImagePassDestination
import com.awakekt.awake.render.imagepass.ImagePassFilterMode
import com.awakekt.awake.render.imagepass.ImagePassRect
import com.awakekt.awake.render.imagepass.ImagePassSamplerSpec
import com.awakekt.awake.render.imagepass.ImagePassShaderSpec
import com.awakekt.awake.render.imagepass.ImagePassSource
import com.awakekt.awake.render.imagepass.ImagePassStep
import com.awakekt.awake.render.imagepass.ImagePassTargetFormat
import com.awakekt.awake.render.imagepass.ImagePassTargetSpec
import com.awakekt.awake.render.imagepass.ImagePassTextureBinding
import com.awakekt.awake.render.pipeline.GroupBindings
import com.awakekt.awake.render.pipeline.ResourceKind
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.render.texture.TextureAsset
import com.awakekt.awake.webgpu.device.GraphicsDevice
import com.awakekt.awake.webgpu.fastArrayBufferOf
import com.awakekt.awake.webgpu.texture.OffscreenRenderTarget
import com.awakekt.awake.webgpu.texture.Texture
import com.awakekt.awake.webgpu.ui.DynamicMesh
import com.awakekt.awake.webgpu.writeBufferData
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMipmapFilterMode
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUPrimitiveTopology
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureDimension
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
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
import kotlin.math.roundToInt

/**
 * Describes the currently active WebGPU render target interrupted and resumed by
 * [WebGpuImagePassExecutor].
 */
internal class WebGpuImagePassParentTarget(
    val colorTexture: GPUTexture,
    val colorView: GPUTextureView,
    val colorFormat: GPUTextureFormat,
    val width: Int,
    val height: Int,
    val isOffscreen: Boolean,
    val sampleable: Boolean = true,
)

/**
 * Generic WebGPU executor for multi-step [ImagePassChain] graphs.
 *
 * Compiles and caches WGSL shaders by [ImagePassShaderSpec.id], pools transient render targets by
 * `(id, width, height, format)`, and handles mid-pass interruption and resumption with
 * `loadOp = GPULoadOp.Load`.
 */
@Suppress("TooManyFunctions")
internal class WebGpuImagePassExecutor(
    private val graphicsDevice: GraphicsDevice,
    private val meshProvider: (Int) -> DynamicMesh,
    private val textureProvider: (TextureAsset) -> Texture,
) {
    private val device: GPUDevice get() = graphicsDevice.wgpuContext.device

    private val transientTargets = LinkedHashMap<String, PooledTransientTarget>()
    private var snapshotTarget: PooledTransientTarget? = null
    private val samplers = HashMap<ImagePassSamplerSpec, GPUSampler>()
    private val compiledShaders = HashMap<String, CompiledShader>()
    private val pipelines = HashMap<PipelineKey, GPURenderPipeline>()
    private val stepSlotsByShader = HashMap<String, MutableList<StepUniformSlot>>()
    private val stepSlotsUsed = HashMap<String, Int>()
    private var meshStepsUsed: Int = 0

    /** Each step's bind group, built per draw and closed when the next pass begins. */
    private val stepBindGroups = mutableListOf<GPUBindGroup>()

    /** Number of transient targets currently held in the pool. */
    internal val pooledTargetCount: Int
        get() = transientTargets.size

    /** Total number of transient GPU texture allocations performed over this executor's lifetime. */
    internal var totalTargetAllocations: Int = 0
        private set

    /** Total number of transient target lookups satisfied from the pool without reallocation. */
    internal var totalTargetReuses: Int = 0
        private set

    /** Returns `(width, height)` for the pooled target [id], or `null` if not allocated. */
    internal fun pooledTargetDimensions(id: String): Pair<Int, Int>? =
        transientTargets[id]?.let { it.width to it.height }

    /** Resets per-submission uniform and mesh slot counters before recording a new UI pass. */
    fun beginPass() {
        closeStepBindGroups()
        stepSlotsUsed.clear()
        meshStepsUsed = 0
    }

    private fun closeStepBindGroups() {
        stepBindGroups.forEach(GPUBindGroup::close)
        stepBindGroups.clear()
    }

    /**
     * Interrupts [currentPassEncoder] on [parentTarget], executes [chain]'s transient and
     * composite steps on [commandEncoder], and returns a resumed [GPURenderPassEncoder] on
     * [parentTarget] with `loadOp = GPULoadOp.Load`.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    fun executeImagePass(
        commandEncoder: GPUCommandEncoder,
        currentPassEncoder: GPURenderPassEncoder,
        parentTarget: WebGpuImagePassParentTarget,
        chain: ImagePassChain,
        stats: RenderStatsCounter,
    ): GPURenderPassEncoder {
        val canExecute = !chain.isEmpty && (!chain.requiresDestinationSnapshot || parentTarget.sampleable)
        if (!canExecute) return currentPassEncoder

        val referenceWidth = chain.sourceBounds.width.roundToInt().coerceAtLeast(1)
        val referenceHeight = chain.sourceBounds.height.roundToInt().coerceAtLeast(1)
        val activeTransients = HashMap<String, PooledTransientTarget>(chain.transientTargets.size)
        for (spec in chain.transientTargets) {
            activeTransients[spec.id] = acquireTransientTarget(
                spec = spec,
                destinationFormat = parentTarget.colorFormat,
                referenceWidth = referenceWidth,
                referenceHeight = referenceHeight,
            )
        }

        currentPassEncoder.end()

        val needsCopiedSnapshot = chain.requiresDestinationSnapshot && (
            !parentTarget.isOffscreen ||
                chain.steps.any { step ->
                    step.destination is ImagePassDestination.ActiveTarget &&
                        step.inputs.any { it.source == ImagePassSource.DestinationSnapshot }
                }
            )
        val snapshotView = if (needsCopiedSnapshot) {
            val copyTarget = ensureSnapshotTarget(
                width = parentTarget.width,
                height = parentTarget.height,
                format = parentTarget.colorFormat,
            )
            commandEncoder.copyTextureToTexture(
                source = TexelCopyTextureInfo(texture = parentTarget.colorTexture),
                destination = TexelCopyTextureInfo(texture = copyTarget.texture),
                copySize = Extent3D(
                    width = parentTarget.width.toUInt(),
                    height = parentTarget.height.toUInt(),
                ),
            )
            copyTarget.view
        } else {
            parentTarget.colorView
        }

        var activeParentPass: GPURenderPassEncoder? = null

        for (stepIndex in chain.steps.indices) {
            val step = chain.steps[stepIndex]
            when (val destination = step.destination) {
                is ImagePassDestination.TransientTarget -> {
                    if (activeParentPass != null) {
                        activeParentPass.end()
                        activeParentPass = null
                    }
                    val target = checkNotNull(activeTransients[destination.id]) {
                        "ImagePassStep references undeclared transient target '${destination.id}'."
                    }
                    val useClear = step.clearDestination || !target.initialized
                    val transientPass = commandEncoder.beginRenderPass(
                        RenderPassDescriptor(
                            colorAttachments = listOf(
                                RenderPassColorAttachment(
                                    view = target.view,
                                    loadOp = if (useClear) GPULoadOp.Clear else GPULoadOp.Load,
                                    clearValue = Color(0.0, 0.0, 0.0, 0.0),
                                    storeOp = GPUStoreOp.Store,
                                ),
                            ),
                        ),
                    )
                    transientPass.setViewport(0f, 0f, target.width.toFloat(), target.height.toFloat(), 0f, 1f)
                    transientPass.setScissorRect(0u, 0u, target.width.toUInt(), target.height.toUInt())
                    recordStepDraw(
                        passEncoder = transientPass,
                        step = step,
                        targetFormat = target.format,
                        blendEnabled = false,
                        blendMode = BlendMode.SourceOver,
                        isPremultiplied = true,
                        snapshotView = snapshotView,
                        activeTransients = activeTransients,
                        stats = stats,
                    )
                    transientPass.end()
                    target.initialized = true
                }

                is ImagePassDestination.ActiveTarget -> {
                    val parentPass = activeParentPass ?: beginParentResumePass(commandEncoder, parentTarget).also {
                        activeParentPass = it
                    }
                    applyScissor(parentPass, destination.scissorRect, parentTarget.width, parentTarget.height)
                    recordStepDraw(
                        passEncoder = parentPass,
                        step = step,
                        targetFormat = parentTarget.colorFormat,
                        blendEnabled = destination.blendEnabled,
                        blendMode = destination.blendMode,
                        isPremultiplied = destination.isPremultiplied,
                        snapshotView = snapshotView,
                        activeTransients = activeTransients,
                        stats = stats,
                    )
                }
            }
        }

        val resumedPass = activeParentPass
        if (resumedPass == null) {
            return beginParentResumePass(commandEncoder, parentTarget)
        }
        resumedPass.setViewport(0f, 0f, parentTarget.width.toFloat(), parentTarget.height.toFloat(), 0f, 1f)
        resumedPass.setScissorRect(0u, 0u, parentTarget.width.toUInt(), parentTarget.height.toUInt())
        return resumedPass
    }

    /** Releases all pooled transient targets while keeping compiled shaders and pipelines intact. */
    fun releaseTransientTargets() {
        transientTargets.values.forEach { it.destroy() }
        transientTargets.clear()
        snapshotTarget?.destroy()
        snapshotTarget = null
    }

    /** Frees all pooled transient targets, uniform buffers, samplers, shaders, and pipelines. */
    fun destroy() {
        closeStepBindGroups()
        releaseTransientTargets()
        stepSlotsByShader.values.forEach { slots ->
            slots.forEach { it.destroy() }
        }
        stepSlotsByShader.clear()
        stepSlotsUsed.clear()
        pipelines.values.forEach(GPURenderPipeline::close)
        pipelines.clear()
        compiledShaders.values.forEach { it.destroy() }
        compiledShaders.clear()
        samplers.values.forEach { it.close() }
        samplers.clear()
    }

    private fun beginParentResumePass(
        commandEncoder: GPUCommandEncoder,
        parentTarget: WebGpuImagePassParentTarget,
    ): GPURenderPassEncoder {
        val pass = commandEncoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = parentTarget.colorView,
                        loadOp = GPULoadOp.Load,
                        storeOp = GPUStoreOp.Store,
                    ),
                ),
            ),
        )
        pass.setViewport(0f, 0f, parentTarget.width.toFloat(), parentTarget.height.toFloat(), 0f, 1f)
        pass.setScissorRect(0u, 0u, parentTarget.width.toUInt(), parentTarget.height.toUInt())
        return pass
    }

    private fun applyScissor(
        passEncoder: GPURenderPassEncoder,
        scissorRect: ImagePassRect?,
        targetWidth: Int,
        targetHeight: Int,
    ) {
        if (scissorRect == null) {
            passEncoder.setScissorRect(0u, 0u, targetWidth.toUInt(), targetHeight.toUInt())
            return
        }
        val clamped = scissorRect.clampTo(targetWidth.toFloat(), targetHeight.toFloat())
        val x = clamped.x.roundToInt().coerceIn(0, targetWidth)
        val y = clamped.y.roundToInt().coerceIn(0, targetHeight)
        val w = clamped.width.roundToInt().coerceAtLeast(0).coerceAtMost(targetWidth - x)
        val h = clamped.height.roundToInt().coerceAtLeast(0).coerceAtMost(targetHeight - y)
        passEncoder.setScissorRect(x.toUInt(), y.toUInt(), w.toUInt(), h.toUInt())
    }

    @Suppress("LongParameterList")
    private fun recordStepDraw(
        passEncoder: GPURenderPassEncoder,
        step: ImagePassStep,
        targetFormat: GPUTextureFormat,
        blendEnabled: Boolean,
        blendMode: BlendMode,
        isPremultiplied: Boolean,
        snapshotView: GPUTextureView,
        activeTransients: Map<String, PooledTransientTarget>,
        stats: RenderStatsCounter,
    ) {
        val compiled = ensureCompiledShader(step.shader)
        val pipeline = ensurePipeline(
            compiled = compiled,
            targetFormat = targetFormat,
            blendEnabled = blendEnabled,
            blendMode = blendMode,
            isPremultiplied = isPremultiplied,
        )
        val slot = acquireStepSlot(compiled)
        if (slot.uniformBuffer != null && step.uniformData.isNotEmpty()) {
            device.queue.writeBufferData(
                slot.uniformBuffer,
                0uL,
                fastArrayBufferOf(step.uniformData),
            )
        }
        val bindGroup = createStepBindGroup(
            pipeline = pipeline,
            compiled = compiled,
            uniformBuffer = slot.uniformBuffer,
            inputs = step.inputs,
            snapshotView = snapshotView,
            activeTransients = activeTransients,
        ).also(stepBindGroups::add)
        passEncoder.setPipeline(pipeline)
        passEncoder.bindGroup(0u, bindGroup)

        val vertices = if (step.hasCustomGeometry) step.quadVertices else DEFAULT_FULLSCREEN_QUAD_VERTICES
        val indices = if (step.hasCustomGeometry) {
            IntArray(step.quadIndices.size) { index -> step.quadIndices[index].toInt() and USHORT_MASK }
        } else {
            DEFAULT_FULLSCREEN_QUAD_INDICES
        }
        val meshIndex = meshStepsUsed
        meshStepsUsed = meshIndex + 1
        val mesh = meshProvider(meshIndex)
        mesh.update(vertices, indices)
        passEncoder.setVertexBuffer(0u, mesh.vertexBufferRef())
        passEncoder.setIndexBuffer(mesh.indexBufferRef(), DynamicMesh.indexFormat)
        passEncoder.drawIndexed(mesh.drawIndexCount.toUInt())
        stats.recordDraw(mesh.drawIndexCount)
    }

    @Suppress("LongParameterList")
    private fun createStepBindGroup(
        pipeline: GPURenderPipeline,
        compiled: CompiledShader,
        uniformBuffer: GPUBuffer?,
        inputs: List<ImagePassTextureBinding>,
        snapshotView: GPUTextureView,
        activeTransients: Map<String, PooledTransientTarget>,
    ) = device.createBindGroupReleasingLayout(
        BindGroupDescriptor(
            layout = pipeline.getBindGroupLayout(0u),
            entries = buildList {
                if (compiled.uniformBinding != null && uniformBuffer != null) {
                    add(
                        BindGroupEntry(
                            binding = compiled.uniformBinding.toUInt(),
                            resource = BufferBinding(buffer = uniformBuffer),
                        ),
                    )
                }
                val samplerEntries = compiled.spec.groupBindings.entries.filter { it.kind == ResourceKind.Sampler }
                for (index in inputs.indices) {
                    val input = inputs[index]
                    val view = when (val source = input.source) {
                        ImagePassSource.DestinationSnapshot -> snapshotView
                        is ImagePassSource.TransientTarget ->
                            checkNotNull(activeTransients[source.id]) {
                                "ImagePassStep references unknown transient target '${source.id}'."
                            }.view
                        is ImagePassSource.RenderTargetColor ->
                            (source.target as OffscreenRenderTarget).colorView
                        is ImagePassSource.Texture ->
                            textureProvider(source.texture).view
                    }
                    add(BindGroupEntry(binding = input.binding.toUInt(), resource = view))
                    val samplerBinding = samplerEntries.firstOrNull { it.binding == input.binding + 1 }?.binding
                        ?: samplerEntries.getOrNull(index)?.binding
                    if (samplerBinding != null) {
                        add(
                            BindGroupEntry(
                                binding = samplerBinding.toUInt(),
                                resource = ensureSampler(input.sampler),
                            ),
                        )
                    }
                }
            },
        ),
    )

    private fun acquireTransientTarget(
        spec: ImagePassTargetSpec,
        destinationFormat: GPUTextureFormat,
        referenceWidth: Int,
        referenceHeight: Int,
    ): PooledTransientTarget {
        val width = spec.scaledWidth(referenceWidth)
        val height = spec.scaledHeight(referenceHeight)
        val gpuFormat = when (spec.format) {
            ImagePassTargetFormat.DestinationMatch -> destinationFormat
            ImagePassTargetFormat.Rgba8Unorm -> GPUTextureFormat.RGBA8Unorm
            ImagePassTargetFormat.Rgba16Float -> GPUTextureFormat.RGBA16Float
        }
        val existing = transientTargets[spec.id]
        if (existing != null) {
            if (existing.width == width && existing.height == height && existing.format == gpuFormat) {
                totalTargetReuses += 1
                return existing
            }
            existing.destroy()
        }
        totalTargetAllocations += 1
        val created = createTransientTarget(width, height, gpuFormat)
        transientTargets[spec.id] = created
        return created
    }

    private fun ensureSnapshotTarget(
        width: Int,
        height: Int,
        format: GPUTextureFormat,
    ): PooledTransientTarget {
        val existing = snapshotTarget
        if (existing != null) {
            if (existing.width == width && existing.height == height && existing.format == format) {
                return existing
            }
            existing.destroy()
        }
        val created = createTransientTarget(width, height, format)
        snapshotTarget = created
        return created
    }

    private fun createTransientTarget(
        width: Int,
        height: Int,
        format: GPUTextureFormat,
    ): PooledTransientTarget {
        val texture = device.createTexture(
            TextureDescriptor(
                size = Extent3D(width = width.toUInt(), height = height.toUInt()),
                format = format,
                usage = GPUTextureUsage.RenderAttachment or
                    GPUTextureUsage.TextureBinding or
                    GPUTextureUsage.CopySrc or
                    GPUTextureUsage.CopyDst,
                dimension = GPUTextureDimension.TwoD,
            ),
        )
        return PooledTransientTarget(
            width = width,
            height = height,
            format = format,
            texture = texture,
            view = texture.createView(TextureViewDescriptor()),
        )
    }

    private fun ensureSampler(spec: ImagePassSamplerSpec): GPUSampler = samplers.getOrPut(spec) {
        val filter = when (spec.filter) {
            ImagePassFilterMode.Nearest -> GPUFilterMode.Nearest
            ImagePassFilterMode.Linear -> GPUFilterMode.Linear
        }
        val addressMode = when (spec.addressMode) {
            ImagePassAddressMode.ClampToEdge -> GPUAddressMode.ClampToEdge
            ImagePassAddressMode.Repeat -> GPUAddressMode.Repeat
        }
        device.createSampler(
            SamplerDescriptor(
                addressModeU = addressMode,
                addressModeV = addressMode,
                addressModeW = addressMode,
                magFilter = filter,
                minFilter = filter,
                mipmapFilter = GPUMipmapFilterMode.Nearest,
            ),
        )
    }

    private fun ensureCompiledShader(spec: ImagePassShaderSpec): CompiledShader =
        compiledShaders.getOrPut(spec.id) {
            val module = device.createShaderModule(ShaderModuleDescriptor(code = spec.wgslSource))
            val uniformByteSize = ((spec.uniformLayout?.total ?: 0) * Float.SIZE_BYTES).toLong()
            val uniformBinding = spec.groupBindings.entries
                .firstOrNull { it.kind == ResourceKind.UniformBuffer }
                ?.binding
            CompiledShader(
                spec = spec,
                shaderModule = module,
                uniformByteSize = uniformByteSize,
                uniformBinding = uniformBinding,
            )
        }

    private fun ensurePipeline(
        compiled: CompiledShader,
        targetFormat: GPUTextureFormat,
        blendEnabled: Boolean,
        blendMode: BlendMode,
        isPremultiplied: Boolean,
    ): GPURenderPipeline {
        val key = PipelineKey(
            shaderId = compiled.spec.id,
            targetFormat = targetFormat,
            blendEnabled = blendEnabled,
            blendMode = blendMode,
            isPremultiplied = isPremultiplied,
        )
        return pipelines.getOrPut(key) {
            buildRenderPipeline(
                compiled = compiled,
                targetFormat = targetFormat,
                blendEnabled = blendEnabled,
                blendMode = blendMode,
                isPremultiplied = isPremultiplied,
            )
        }
    }

    private fun buildRenderPipeline(
        compiled: CompiledShader,
        targetFormat: GPUTextureFormat,
        blendEnabled: Boolean,
        blendMode: BlendMode,
        isPremultiplied: Boolean,
    ): GPURenderPipeline = device.createAwakePipelineLayout(mapOf(0 to compiled.spec.groupBindings)).use { layout ->
        createRenderPipeline(compiled, layout, targetFormat, blendEnabled, blendMode, isPremultiplied)
    }

    @Suppress("LongParameterList")
    private fun createRenderPipeline(
        compiled: CompiledShader,
        layout: GPUPipelineLayout,
        targetFormat: GPUTextureFormat,
        blendEnabled: Boolean,
        blendMode: BlendMode,
        isPremultiplied: Boolean,
    ): GPURenderPipeline = device.createRenderPipeline(
        RenderPipelineDescriptor(
            layout = layout,
            vertex = VertexState(
                module = compiled.shaderModule,
                entryPoint = VERTEX_ENTRY_POINT,
                buffers = buildVertexBufferLayouts(compiled.spec.vertexFormat),
            ),
            fragment = FragmentState(
                module = compiled.shaderModule,
                entryPoint = FRAGMENT_ENTRY_POINT,
                targets = listOf(
                    ColorTargetState(
                        format = targetFormat,
                        blend = if (blendEnabled) createBlendState(blendMode, isPremultiplied) else null,
                    ),
                ),
            ),
            primitive = PrimitiveState(topology = GPUPrimitiveTopology.TriangleList),
        ),
    )

    private fun buildVertexBufferLayouts(format: VertexFormat): List<VertexBufferLayout> {
        if (format.attributes.isEmpty()) return emptyList()
        return listOf(
            VertexBufferLayout(
                arrayStride = format.strideBytes.toULong(),
                attributes = format.entries.map { entry ->
                    VertexAttribute(
                        shaderLocation = entry.attribute.location.toUInt(),
                        offset = entry.offsetBytes.toULong(),
                        format = entry.attribute.format.toGpuVertexFormat(),
                    )
                },
            ),
        )
    }

    private fun createBlendState(blendMode: BlendMode, isPremultiplied: Boolean): BlendState {
        val srcFactor = when (blendMode) {
            BlendMode.SourceOver, BlendMode.Plus ->
                if (isPremultiplied) GPUBlendFactor.One else GPUBlendFactor.SrcAlpha
            BlendMode.Screen, BlendMode.Overlay -> GPUBlendFactor.One
        }
        val dstFactor = when (blendMode) {
            BlendMode.SourceOver -> GPUBlendFactor.OneMinusSrcAlpha
            BlendMode.Plus, BlendMode.Screen, BlendMode.Overlay -> GPUBlendFactor.One
        }
        return BlendState(
            color = BlendComponent(srcFactor = srcFactor, dstFactor = dstFactor),
            alpha = BlendComponent(srcFactor = GPUBlendFactor.One, dstFactor = dstFactor),
        )
    }

    private fun acquireStepSlot(compiled: CompiledShader): StepUniformSlot {
        val slots = stepSlotsByShader.getOrPut(compiled.spec.id) { mutableListOf() }
        val slotIndex = stepSlotsUsed[compiled.spec.id] ?: 0
        stepSlotsUsed[compiled.spec.id] = slotIndex + 1
        while (slots.size <= slotIndex) {
            val buffer = if (compiled.uniformByteSize > 0L && compiled.uniformBinding != null) {
                device.createBuffer(
                    BufferDescriptor(
                        size = compiled.uniformByteSize.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    ),
                )
            } else {
                null
            }
            slots.add(StepUniformSlot(buffer))
        }
        return slots[slotIndex]
    }

    private class PooledTransientTarget(
        val width: Int,
        val height: Int,
        val format: GPUTextureFormat,
        val texture: GPUTexture,
        val view: GPUTextureView,
        var initialized: Boolean = false,
    ) {
        fun destroy() {
            view.close()
            texture.close()
        }
    }

    private class CompiledShader(
        val spec: ImagePassShaderSpec,
        val shaderModule: GPUShaderModule,
        val uniformByteSize: Long,
        val uniformBinding: Int?,
    ) {
        fun destroy() {
            shaderModule.close()
        }
    }

    private data class PipelineKey(
        val shaderId: String,
        val targetFormat: GPUTextureFormat,
        val blendEnabled: Boolean,
        val blendMode: BlendMode,
        val isPremultiplied: Boolean,
    )

    private class StepUniformSlot(
        val uniformBuffer: GPUBuffer?,
    ) {
        fun destroy() {
            uniformBuffer?.close()
        }
    }

    private companion object {
        const val VERTEX_ENTRY_POINT = "vertexMain"
        const val FRAGMENT_ENTRY_POINT = "fragmentMain"
        const val USHORT_MASK = 0xFFFF

        val DEFAULT_FULLSCREEN_QUAD_VERTICES = floatArrayOf(
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
            1f, -1f, 1f, 1f,
            -1f, -1f, 0f, 1f,
        )
        val DEFAULT_FULLSCREEN_QUAD_INDICES = intArrayOf(0, 1, 2, 0, 2, 3)
    }
}
