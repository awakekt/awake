/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.renderer.SHADOW_CASCADE_PASS_GROUP
import com.awakekt.awake.render.renderer.ShadowCascadeUniforms
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.enums.VkSubpassContents
import com.awakekt.awake.vulkan.gen.VulkanDescriptors
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.models.VkViewport
import com.awakekt.awake.vulkan.models.info.VkRenderPassBeginInfo
import com.awakekt.awake.vulkan.renderer.PreparedDrawCall
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
) {
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
        drawCalls: List<PreparedDrawCall>,
        castFormat: VertexFormat,
        cascades: ShadowCascadeUniforms,
    ) {
        // EVERY layer, not just the ones this frame's cascade set fills. A layer that is never
        // rendered is never written, and sampling the array then reads an image subresource in an
        // undefined layout -- which the validation layer rejects and a driver may render as
        // anything. A configuration with fewer cascades than layers repeats its last one, so the
        // extra passes are duplicates rather than holes.
        for (cascade in 0 until depthTarget.layers) {
            val source = cascades.viewProjections[minOf(cascade, cascades.count - 1)]
            depthOnlyPipeline.writeCascade(cascade, source)
            recordCascade(commandBuffer, drawCalls, castFormat, cascade)
        }
    }

    private fun recordCascade(
        commandBuffer: Long,
        drawCalls: List<PreparedDrawCall>,
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
        depthOnlyPipeline.bind(commandBuffer)
        // Set 1, once per cascade rather than per draw: every mesh in this pass renders through
        // the same matrix, and only the pass knows which cascade it is.
        if (depthOnlyPipeline.hasCascadeBlock) {
            VulkanDescriptors.vkCmdBindDescriptorSet(
                commandBuffer,
                depthOnlyPipeline.pipelineLayout,
                SHADOW_CASCADE_PASS_GROUP,
                depthOnlyPipeline.cascadeBinding(cascade),
            )
        }
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
        var drawIndex = 0
        while (drawIndex < drawCalls.size) {
            val prepared = drawCalls[drawIndex]
            if (prepared.pipeline.vertexFormat == castFormat) {
                // Safe for the same reason RendererDraw3D's own `mesh as Mesh` is: a
                // Renderer only ever draws meshes it created itself. bind/draw live on the
                // concrete Mesh, not the shared interface -- they take a VkCommandBuffer.
                prepared.mesh.bind(commandBuffer)
                prepared.material.bind(
                    commandBuffer,
                    depthOnlyPipeline.pipelineLayout,
                    prepared.frameIndex,
                    prepared.uniformSlotIndex,
                )
                prepared.mesh.draw(commandBuffer)
            }
            drawIndex += 1
        }
        Vulkan.vkCmdEndRenderPass(commandBuffer)
    }

    fun destroy() {
        depthOnlyPipeline.destroy()
        depthTarget.destroy()
    }
}
