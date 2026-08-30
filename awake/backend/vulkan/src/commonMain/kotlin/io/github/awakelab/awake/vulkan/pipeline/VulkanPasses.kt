/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.vulkan.pipeline

import io.github.awakelab.awake.core.graphics2d.BlendMode
import io.github.awakelab.awake.core.graphics2d.TextureCompositeMode
import io.github.awakelab.awake.render.command.BufferHandle
import io.github.awakelab.awake.render.command.MaterialBinding
import io.github.awakelab.awake.render.command.PipelineHandle
import io.github.awakelab.awake.render.command.PreparedDraw
import io.github.awakelab.awake.render.passes.LinePass
import io.github.awakelab.awake.render.passes2d.TexturedPrimitiveRun
import io.github.awakelab.awake.render.passes2d.UiPass
import io.github.awakelab.awake.render.passes2d.UiPipelineKind
import io.github.awakelab.awake.render.passes2d.UiRun
import io.github.awakelab.awake.render.passes2d.UiRunRecorder
import io.github.awakelab.awake.vulkan.debug.LineMesh
import io.github.awakelab.awake.vulkan.debug.LineRenderPipeline
import io.github.awakelab.awake.vulkan.material.Material
import io.github.awakelab.awake.vulkan.ui.DynamicMesh

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
        return VulkanUiRunRecorder(context, pipelines)
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

/** One frame's Vulkan translation of [UiRunRecorder]; cheap enough to build per UI pass. */
private class VulkanUiRunRecorder(
    private val context: VulkanRenderFrameContext,
    private val pipelines: UiPipelineSet,
) : UiRunRecorder<DynamicMesh> {

    override fun bindPipeline(kind: UiPipelineKind): Boolean {
        val pipeline = when (kind) {
            UiPipelineKind.Quad -> pipelines.quad
            UiPipelineKind.RoundedQuad -> pipelines.roundedQuad
            UiPipelineKind.Glyph -> pipelines.glyph
            UiPipelineKind.Texture -> pipelines.textures[TextureCompositeMode(BlendMode.SourceOver, false)]
        }
        // Texture binds nothing up front: bindMaterial rebinds the pipeline per primitive,
        // since each one also needs its own descriptor set bound at the same point.
        if (pipeline != null && kind != UiPipelineKind.Texture) pipeline.bind(context.commandBuffer)
        return pipeline != null
    }

    override fun drawMesh(mesh: DynamicMesh) {
        mesh.bind(context.frameIndex, context.commandBuffer)
        mesh.draw(context.frameIndex, context.commandBuffer)
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) =
        context.recorder.setScissor(x, y, width, height)

    override fun drawTexturePrimitive(primitive: TexturedPrimitiveRun, slotIndex: Int) {
        val pipeline = pipelines.textures[primitive.compositeMode] ?: return
        val material = primitive.texture as Material
        val mesh = context.textureMeshForPrimitive(slotIndex)
        pipeline.bindMaterial(
            context.commandBuffer,
            context.frameIndex,
            slotIndex,
            material.samplerHandle,
            material.imageViewHandle,
        )
        mesh.update(context.frameIndex, primitive.vertices, primitive.indices)
        mesh.bind(context.frameIndex, context.commandBuffer)
        mesh.draw(context.frameIndex, context.commandBuffer)
    }
}
