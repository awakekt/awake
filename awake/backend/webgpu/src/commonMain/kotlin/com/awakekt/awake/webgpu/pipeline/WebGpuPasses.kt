/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.render.command.BufferHandle
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.passes.LinePass
import com.awakekt.awake.render.passes2d.UiPass
import com.awakekt.awake.render.passes2d.UiRun
import com.awakekt.awake.render.passes2d.UiRunRecorder
import com.awakekt.awake.webgpu.debug.LineMesh
import com.awakekt.awake.webgpu.debug.LineRenderPipeline
import com.awakekt.awake.webgpu.ui.DynamicMesh

/**
 * WebGPU's implementations of the three scene-pass ports, mirroring the Vulkan backend's. Each is
 * a plain translation into this backend's own types -- the features themselves live once in
 * `render:passes`.
 *
 * Every port method takes a `frameIndex` this backend ignores: its GPU resources are
 * single-buffered, where Vulkan's are per-frame-in-flight. That asymmetry is the whole reason
 * the ports exist rather than two copies of each feature.
 */

/** WebGPU's [LinePass]. The mesh is per-frame `Renderer` state, so it comes from the context. */
internal class WebGpuLinePass(
    private val pipeline: LineRenderPipeline,
) : LinePass<WebGpuRenderFrameContext> {
    override fun writeMvp(frameIndex: Int, mvp: FloatArray) = pipeline.writeMvp(mvp)

    override fun lineDraw(context: WebGpuRenderFrameContext): PreparedDraw =
        LineDraw(pipeline, context.lineMesh)

    override fun destroy() = Unit
}

/** WebGPU's [UiPass]. */
internal class WebGpuUiPass : UiPass<DynamicMesh, WebGpuRenderFrameContext> {
    override fun runs(context: WebGpuRenderFrameContext): List<UiRun<DynamicMesh>> = context.uiRuns

    override fun recorder(context: WebGpuRenderFrameContext): UiRunRecorder<DynamicMesh>? {
        val pipelines = context.uiPipelines() ?: return null
        return WebGpuUiRunRecorder(
            encoder = context.encoder,
            quad = pipelines.quad,
            roundedQuad = pipelines.roundedQuad,
            glyph = pipelines.glyph,
            textures = pipelines.textures,
            textureMeshForPrimitive = context::textureMeshForPrimitive,
        )
    }
}

/** This frame's staged debug lines as one non-indexed [PreparedDraw]. No index buffer at all:
 * consecutive vertex pairs are the segments under `LineList`, so [elementCount] is a vertex
 * count, and 0 vertices means the shared feature skips the whole thing (bind included). */
private class LineDraw(
    private val linePipeline: LineRenderPipeline,
    private val lineMesh: LineMesh,
) : PreparedDraw {
    override val pipeline: PipelineHandle get() = linePipeline.handle
    override val materialBinding: MaterialBinding get() = linePipeline.bindGroupHandle
    override val vertexBuffer: BufferHandle get() = lineMesh.vertexBinding
    override val indexBuffer: BufferHandle? get() = null
    override val elementCount: Int get() = lineMesh.vertexCount
}
