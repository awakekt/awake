/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.webgpu.pipeline

import io.github.awakelab.awake.render.renderer.ShadowCascadeUniforms
import io.github.awakelab.awake.render.command.PreparedDraw
import io.github.awakelab.awake.render.pipeline.BindingSemantic
import io.github.awakelab.awake.render.pipeline.ShadowCascadePassBinding
import io.github.awakelab.awake.webgpu.texture.DepthTarget
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.beginRenderPass

/**
 * Renders the frame's draws a second time, depth only, into [depthTarget], before the scene
 * pass. Knows nothing about why a caller wants that depth: the transform is whatever the
 * caller's own vertex shader reads out of the per-draw uniform buffer.
 */
class DepthPrePassFeature(
    val depthTarget: DepthTarget,
    private val depthOnlyPipeline: DepthOnlyPipeline,

) {

    /** This pass own pipeline, for a caller that has to build the prepared draws it takes. */
    val depthOnlyHandle get() = depthOnlyPipeline.handle

    /**
     * One render pass per cascade, each into that cascade's own layer.
     *
     * A pass per layer rather than one pass over the array, for the reason Vulkan's twin gives:
     * an attachment is a single layer, and writing several at once is multiview -- a different
     * feature with its own support question on both backends.
     */
    fun recordCommands(
        encoder: GPUCommandEncoder,
        draws: List<PreparedDraw>,
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
            recordCascade(encoder, draws, cascade)
        }
    }

    private fun recordCascade(
        encoder: GPUCommandEncoder,
        draws: List<PreparedDraw>,
        cascade: Int,
    ) {
        encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = emptyList(),
                depthStencilAttachment = RenderPassDepthStencilAttachment(
                    view = depthTarget.viewFor(cascade),
                    depthClearValue = 1.0f,
                    depthLoadOp = GPULoadOp.Clear,
                    depthStoreOp = GPUStoreOp.Store,
                ),
            ),
        ) {
            val recorder = WebGpuCommandRecorder(this)
            recorder.setScissor(0, 0, depthTarget.size, depthTarget.size)
            recorder.bindPipeline(depthOnlyPipeline.handle)
            // Group 1, once for the whole cascade: every mesh here renders through one matrix.
            if (depthOnlyPipeline.hasCascadeBlock) {
                recorder.bindMaterial(ShadowCascadePassBinding, depthOnlyPipeline.cascadeBinding(cascade))
            }

            var drawIndex = 0
            while (drawIndex < draws.size) {
                val prepared = draws[drawIndex]
                val vBuffer = prepared.vertexBuffer
                if (vBuffer != null) {
                    recorder.bindVertexBuffer(0, vBuffer)
                    val indexBuffer = prepared.indexBuffer
                    if (indexBuffer != null) {
                        recorder.bindIndexBuffer(indexBuffer)
                        recorder.bindMaterial(BindingSemantic.Material, prepared.materialBinding)
                        recorder.drawIndexed(prepared.elementCount)
                    } else {
                        recorder.bindMaterial(BindingSemantic.Material, prepared.materialBinding)
                        recorder.draw(prepared.elementCount)
                    }
                }
                drawIndex += 1
            }
            end()
        }
    }

    fun destroy() {
        depthOnlyPipeline.destroy()
        depthTarget.destroy()
    }
}
