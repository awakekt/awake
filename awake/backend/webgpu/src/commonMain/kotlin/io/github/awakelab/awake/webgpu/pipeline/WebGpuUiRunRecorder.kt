/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.pipeline

import io.github.awakelab.awake.render.passes2d.TexturedPrimitiveRun
import io.github.awakelab.awake.core.graphics2d.BlendMode
import io.github.awakelab.awake.core.graphics2d.TextureCompositeMode
import io.github.awakelab.awake.render.passes2d.UiPipelineKind
import io.github.awakelab.awake.render.passes2d.UiRunRecorder
import io.github.awakelab.awake.webgpu.material.Material
import io.github.awakelab.awake.webgpu.ui.DynamicMesh
import io.github.awakelab.awake.webgpu.ui.UiRenderPipeline
import io.ygdrasil.webgpu.GPURenderPassEncoder

/**
 * WebGPU's half of [io.github.awakelab.awake.render.passes2d.SharedUiRenderFeature] -- the
 * mirror of Vulkan's `VulkanUiRunRecorder`. Built per UI pass, around the encoder whose render
 * pass the caller has already begun.
 *
 * [textureMeshForPrimitive] is a pool, not one shared mesh: `queue.writeBuffer` is queue-
 * scheduled rather than interleaved with encoding, so a run's later primitive writing a shared
 * mesh would land before an earlier, already-encoded draw reads it.
 */
internal class WebGpuUiRunRecorder(
    private val encoder: GPURenderPassEncoder,
    private val quad: UiRenderPipeline,
    private val roundedQuad: UiRenderPipeline?,
    private val glyph: UiRenderPipeline?,
    private val textures: Map<TextureCompositeMode, UiRenderPipeline>,
    private val textureMeshForPrimitive: (Int) -> DynamicMesh,
) : UiRunRecorder<DynamicMesh> {

    override fun bindPipeline(kind: UiPipelineKind): Boolean {
        val pipeline = when (kind) {
            UiPipelineKind.Quad -> quad
            UiPipelineKind.RoundedQuad -> roundedQuad
            UiPipelineKind.Glyph -> glyph
            // A texture chooses its exact blend/alpha-convention pipeline in
            // drawTexturePrimitive. Requiring the straight-alpha default here would discard a
            // run that contains only premultiplied render-target textures.
            UiPipelineKind.Texture -> if (textures.isEmpty()) null else quad
        }
        // Texture binds nothing up front: each primitive binds its own material bind group.
        if (pipeline != null && kind != UiPipelineKind.Texture) {
            encoder.setPipeline(pipeline.pipeline)
            // Glyph reads its font atlas through the same group 0 its screen-size uniform uses.
            val group = if (kind == UiPipelineKind.Glyph) pipeline.bindGroup else pipeline.screenSizeBindGroup
            encoder.setBindGroup(0u, group)
        }
        return pipeline != null
    }

    override fun drawMesh(mesh: DynamicMesh) {
        encoder.setVertexBuffer(0u, mesh.vertexBufferRef())
        encoder.setIndexBuffer(mesh.indexBufferRef(), DynamicMesh.indexFormat)
        encoder.drawIndexed(mesh.drawIndexCount.toUInt())
    }

    override fun setScissor(x: Int, y: Int, width: Int, height: Int) {
        encoder.setScissorRect(x.toUInt(), y.toUInt(), width.toUInt(), height.toUInt())
    }

    override fun drawTexturePrimitive(primitive: TexturedPrimitiveRun, slotIndex: Int) {
        val pipeline = textures[primitive.compositeMode] ?: return
        val material = primitive.texture as Material
        val mesh = textureMeshForPrimitive(slotIndex)
        encoder.setPipeline(pipeline.pipeline)
        encoder.setBindGroup(0u, pipeline.bindGroupFor(material))
        mesh.update(primitive.vertices, primitive.indices)
        drawMesh(mesh)
    }
}
