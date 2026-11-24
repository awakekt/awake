/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.passes.uniforms.SHADOW_CASCADE_PASS_GROUP
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.renderer.RenderPassKind
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.flags.VkAccessFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanCommands
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.texture.DepthTarget
import com.awakekt.awake.vulkan.texture.release

/**
 * Renders the frame's draws a second time, depth only, into [depthTarget].
 *
 * NOT a [RenderFeature]: it begins and ends its own render pass, and runs on its own one-time
 * command buffer before the frame's command buffer is recorded at all, so it shares no signature
 * with the shared-pass features.
 *
 * Knows nothing about *why* a caller wants that depth, or from what viewpoint. The transform is
 * whatever the caller's own vertex shader reads out of the per-draw uniform buffer the scene pass
 * already writes -- this binds each [PreparedDrawCall.material]'s existing descriptor set against
 * [depthOnlyPipeline]'s layout rather than introducing a second per-draw uniform scheme. See that
 * pipeline's doc comment for why the two layouts are binding-compatible.
 *
 * Covers every [PreparedDrawCall] whose resolved pipeline shares [castFormat], compared by
 * FORMAT rather than pipeline identity, so an instanced pipeline over the same vertex layout is
 * included. Skinned-instanced and particle draws are not: [depthOnlyPipeline] is built with ONE
 * fixed vertex layout and cannot correctly bind theirs. Widening that needs a pipeline per
 * format, not a looser check.
 *
 * Content features add draws of their own through [contentCasters]: each brings a pipeline
 * built over its own group 0, written the same cascade matrices and drawn in every rendered
 * sub-pass after the frame's draws.
 */
