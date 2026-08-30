/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.pipeline

import io.github.awakelab.awake.core.geometry.VertexFormat
import io.github.awakelab.awake.vulkan.Vulkan
import io.github.awakelab.awake.vulkan.enums.VkSubpassContents
import io.github.awakelab.awake.vulkan.mesh.Mesh
import io.github.awakelab.awake.vulkan.models.VkExtent2D
import io.github.awakelab.awake.vulkan.models.VkRect2D
import io.github.awakelab.awake.vulkan.models.VkViewport
import io.github.awakelab.awake.vulkan.models.info.VkRenderPassBeginInfo
import io.github.awakelab.awake.vulkan.renderer.PreparedDrawCall
import io.github.awakelab.awake.vulkan.renderer.Renderer
import io.github.awakelab.awake.vulkan.texture.DepthTarget

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
    /** [commandBuffer] is the caller's already-begun one-time buffer (`Renderer` owns that
     * runner); [castFormat] is the one vertex format this pipeline can bind. */
    fun recordCommands(
        commandBuffer: Long,
        drawCalls: List<PreparedDrawCall>,
        castFormat: VertexFormat,
    ) {
        val renderPassInfo = VkRenderPassBeginInfo(
            renderPass = depthTarget.renderPass,
            framebuffer = depthTarget.framebuffer,
            renderArea = VkRect2D(extent = VkExtent2D(depthTarget.size, depthTarget.size)),
            pClearValues = arrayOf(Renderer.clearDepthValue),
        )
        Vulkan.vkCmdBeginRenderPass(
            commandBuffer,
            renderPassInfo,
            VkSubpassContents.VK_SUBPASS_CONTENTS_INLINE,
        )
        depthOnlyPipeline.bind(commandBuffer)
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
                // Safe for the same reason RendererDraw3D's own `drawCall.mesh as Mesh` is: a
                // Renderer only ever draws meshes it created itself. bind/draw live on the
                // concrete Mesh, not the shared interface -- they take a VkCommandBuffer.
                (prepared.drawCall.mesh as Mesh).bind(commandBuffer)
                prepared.material.bind(
                    commandBuffer,
                    depthOnlyPipeline.pipelineLayout,
                    prepared.frameIndex,
                    prepared.uniformSlotIndex,
                )
                (prepared.drawCall.mesh as Mesh).draw(commandBuffer)
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
