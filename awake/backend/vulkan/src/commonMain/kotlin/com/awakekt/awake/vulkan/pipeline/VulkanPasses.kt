/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.pipeline

import com.awakekt.awake.core.text.font.UiFont
import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.imagepass.ImagePassChain
import com.awakekt.awake.render.passes.LinePass
import com.awakekt.awake.render.passes2d.TexturedPrimitiveRun
import com.awakekt.awake.render.passes2d.UiPass
import com.awakekt.awake.render.passes2d.UiPipelineKind
import com.awakekt.awake.render.passes2d.UiRun
import com.awakekt.awake.render.passes2d.UiRunRecorder
import com.awakekt.awake.render.renderer.RenderStatsCounter
import com.awakekt.awake.vulkan.Vulkan
import com.awakekt.awake.vulkan.debug.LineMesh
import com.awakekt.awake.vulkan.debug.LineRenderPipeline
import com.awakekt.awake.vulkan.material.Material
import com.awakekt.awake.vulkan.models.VkExtent2D
import com.awakekt.awake.vulkan.models.VkOffset2D
import com.awakekt.awake.vulkan.models.VkRect2D
import com.awakekt.awake.vulkan.ui.DynamicMesh

/**
 * Vulkan's implementations of the three scene-pass ports. Each is a plain translation into this
 * backend's own types -- the features themselves live once in `render:passes`.
 *
 * `frameIndex` is real here, unlike on WebGPU: every GPU resource below is per-frame-in-flight.
 */

/** Vulkan's [LinePass]. [lineMesh] is NOT destroyed here -- it stays a `Renderer`-owned
 * per-frame staging buffer. */
internal class VulkanLinePass(
    private val pipeline: LineRenderPipeline,
) : LinePass<VulkanRenderFrameContext> {
    override fun writeMvp(frameIndex: Int, mvp: FloatArray) = pipeline.writeMvp(frameIndex, mvp)

    override fun lineDraw(context: VulkanRenderFrameContext): PreparedDraw =
        LineDraw(pipeline, context.lineMesh, context.frameIndex)

    override fun destroy() = pipeline.destroy()
}

/** Vulkan's [UiPass]. */
internal class VulkanUiPass : UiPass<DynamicMesh, VulkanRenderFrameContext> {
    override fun runs(context: VulkanRenderFrameContext): List<UiRun<DynamicMesh>> = context.uiRuns

    override fun recorder(context: VulkanRenderFrameContext): UiRunRecorder<DynamicMesh>? {
        val pipelines = context.uiPipelines() ?: return null
        return VulkanUiRunRecorder(
            commandBuffer = context.commandBuffer,
            frameIndex = context.frameIndex,
            stats = context.stats,
            pipelines = pipelines,
            textureMeshForPrimitive = context::textureMeshForPrimitive,
            onExecuteImagePass = context::executeImagePass,
        )
    }
}

/** This frame's staged debug lines as one non-indexed [PreparedDraw]. One small object per frame,
 * not per draw call -- every handle it exposes was created with its resource. */
private class LineDraw(
    private val linePipeline: LineRenderPipeline,
    private val lineMesh: LineMesh,
    private val frameIndex: Int,
) : PreparedDraw {
    override val pipeline: PipelineHandle get() = linePipeline
    override val materialBinding: MaterialBinding get() = linePipeline.uniformBinding(frameIndex)
    override val vertexBuffer: BufferHandle get() = lineMesh.binding(frameIndex)

    /** No index buffer at all: consecutive vertex pairs are the line segments under
     * `LINE_LIST`, so [elementCount] is a vertex count. */
    override val indexBuffer: BufferHandle? get() = null
    override val elementCount: Int get() = lineMesh.vertexCount(frameIndex)
}

/**
 * One frame's Vulkan translation of [UiRunRecorder]; cheap enough to build per UI pass. The swapchain
 * UI pass and the offscreen `renderUiToTexture` each build one over their own [pipelines], so both
 * record through the shared feature.
 */
internal class VulkanUiRunRecorder(
    private val commandBuffer: Long,
    private val frameIndex: Int,
    private val stats: RenderStatsCounter,
    private val pipelines: UiPipelineSet,
    private val textureMeshForPrimitive: (Int) -> DynamicMesh,
    private val onExecuteImagePass: ((ImagePassChain, Boolean) -> Unit)? = null,
) : UiRunRecorder<DynamicMesh> {

    override fun bindPipeline(kind: UiPipelineKind): Boolean {
        val pipeline = when (kind) {
            UiPipelineKind.Quad -> pipelines.quad
            UiPipelineKind.RoundedQuad -> pipelines.roundedQuad
            UiPipelineKind.Glyph -> pipelines.glyph
            // A texture binds its exact blend/alpha-convention pipeline in drawTexturePrimitive.
            // Requiring the straight-alpha default here dropped a run holding only premultiplied
            // render-target textures.
            UiPipelineKind.Texture -> pipelines.textures.values.firstOrNull()
        }
        // Texture binds nothing up front: bindMaterial rebinds the pipeline per primitive,
        // since each one also needs its own descriptor set bound at the same point.
        // Glyph binds only the pipeline here: its group 0 is the font's, bound in bindGlyphFont.
        if (pipeline != null && kind != UiPipelineKind.Texture) pipeline.bind(commandBuffer)
        return pipeline != null
    }

    override fun bindGlyphFont(font: UiFont): Boolean {
        val pipeline = pipelines.glyph
        val atlas = pipelines.fontAtlases[font]
        if (pipeline == null || atlas == null) return false
        pipeline.bindFontAtlas(commandBuffer, font, atlas)
        return true
    }

    override fun drawMesh(mesh: DynamicMesh) {
        mesh.bind(frameIndex, commandBuffer)
        mesh.draw(frameIndex, commandBuffer, stats)
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) =
        Vulkan.vkCmdSetScissor(
            commandBuffer,
            0,
            arrayOf(VkRect2D(offset = VkOffset2D(x = x, y = y), extent = VkExtent2D(width = width, height = height))),
        )

    override fun drawTexturePrimitive(primitive: TexturedPrimitiveRun, slotIndex: Int) {
        val pipeline = pipelines.textures[primitive.compositeMode] ?: return
        val material = primitive.texture as Material
        val mesh = textureMeshForPrimitive(slotIndex)
        pipeline.bindMaterial(
            commandBuffer,
            frameIndex,
            slotIndex,
            material.samplerHandle,
            material.imageViewHandle,
        )
        mesh.update(frameIndex, primitive.vertices, primitive.indices)
        mesh.bind(frameIndex, commandBuffer)
        mesh.draw(frameIndex, commandBuffer, stats)
    }

    override fun executeImagePass(chain: ImagePassChain, hasMoreImagePasses: Boolean) {
        onExecuteImagePass?.invoke(chain, hasMoreImagePasses)
    }
}