internal class DepthPrePassFeature(
    /** Also read by `Renderer`, which binds this target's own descriptor set once per scene
     * pass so shaders can sample the depth this pass wrote. */
    internal val depthTarget: DepthTarget,
    private val depthOnlyPipeline: DepthOnlyPipeline,
    private val variantPipelines: Map<DepthCasterKind, DepthOnlyPipeline> = emptyMap(),
    private val formatPipelines: Map<VertexFormat, DepthOnlyPipeline> = emptyMap(),
    private val keyedVariantPipelines: Map<DepthRenderKey, DepthOnlyPipeline> = emptyMap(),
    private val instancedFormatPipelines: Map<VertexFormat, DepthOnlyPipeline> = emptyMap(),
) {
    /** The owning `Renderer`'s counter, set when it takes this feature: a pass is built first. */
    internal var stats: RenderStatsCounter? = null

    /** Whether each pass this records counts as a shadow pass: true for the light's cascades, not the camera's depth. */
    internal var countsShadowPasses = false

    /** Total depth render passes recorded over this feature's lifetime, for pass-count regression tests. */
    internal var passesRecorded: Long = 0L
        private set

    /** A content feature's depth pipeline and what hands it a draw each frame. Its pipeline is
     * the caller's to destroy, once removed from [contentCasters]. */
    internal class ContentCaster(val pipeline: DepthOnlyPipeline, val source: ContentDepthSource)

    /** Drawn in every rendered sub-pass, after the frame's draws. */
    val contentCasters = ArrayList<ContentCaster>()

    private val baseCascadePipelines: List<DepthOnlyPipeline> = buildList {
        add(depthOnlyPipeline)
        addAll(variantPipelines.values)
        addAll(keyedVariantPipelines.values)
        addAll(formatPipelines.values)
        addAll(instancedFormatPipelines.values)
    }.distinct()
    private val cachedCascadePipelines = ArrayList<DepthOnlyPipeline>(baseCascadePipelines)
    private val cachedContentCasterPipelines = ArrayList<DepthOnlyPipeline>()

    /** Every pipeline the cascade matrices are written to, content casters' included. */
    private val cascadePipelines: List<DepthOnlyPipeline>
        get() {
            var matches = contentCasters.size == cachedContentCasterPipelines.size
            var checkIndex = 0
            while (matches && checkIndex < contentCasters.size) {
                matches = contentCasters[checkIndex].pipeline === cachedContentCasterPipelines[checkIndex]
                checkIndex += 1
            }
            if (!matches) {
                cachedCascadePipelines.clear()
                cachedCascadePipelines.addAll(baseCascadePipelines)
                cachedContentCasterPipelines.clear()
                for (i in contentCasters.indices) {
                    val p = contentCasters[i].pipeline
                    cachedContentCasterPipelines.add(p)
                    if (p !in cachedCascadePipelines) cachedCascadePipelines.add(p)
                }
            }
            return cachedCascadePipelines
        }

    internal fun pipelineFor(kind: DepthCasterKind, format: VertexFormat): DepthOnlyPipeline? {
        val pipeline = when (kind) {
            DepthCasterKind.Ordinary -> formatPipelines[format] ?: depthOnlyPipeline
            DepthCasterKind.Instanced -> instancedFormatPipelines[format] ?: variantPipelines[kind]
            // A skinned pipeline's own depth shader first; the plan's variant covers the plain format.
            DepthCasterKind.Skinned -> formatPipelines[format] ?: variantPipelines[kind]
            else -> variantPipelines[kind]
        }
        return pipeline?.takeIf { it.vertexFormat == format }
    }

    internal fun pipelineFor(key: DepthRenderKey, format: VertexFormat): DepthOnlyPipeline? {
        // A masked caster cannot use an opaque fallback: that would write depth for discarded
        // texels and make later passes treat transparent holes as solid geometry. Until the
        // backend has a keyed masked resource, omit this draw from the depth pass safely.
        if (key.alphaMode == com.awakekt.awake.render.pipeline.AlphaMode.Masked) {
            return keyedVariantPipelines[key]?.takeIf { it.vertexFormat == format }
        }
        return (keyedVariantPipelines[key] ?: pipelineFor(key.kind, format))
            ?.takeIf { it.vertexFormat == format }
    }

    /**
     * [commandBuffer] is the caller's already-begun one-time buffer (`Renderer` owns that
     * runner); [castFormat] is the one vertex format this pipeline can bind; [cascades] is this
     * frame's cascade set, one render pass each.
     *
     * A pass per cascade rather than one pass writing every layer: a framebuffer attaches one
     * layer, and rendering them together needs multiview, which is a different feature with its
     * own extension and its own device support question. The geometry is submitted N times, which
     * is what the literature measures as faster than geometry-shader amplification anyway.
     */
    fun recordCommands(
        commandBuffer: Long,
        frameIndex: Int,
        drawCalls: List<PreparedDraw>,
        castFormat: VertexFormat,
        cascades: GpuShadowCascadeData,
        environment: GpuEnvironmentState? = null,
    ) = recordCommands(commandBuffer, frameIndex, drawCalls, castFormat, cascades.viewProjections, environment)

    private var initializedLayers = BooleanArray(depthTarget.layers)
    private var dirtyLayers = BooleanArray(depthTarget.layers)

    /**
     * Holds [layers] at [size] in [depthTarget], or releases it to its 1x1 stand-in when [layers] is
     * 0, and forgets which layers were cleared: the new image's layers all start `UNDEFINED`, and
     * [initializeLayers] clears them before anything samples them. Whether the image changed. The
     * caller has waited for the device and has not begun recording a frame that binds the target
     * (#1688, #1698).
     */
    internal fun resize(layers: Int, width: Int, height: Int = width): Boolean {
        val resident = if (depthTarget.resident) depthTarget.layers else 0
        val sameExtent = width == depthTarget.width && height == depthTarget.height
        if (layers == resident && (layers == 0 || sameExtent)) return false
        if (layers == 0) depthTarget.release() else depthTarget.reallocate(layers, width, height)
        initializedLayers = BooleanArray(depthTarget.layers)
        dirtyLayers = BooleanArray(depthTarget.layers)
        return true
    }

    /** Records the generic packet's arbitrary layered depth resource. */
    fun recordCommands(
        commandBuffer: Long,
        frameIndex: Int,
        drawCalls: List<PreparedDraw>,
        castFormat: VertexFormat,
        viewProjections: List<Mat4>,
        environment: GpuEnvironmentState? = null,
    ) {
        require(viewProjections.isNotEmpty()) { "A layered depth pass needs at least one matrix." }
        // Each rendered cascade writes and transitions its layer to SHADER_READ_ONLY_OPTIMAL.
        // Unrendered layers are transitioned once with an empty pass to satisfy Vulkan layout
        // invariants without incurring per-frame render pass overhead.
        // Hoist the pre- and post-pass memory barriers outside the cascade loop so distinct
        // layers can overlap on the GPU instead of draining the pipeline after every cascade.
        val allPipelines = cascadePipelines
        val activeCount = minOf(viewProjections.size, depthTarget.layers)
        var anyRecorded = false
        for (cascade in 0 until activeCount) {
            if (!anyRecorded) {
                recordPreDepthBarrier(commandBuffer)
                anyRecorded = true
            }
            val source = viewProjections[cascade]
            for (i in allPipelines.indices) allPipelines[i].writeCascade(frameIndex, cascade, source)
            if (countsShadowPasses) stats?.beginShadowPass()
            recordCascade(commandBuffer, frameIndex, drawCalls, castFormat, cascade, environment)
            stats?.countAs(RenderPassKind.Other)
            initializedLayers[cascade] = true
            dirtyLayers[cascade] = true
        }
        val initializedAny = finalizeInactiveLayers(
            commandBuffer = commandBuffer,
            castFormat = castFormat,
            activeLayersMask = 0L,
            clearDirtyLimit = 0,
            preBarrierAlreadyEmitted = anyRecorded,
        )
        if (anyRecorded || initializedAny) recordPostDepthBarrier(commandBuffer)
    }

    /**
     * Records only the requested subpasses, avoiding redundant passes over unused layers. Each one
     * also draws the content casters that [environment] lets draw.
     */
    fun recordCommands(
        commandBuffer: Long,
        frameIndex: Int,
        subPasses: List<GpuSubPass>,
        castFormat: VertexFormat,
        environment: GpuEnvironmentState,
    ) {
        if (subPasses.isEmpty()) return
        val allPipelines = cascadePipelines
        var activeLayersMask = 0L
        var anyRecorded = false
        for (i in subPasses.indices) {
            val subPass = subPasses[i]
            val layer = subPass.targetLayer
            if (layer !in 0 until depthTarget.layers) continue
            val duplicateLayer = layer < Long.SIZE_BITS && (activeLayersMask and (1L shl layer)) != 0L
            if (!anyRecorded || duplicateLayer) {
                recordPreDepthBarrier(commandBuffer)
                anyRecorded = true
            }
            if (layer < Long.SIZE_BITS) activeLayersMask = activeLayersMask or (1L shl layer)
            for (p in allPipelines.indices) allPipelines[p].writeCascade(frameIndex, layer, subPass.viewProjection)
            if (countsShadowPasses) stats?.beginShadowPass()
            recordCascade(commandBuffer, frameIndex, subPass.resolvedDraws, castFormat, layer, environment)
            stats?.countAs(RenderPassKind.Other)
            initializedLayers[layer] = true
            dirtyLayers[layer] = true
        }
        val finalizedAny = finalizeInactiveLayers(
            commandBuffer = commandBuffer,
            castFormat = castFormat,
            activeLayersMask = activeLayersMask,
            clearDirtyLimit = minOf(4, depthTarget.layers),
            preBarrierAlreadyEmitted = anyRecorded,
        )
        if (anyRecorded || finalizedAny) recordPostDepthBarrier(commandBuffer)
    }

    /**
     * Moves every layer no pass has rendered yet out of `UNDEFINED`, once, with an empty pass.
     * When [clearDirty] is true, also clears any layer dirtied by a previous active frame once so
     * subsequent empty frames pay zero render passes and zero barriers.
     */
    fun initializeLayers(commandBuffer: Long, castFormat: VertexFormat, clearDirty: Boolean = false) {
        val clearLimit = if (clearDirty) depthTarget.layers else 0
        if (finalizeInactiveLayers(commandBuffer, castFormat, 0L, clearLimit, preBarrierAlreadyEmitted = false)) {
            recordPostDepthBarrier(commandBuffer)
        }
    }

    // Clears inactive layers that held rendered depth (within clearDirtyLimit) and initializes any
    // layer still in UNDEFINED, reusing the caller's pre-pass barrier when one was already emitted.
    private fun finalizeInactiveLayers(
        commandBuffer: Long,
        castFormat: VertexFormat,
        activeLayersMask: Long,
        clearDirtyLimit: Int,
        preBarrierAlreadyEmitted: Boolean,
    ): Boolean {
        var emitted = preBarrierAlreadyEmitted
        var recordedAny = false
        for (layer in 0 until depthTarget.layers) {
            val renderedThisFrame = layer < Long.SIZE_BITS && (activeLayersMask and (1L shl layer)) != 0L
            val needsClear = !renderedThisFrame && layer < clearDirtyLimit && dirtyLayers[layer]
            if (!initializedLayers[layer] || needsClear) {
                if (!emitted) {
                    recordPreDepthBarrier(commandBuffer)
                    emitted = true
                }
                // No draws, so no cascade set is bound and any frame's slot would do.
                recordCascade(commandBuffer, 0, emptyList(), castFormat, layer)
                initializedLayers[layer] = true
                dirtyLayers[layer] = false
                recordedAny = true
            }
        }
        return recordedAny
    }

    private fun recordCascade(
        commandBuffer: Long,
        frameIndex: Int,
        drawCalls: List<PreparedDraw>,
        castFormat: VertexFormat,
        cascade: Int,
        content: GpuEnvironmentState? = null,
    ) {
        passesRecorded += 1L
        VulkanCommands.vkCmdBeginRenderPassDepth(
            commandBuffer,
            depthTarget.renderPass,
            depthTarget.framebufferFor(cascade),
            depthTarget.width,
            depthTarget.height,
            1f,
        )
        VulkanCommands.vkCmdSetViewportRect(commandBuffer, 0f, 0f, depthTarget.width.toFloat(), depthTarget.height.toFloat(), 0f, 1f)
        VulkanCommands.vkCmdSetScissorRect(commandBuffer, 0, 0, depthTarget.width, depthTarget.height)
        var boundPipeline: DepthOnlyPipeline? = null
        var boundMaterialSet = NO_SET
        var boundVertex0 = NO_SET
        var boundVertex1 = NO_SET
        var boundVertex2 = NO_SET
        var boundVertex3 = NO_SET
        var boundIndex = NO_SET
        var drawIndex = 0
        while (drawIndex < drawCalls.size) {
            val prepared = drawCalls[drawIndex]
            // An instance buffer, not a count above one, makes a draw instanced: a lone copy still
            // takes its model from the buffer.
            val instanced = prepared.instanceVertexBuffer != null
            val kind = when {
                instanced && prepared.jointPaletteBinding != null ->
                    DepthCasterKind.SkinnedInstanced
                // Particles alone carry frames; a tinted prop carries colours too.
                instanced && prepared.instanceFrameBuffer != null ->
                    DepthCasterKind.Particle
                instanced -> DepthCasterKind.Instanced
                prepared.vertexFormat?.isSkinned == true -> DepthCasterKind.Skinned
                else -> DepthCasterKind.Ordinary
            }
            // Keyed by coverage too: a masked caster draws through its cut-out shader, or not at all.
            val alphaMode = prepared.depthRenderKey?.alphaMode ?: AlphaMode.Opaque
            val pipeline = prepared.vertexFormat?.let { pipelineFor(DepthRenderKey.of(kind, alphaMode), it) }
            val vertexBuffer = prepared.vertexBuffer as? VulkanBufferBinding
            val indexBuffer = prepared.indexBuffer as? VulkanBufferBinding
            val depthMaterial = prepared.depthMaterialBinding ?: prepared.materialBinding
            val paletteBinding = prepared.depthJointPaletteBinding ?: prepared.jointPaletteBinding
            val instanceBuffer = prepared.instanceVertexBuffer as? VulkanBufferBinding
            if (pipeline == null || vertexBuffer == null || depthMaterial == null) {
                drawIndex += 1
                continue
            }
            var drawPipeline = 0L
            if (boundPipeline !== pipeline) {
                drawPipeline = pipeline.pipelineHandle
                if (pipeline.hasCascadeBlock) {
                    VulkanDescriptors.vkCmdBindDescriptorSet(
                        commandBuffer,
                        pipeline.pipelineLayout,
                        SHADOW_CASCADE_PASS_GROUP,
                        pipeline.cascadeBinding(frameIndex, cascade),
                    )
                }
                boundPipeline = pipeline
                boundMaterialSet = NO_SET
            }
            val drawVertex0 = if (vertexBuffer.handle != boundVertex0) {
                boundVertex0 = vertexBuffer.handle
                vertexBuffer.handle
            } else {
                0L
            }
            if (kind != DepthCasterKind.Ordinary && instanceBuffer != null) {
                if (instanceBuffer.handle != boundVertex1) {
                    VulkanBuffers.vkCmdBindVertexBuffer(commandBuffer, 1, instanceBuffer.handle, 0L)
                    boundVertex1 = instanceBuffer.handle
                }
                (prepared.instanceColorBuffer as? VulkanBufferBinding)?.let {
                    if (it.handle != boundVertex2) {
                        VulkanBuffers.vkCmdBindVertexBuffer(commandBuffer, 2, it.handle, 0L)
                        boundVertex2 = it.handle
                    }
                }
                (prepared.instanceFrameBuffer as? VulkanBufferBinding)?.let {
                    if (it.handle != boundVertex3) {
                        VulkanBuffers.vkCmdBindVertexBuffer(commandBuffer, 3, it.handle, 0L)
                        boundVertex3 = it.handle
                    }
                }
            }
            // Alike instanced casters share one set (#1555): bound once until the pipeline changes.
            val materialSet = (depthMaterial as VulkanMaterialBinding).descriptorSetHandle
            val drawSet0 = if (materialSet != boundMaterialSet) {
                boundMaterialSet = materialSet
                materialSet
            } else {
                0L
            }
            if (kind == DepthCasterKind.SkinnedInstanced && paletteBinding != null) {
                VulkanDescriptors.vkCmdBindDescriptorSet(
                    commandBuffer,
                    pipeline.pipelineLayout,
                    BindingLayout.Standard.slot(com.awakekt.awake.render.pipeline.BindingSemantic.JointPalette),
                    (paletteBinding as VulkanMaterialBinding).descriptorSetHandle,
                )
            }
            if (indexBuffer != null) {
                val drawIndexBuffer = if (indexBuffer.handle != boundIndex) {
                    boundIndex = indexBuffer.handle
                    indexBuffer.handle
                } else {
                    0L
                }
                VulkanCommands.vkCmdDrawIndexedWithState(
                    commandBuffer = commandBuffer,
                    pipeline = drawPipeline,
                    pipelineLayout = pipeline.pipelineLayout,
                    descriptorSet0 = drawSet0,
                    vertexBuffer0 = drawVertex0,
                    indexBuffer = drawIndexBuffer,
                    indexCount = prepared.elementCount,
                    instanceCount = prepared.instances,
                    firstIndex = 0,
                )
            } else {
                VulkanCommands.vkCmdDrawWithState(
                    commandBuffer = commandBuffer,
                    pipeline = drawPipeline,
                    pipelineLayout = pipeline.pipelineLayout,
                    descriptorSet0 = drawSet0,
                    vertexBuffer0 = drawVertex0,
                    vertexCount = prepared.elementCount,
                    instanceCount = prepared.instances,
                )
            }
            stats?.recordDraw(prepared.elementCount, prepared.instances)
            drawIndex += 1
        }
        if (content != null) recordContentCasters(commandBuffer, frameIndex, cascade, content)
        Vulkan.vkCmdEndRenderPass(commandBuffer)
    }

    /** Each content caster's draw for this frame, through its own pipeline and its own group 0. */
    private fun recordContentCasters(commandBuffer: Long, frameIndex: Int, cascade: Int, environment: GpuEnvironmentState) {
        for (index in contentCasters.indices) {
            val caster = contentCasters[index]
            val draw = caster.source.depthDraw(frameIndex, environment)
            val vertexBuffer = draw?.vertexBuffer as? VulkanBufferBinding
            if (draw == null || vertexBuffer == null) continue
            val pipeline = caster.pipeline
            pipeline.bind(commandBuffer)
            VulkanDescriptors.vkCmdBindDescriptorSet(
                commandBuffer,
                pipeline.pipelineLayout,
                SHADOW_CASCADE_PASS_GROUP,
                pipeline.cascadeBinding(frameIndex, cascade),
            )
            VulkanDescriptors.vkCmdBindDescriptorSet(
                commandBuffer,
                pipeline.pipelineLayout,
                BindingLayout.Standard.slot(com.awakekt.awake.render.pipeline.BindingSemantic.Material),
                (draw.materialBinding as VulkanMaterialBinding).descriptorSetHandle,
            )
            VulkanBuffers.vkCmdBindVertexBuffer(commandBuffer, 0, vertexBuffer.handle, 0L)
            issueDepthDraw(commandBuffer, draw, draw.indexBuffer as? VulkanBufferBinding, stats)
        }
    }

    fun destroy() {
        buildList {
            add(depthOnlyPipeline)
            addAll(variantPipelines.values)
            addAll(formatPipelines.values)
            addAll(keyedVariantPipelines.values)
            addAll(instancedFormatPipelines.values)
        }.distinct().forEach { it.destroy() }
        depthTarget.destroy()
    }
}

