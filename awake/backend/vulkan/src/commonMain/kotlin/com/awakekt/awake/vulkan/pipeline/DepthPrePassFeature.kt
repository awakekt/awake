/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.passes.uniforms.SHADOW_CASCADE_PASS_GROUP
import com.awakekt.awake.render.pipeline.BindingLayout
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.gen.VulkanBuffers
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
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
 */
internal class DepthPrePassFeature(
    /** Also read by `Renderer`, which binds this target's own descriptor set once per scene
     * pass so shaders can sample the depth this pass wrote. */
    internal val depthTarget: DepthTarget,
    private val depthOnlyPipeline: DepthOnlyPipeline,
    private val variantPipelines: Map<DepthCasterKind, DepthOnlyPipeline> = emptyMap(),
    private val formatPipelines: Map<VertexFormat, DepthOnlyPipeline> = emptyMap(),
    private val keyedVariantPipelines: Map<DepthRenderKey, DepthOnlyPipeline> = emptyMap(),
) {
    internal fun pipelineFor(kind: DepthCasterKind, format: VertexFormat): DepthOnlyPipeline? {
        val pipeline = if (kind == DepthCasterKind.Ordinary) {
            formatPipelines[format] ?: depthOnlyPipeline
        } else {
            variantPipelines[kind]
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
        drawCalls: List<PreparedDraw>,
        castFormat: VertexFormat,
        cascades: GpuShadowCascadeData,
    ) = recordCommands(commandBuffer, drawCalls, castFormat, cascades.viewProjections)

    /** Records the generic packet's arbitrary layered depth resource. */
    fun recordCommands(
        commandBuffer: Long,
        drawCalls: List<PreparedDraw>,
        castFormat: VertexFormat,
        viewProjections: List<Mat4>,
    ) {
        require(viewProjections.isNotEmpty()) { "A layered depth pass needs at least one matrix." }
        // EVERY layer, not just the ones this frame's cascade set fills. A layer that is never
        // rendered is never written, and sampling the array then reads an image subresource in an
        // undefined layout -- which the validation layer rejects and a driver may render as
        // anything. A configuration with fewer cascades than layers repeats its last one, so the
        // extra passes are duplicates rather than holes.
        val allPipelines = buildList {
            add(depthOnlyPipeline)
            addAll(variantPipelines.values)
            addAll(keyedVariantPipelines.values)
            addAll(formatPipelines.values)
        }.distinct()
        for (cascade in 0 until depthTarget.layers) {
            val source = viewProjections[minOf(cascade, viewProjections.lastIndex)]
            allPipelines.forEach { it.writeCascade(cascade, source) }
            recordCascade(commandBuffer, drawCalls, castFormat, cascade)
        }
    }

    /** Missing layers are still cleared, keeping every sampled subresource initialized without
     * making this backend interpret why a layer was requested. */
    fun recordCommands(
        commandBuffer: Long,
        subPasses: List<GpuSubPass>,
        castFormat: VertexFormat,
    ) {
        if (subPasses.isEmpty()) return
        val byLayer = subPasses.associateBy { it.targetLayer }
        val allPipelines = buildList {
            add(depthOnlyPipeline)
            addAll(variantPipelines.values)
            addAll(keyedVariantPipelines.values)
            addAll(formatPipelines.values)
        }.distinct()
        for (layer in 0 until depthTarget.layers) {
            val subPass = byLayer[layer]
            val source = subPass?.viewProjection ?: Mat4()
            allPipelines.forEach { it.writeCascade(layer, source) }
            recordCascade(commandBuffer, subPass?.resolvedDraws.orEmpty(), castFormat, layer)
        }
    }

    private fun recordCascade(
        commandBuffer: Long,
        drawCalls: List<PreparedDraw>,
        castFormat: VertexFormat,
        cascade: Int,
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
            val kind = when {
                prepared.instances > 1 && prepared.jointPaletteBinding != null ->
                    DepthCasterKind.SkinnedInstanced
                prepared.instances > 1 && prepared.instanceColorBuffer != null ->
                    DepthCasterKind.Particle
                prepared.instances > 1 -> DepthCasterKind.Instanced
                prepared.vertexFormat == VertexFormat.PositionNormalColorSkin ->
                    DepthCasterKind.Skinned
                else -> DepthCasterKind.Ordinary
            }
            val pipeline = prepared.vertexFormat?.let { pipelineFor(kind, it) }
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
                            pipeline.cascadeBinding(cascade),
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
                if (indexBuffer != null) {
                    VulkanBuffers.vkCmdBindIndexBuffer(commandBuffer, indexBuffer.handle, 0, com.awakekt.awake.vulkan.models.info.VkIndexType.VK_INDEX_TYPE_UINT32)
                    VulkanBuffers.vkCmdDrawIndexed(commandBuffer, prepared.elementCount, prepared.instances, 0, 0, 0)
                } else {
                    Vulkan.vkCmdDraw(commandBuffer, prepared.elementCount, prepared.instances, 0, 0)
                }
            }
            drawIndex += 1
        }
        Vulkan.vkCmdEndRenderPass(commandBuffer)
    }

    fun destroy() {
        buildList {
            add(depthOnlyPipeline)
            addAll(variantPipelines.values)
        }.distinct().forEach { it.destroy() }
        depthTarget.destroy()
    }
}
