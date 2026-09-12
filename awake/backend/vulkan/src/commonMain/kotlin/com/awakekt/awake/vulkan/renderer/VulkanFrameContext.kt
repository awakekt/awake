/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.CommandRecorder
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.vulkan.pipeline.RenderPipeline
import com.awakekt.awake.vulkan.pipeline.UiPipelineSet
import com.awakekt.awake.vulkan.pipeline.VulkanRenderFrameContext

/**
 * Vulkan-side implementation of [VulkanRenderFrameContext] -- the one class bridging the
 * contract interface to real [Renderer] internals. Every member is a one-line delegation,
 * and this is the only file allowed to reference both sides together.
 * Cheap to allocate once per frame (holds references only, copies nothing).
 *
 * Named [VulkanFrameContext] to mirror [WebGpuFrameContext] on the sibling backend.
 */
internal class VulkanFrameContext(
    private val renderer: Renderer,
    override val commandBuffer: Long,
    override val frameIndex: Int,
    override val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>>,
    override val transparentDrawCalls: List<PreparedDraw> = emptyList(),
    override val primaryPipeline: RenderPipeline,
    override val viewProjection: Mat4,
    override val cameraEye: Vec3f,
    override val environmentState: GpuEnvironmentState = GpuEnvironmentState.Default,
) : VulkanRenderFrameContext {
    override val lineMesh get() = renderer.lineMesh
    override val uiRuns get() = renderer.uiRuns
    override val surfaceWidth get() = renderer.swapchainManager.extent.width
    override val surfaceHeight get() = renderer.swapchainManager.extent.height

    /** Aimed at [commandBuffer] here, once per frame, rather than at every record site. */
    override val recorder: CommandRecorder = renderer.commandRecorder.apply {
        commandBuffer = this@VulkanFrameContext.commandBuffer
    }

    override fun uiPipelines(): UiPipelineSet? {
        val quad = renderer.uiRenderPipeline ?: return null
        return UiPipelineSet(
            quad = quad,
            glyph = renderer.uiGlyphRenderPipeline,
            textures = renderer.uiTextureRenderPipelines,
            roundedQuad = renderer.uiRoundedQuadRenderPipeline,
        )
    }

    override fun textureMeshForPrimitive(index: Int) = renderer.textureMeshForPrimitive(index)
}