private fun issueDepthDraw(
    commandBuffer: Long,
    prepared: PreparedDraw,
    indexBuffer: VulkanBufferBinding?,
    stats: RenderStatsCounter?,
) {
    if (indexBuffer != null) {
        VulkanBuffers.vkCmdBindIndexBuffer(
            commandBuffer,
            indexBuffer.handle,
            0,
            com.awakekt.awake.vulkan.models.info.VkIndexType.VK_INDEX_TYPE_UINT32,
        )
        VulkanBuffers.vkCmdDrawIndexed(commandBuffer, prepared.elementCount, prepared.instances, 0, 0, 0)
    } else {
        Vulkan.vkCmdDraw(commandBuffer, prepared.elementCount, prepared.instances, 0, 0)
    }
    stats?.recordDraw(prepared.elementCount, prepared.instances)
}

private fun recordPreDepthBarrier(commandBuffer: Long) {
    // The render pass's incoming dependency states this too, and MoltenVK drops it like the
    // outgoing one below. Without it, this pass can overwrite a layer while an earlier scene
    // pass still samples it: the previous frame's, or the main frame's when an offscreen render
    // (a canvas View, a readback) reuses the target.
    VulkanImages.vkCmdMemoryBarrier(
        commandBuffer,
        srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value or
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value or
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT.value,
        srcAccessMask = VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value,
        dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT.value or
            VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT.value,
        dstAccessMask = VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT.value or
            VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value,
    )
}

private fun recordPostDepthBarrier(commandBuffer: Long) {
    // The render pass's outgoing dependency already states this ordering, but MoltenVK up to
    // 1.4.1 never encodes subpass dependencies: without an explicit barrier the scene pass can
    // sample these layers while they are still being written. Emitted once after all cascades
    // finish so the GPU can overlap independent layers while the scene pass waits on all of them.
    VulkanImages.vkCmdMemoryBarrier(
        commandBuffer,
        srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT.value,
        srcAccessMask = VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value,
        dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value,
        dstAccessMask = VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value,
    )
}

/** No material set bound yet in this pass, or since its last pipeline bind: no real handle is 0. */
private const val NO_SET = 0L
