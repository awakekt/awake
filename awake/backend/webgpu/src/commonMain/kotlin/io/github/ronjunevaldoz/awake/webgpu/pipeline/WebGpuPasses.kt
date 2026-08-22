// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.webgpu.pipeline

import io.github.ronjunevaldoz.awake.render.command.BufferHandle
import io.github.ronjunevaldoz.awake.render.command.MaterialBinding
import io.github.ronjunevaldoz.awake.render.command.PipelineHandle
import io.github.ronjunevaldoz.awake.render.command.PreparedDraw
import io.github.ronjunevaldoz.awake.render.passes.LinePass
import io.github.ronjunevaldoz.awake.render.passes.SkyboxPass
import io.github.ronjunevaldoz.awake.render.passes2d.UiPass
import io.github.ronjunevaldoz.awake.render.passes2d.UiRun
import io.github.ronjunevaldoz.awake.render.passes2d.UiRunRecorder
import io.github.ronjunevaldoz.awake.webgpu.debug.LineMesh
import io.github.ronjunevaldoz.awake.webgpu.debug.LineRenderPipeline
import io.github.ronjunevaldoz.awake.webgpu.debug.SkyboxRenderPipeline
import io.github.ronjunevaldoz.awake.webgpu.ui.DynamicMesh

/**
 * WebGPU's implementations of the three scene-pass ports, mirroring the Vulkan backend's. Each is
 * a plain translation into this backend's own types -- the features themselves live once in
 * `render:passes`.
 *
 * Every port method takes a `frameIndex` this backend ignores: its GPU resources are
 * single-buffered, where Vulkan's are per-frame-in-flight. That asymmetry is the whole reason
 * the ports exist rather than two copies of each feature.
 */

/** WebGPU's [SkyboxPass]. */
internal class WebGpuSkyboxPass(
    private val skybox: SkyboxRenderPipeline,
) : SkyboxPass {
    override fun writeUniforms(frameIndex: Int, uniforms: FloatArray) = skybox.writeUniforms(uniforms)

    override fun pipeline(frameIndex: Int): PipelineHandle = WebGpuPipelineHandle(skybox.pipeline)

    override fun uniformBinding(frameIndex: Int): MaterialBinding = WebGpuBindGroupHandle(skybox.bindGroup)

    /** Owned by `WebGpuEngine`, which destroys it alongside the other pipelines -- unlike
     * Vulkan's, where the pass took ownership during the feature refactor. */
    override fun destroy() = Unit
}

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
            texture = pipelines.texture,
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
