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
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.enums.flags.VkAccessFlagBits
import com.awakekt.awake.vulkan.enums.flags.VkPipelineStageFlagBits
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.gen.VulkanImages
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.renderer.Renderer
import com.awakekt.awake.vulkan.texture.DepthTarget

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

    /** A content feature's depth pipeline and what hands it a draw each frame. Its pipeline is
     * the caller's to destroy, once removed from [contentCasters]. */
    internal class ContentCaster(val pipeline: DepthOnlyPipeline, val source: ContentDepthSource)

    /** Drawn in every rendered sub-pass, after the frame's draws. */
    val contentCasters = ArrayList<ContentCaster>()

    /** Every pipeline the cascade matrices are written to, content casters' included. */
    private val cascadePipelines: List<DepthOnlyPipeline>
        get() = buildList {
            add(depthOnlyPipeline)
            addAll(variantPipelines.values)
            addAll(keyedVariantPipelines.values)
            addAll(formatPipelines.values)
            addAll(instancedFormatPipelines.values)
            contentCasters.forEach { add(it.pipeline) }
        }.distinct()

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
    ) = recordCommands(commandBuffer, frameIndex, drawCalls, castFormat, cascades.viewProjections)

    private val initializedLayers = BooleanArray(depthTarget.layers)

    /** Records the generic packet's arbitrary layered depth resource. */
    fun recordCommands(
        commandBuffer: Long,
        frameIndex: Int,
        drawCalls: List<PreparedDraw>,
        castFormat: VertexFormat,
        viewProjections: List<Mat4>,
    ) {
        require(viewProjections.isNotEmpty()) { "A layered depth pass needs at least one matrix." }
        // Each rendered cascade writes and transitions its layer to SHADER_READ_ONLY_OPTIMAL.
        // Unrendered layers are transitioned once with an empty pass to satisfy Vulkan layout
        // invariants without incurring per-frame render pass overhead.
        val allPipelines = cascadePipelines
        val activeCount = minOf(viewProjections.size, depthTarget.layers)
        for (cascade in 0 until activeCount) {
            val source = viewProjections[cascade]
            allPipelines.forEach { it.writeCascade(frameIndex, cascade, source) }
            recordCascade(commandBuffer, frameIndex, drawCalls, castFormat, cascade)
            initializedLayers[cascade] = true
        }
        initializeLayers(commandBuffer, castFormat)
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
        for (subPass in subPasses) {
            val layer = subPass.targetLayer
            if (layer in 0 until depthTarget.layers) {
                allPipelines.forEach { it.writeCascade(frameIndex, layer, subPass.viewProjection) }
                recordCascade(commandBuffer, frameIndex, subPass.resolvedDraws, castFormat, layer, environment)
                initializedLayers[layer] = true
            }
        }
        val activeLayers = subPasses.map { it.targetLayer }.toSet()
        for (cascade in 0 until minOf(4, depthTarget.layers)) {
            if (cascade !in activeLayers) {
                allPipelines.forEach { it.writeCascade(frameIndex, cascade, Mat4()) }
                recordCascade(commandBuffer, frameIndex, emptyList(), castFormat, cascade)
                initializedLayers[cascade] = true
            }
        }
        initializeLayers(commandBuffer, castFormat)
    }

    /**
     * Moves every layer no pass has rendered yet out of `UNDEFINED`, once, with an empty pass.
     *
     * Also called on frames that render no depth at all: the scene pass binds the whole array
     * whether or not shadows ran, and sampling an `UNDEFINED` layer is invalid.
     */
    fun initializeLayers(commandBuffer: Long, castFormat: VertexFormat) {
        for (layer in 0 until depthTarget.layers) {
            if (!initializedLayers[layer]) {
                // No draws, so no cascade set is bound and any frame's slot would do.
                recordCascade(commandBuffer, 0, emptyList(), castFormat, layer)
                initializedLayers[layer] = true
            }
        }
    }

    private fun recordCascade(
        commandBuffer: Long,
        frameIndex: Int,
        drawCalls: List<PreparedDraw>,
        castFormat: VertexFormat,
        cascade: Int,
        content: GpuEnvironmentState? = null,
    ) {
        val renderPassInfo = VkRenderPassBeginInfo(
            renderPass = depthTarget.renderPass,
            framebuffer = depthTarget.framebufferFor(cascade),
            renderArea = VkRect2D(extent = VkExtent2D(depthTarget.size, depthTarget.size)),
            pClearValues = arrayOf(Renderer.clearDepthValue),
        )
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            renderPassInfo,
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        val size = depthTarget.size.toFloat()
        Vulkan.vkCmdSetViewport(
            commandBuffer,
            0,
            arrayOf(VkViewport(width = size, height = size)),
        )
        Vulkan.vkCmdSetScissor(
            commandBuffer,
            0,
            arrayOf(VkRect2D(extent = VkExtent2D(depthTarget.size, depthTarget.size))),
        )
        var boundPipeline: DepthOnlyPipeline? = null
        var drawIndex = 0
        while (drawIndex < drawCalls.size) {
            val prepared = drawCalls[drawIndex]
            // An instance buffer, not a count above one, makes a draw instanced: a lone copy still
            // takes its model from the buffer.
            val instanced = prepared.instanceVertexBuffer != null
            val kind = when {
                instanced && prepared.jointPaletteBinding != null ->
                    DepthCasterKind.SkinnedInstanced
                instanced && prepared.instanceColorBuffer != null ->
                    DepthCasterKind.Particle
                instanced -> DepthCasterKind.Instanced
                prepared.vertexFormat?.isSkinned == true -> DepthCasterKind.Skinned
                else -> DepthCasterKind.Ordinary
            }
            // Keyed by coverage too: a masked caster draws through its cut-out shader, or not at all.
            val alphaMode = prepared.depthRenderKey?.alphaMode ?: AlphaMode.Opaque
            val pipeline = prepared.vertexFormat?.let { pipelineFor(DepthRenderKey(kind, alphaMode), it) }
            val vertexBuffer = prepared.vertexBuffer as? VulkanBufferBinding
            val indexBuffer = prepared.indexBuffer as? VulkanBufferBinding
            val depthMaterial = prepared.depthMaterialBinding ?: prepared.materialBinding
            val paletteBinding = prepared.depthJointPaletteBinding ?: prepared.jointPaletteBinding
            val instanceBuffer = prepared.instanceVertexBuffer as? VulkanBufferBinding
            if (pipeline != null && vertexBuffer != null && depthMaterial != null) {
                if (boundPipeline !== pipeline) {
                    pipeline.bind(commandBuffer)
                    if (pipeline.hasCascadeBlock) {
                        VulkanDescriptors.vkCmdBindDescriptorSet(
                            commandBuffer,
                            pipeline.pipelineLayout,
                            SHADOW_CASCADE_PASS_GROUP,
                            pipeline.cascadeBinding(frameIndex, cascade),
                        )
                    }
                    boundPipeline = pipeline
                }
                VulkanBuffers.vkCmdBindVertexBuffers(
                    commandBuffer,
                    0,
                    vertexBuffer.asArray,
                    longArrayOf(0L),
                )
                if (kind != DepthCasterKind.Ordinary && instanceBuffer != null) {
                    VulkanBuffers.vkCmdBindVertexBuffers(commandBuffer, 1, instanceBuffer.asArray, longArrayOf(0L))
                    (prepared.instanceColorBuffer as? VulkanBufferBinding)?.let {
                        VulkanBuffers.vkCmdBindVertexBuffers(commandBuffer, 2, it.asArray, longArrayOf(0L))
                    }
                    (prepared.instanceFrameBuffer as? VulkanBufferBinding)?.let {
                        VulkanBuffers.vkCmdBindVertexBuffers(commandBuffer, 3, it.asArray, longArrayOf(0L))
                    }
                }
                VulkanDescriptors.vkCmdBindDescriptorSet(
                    commandBuffer,
                    pipeline.pipelineLayout,
                    BindingLayout.Standard.slot(com.awakekt.awake.render.pipeline.BindingSemantic.Material),
                    (depthMaterial as VulkanMaterialBinding).descriptorSetHandle,
                )
                if (kind == DepthCasterKind.SkinnedInstanced && paletteBinding != null) {
                    VulkanDescriptors.vkCmdBindDescriptorSet(
                        commandBuffer,
                        pipeline.pipelineLayout,
                        BindingLayout.Standard.slot(com.awakekt.awake.render.pipeline.BindingSemantic.JointPalette),
                        (paletteBinding as VulkanMaterialBinding).descriptorSetHandle,
                    )
                }
                issueDraw(commandBuffer, prepared, indexBuffer)
            }
            drawIndex += 1
        }
        if (content != null) recordContentCasters(commandBuffer, frameIndex, cascade, content)
        Vulkan.vkCmdEndRenderPass(commandBuffer)
        // The render pass's outgoing dependency already states this ordering, but MoltenVK up to
        // 1.4.1 never encodes subpass dependencies: without an explicit barrier the scene pass can
        // sample this layer while it is still being written. One per layer, so each pass waits
        // on the one before and the scene pass waits on all of them.
        VulkanImages.vkCmdMemoryBarrier(
            commandBuffer,
            srcStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT.value,
            srcAccessMask = VkAccessFlagBits.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT.value,
            dstStageMask = VkPipelineStageFlagBits.VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT.value,
            dstAccessMask = VkAccessFlagBits.VK_ACCESS_SHADER_READ_BIT.value,
        )
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
            VulkanBuffers.vkCmdBindVertexBuffers(commandBuffer, 0, vertexBuffer.asArray, longArrayOf(0L))
            issueDraw(commandBuffer, draw, draw.indexBuffer as? VulkanBufferBinding)
        }
    }

    private fun issueDraw(commandBuffer: Long, prepared: PreparedDraw, indexBuffer: VulkanBufferBinding?) {
        if (indexBuffer != null) {
            VulkanBuffers.vkCmdBindIndexBuffer(commandBuffer, indexBuffer.handle, 0, com.awakekt.awake.vulkan.models.info.VkIndexType.VK_INDEX_TYPE_UINT32)
            VulkanBuffers.vkCmdDrawIndexed(commandBuffer, prepared.elementCount, prepared.instances, 0, 0, 0)
        } else {
            Vulkan.vkCmdDraw(commandBuffer, prepared.elementCount, prepared.instances, 0, 0)
        }
        stats?.recordDraw(prepared.elementCount, prepared.instances)
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
