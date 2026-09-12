/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.pipeline.ShadowCascadePassBinding
import com.awakekt.awake.webgpu.texture.DepthTarget
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
    private val variantPipelines: Map<DepthCasterKind, DepthOnlyPipeline> = emptyMap(),
    private val formatPipelines: Map<VertexFormat, DepthOnlyPipeline> = emptyMap(),
    private val keyedVariantPipelines: Map<DepthRenderKey, DepthOnlyPipeline> = emptyMap(),

) {

    /** This pass own pipeline, for a caller that has to build the prepared draws it takes. */
    val depthOnlyHandle get() = depthOnlyPipeline.handle

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

    internal fun materialBinding(pipeline: DepthOnlyPipeline, buffer: io.ygdrasil.webgpu.GPUBuffer) =
        pipeline.materialBinding(buffer)

    internal fun materialBinding(
        pipeline: DepthOnlyPipeline,
        material: com.awakekt.awake.webgpu.material.Material,
        uniformBuffer: io.ygdrasil.webgpu.GPUBuffer,
    ) = pipeline.materialBinding(material, uniformBuffer)

    internal fun paletteBinding(pipeline: DepthOnlyPipeline, buffer: io.ygdrasil.webgpu.GPUBuffer) =
        pipeline.paletteBinding(buffer)

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
        cascades: GpuShadowCascadeData,
    ) = recordCommands(encoder, draws, cascades.viewProjections)

    /** Records the generic packet's arbitrary layered depth resource. */
    fun recordCommands(
        encoder: GPUCommandEncoder,
        draws: List<PreparedDraw>,
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
        }.distinct()
        for (cascade in 0 until depthTarget.layers) {
            val source = viewProjections[minOf(cascade, viewProjections.lastIndex)]
            allPipelines.forEach { it.writeCascade(cascade, source) }
            recordCascade(encoder, draws, cascade)
        }
    }

    /** Missing layers are still cleared, keeping every sampled subresource initialized without
     * making this backend interpret why a layer was requested. */
    fun recordCommands(
        encoder: GPUCommandEncoder,
        subPasses: List<GpuSubPass>,
    ) {
        if (subPasses.isEmpty()) return
        val byLayer = subPasses.associateBy { it.targetLayer }
        val allPipelines = buildList {
            add(depthOnlyPipeline)
            addAll(variantPipelines.values)
            addAll(keyedVariantPipelines.values)
        }.distinct()
        for (layer in 0 until depthTarget.layers) {
            val subPass = byLayer[layer]
            val source = subPass?.viewProjection ?: Mat4()
            allPipelines.forEach { it.writeCascade(layer, source) }
            recordCascade(encoder, subPass?.resolvedDraws.orEmpty(), layer)
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
            var boundPipeline: DepthOnlyPipeline? = null
            var drawIndex = 0
            while (drawIndex < draws.size) {
                val prepared = draws[drawIndex]
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
                val format = prepared.vertexFormat
                val pipeline = if (format == null) null else pipelineFor(kind, format)
                val vBuffer = prepared.vertexBuffer
                val depthMaterial = prepared.depthMaterialBinding
                val instanceBuffer = prepared.instanceVertexBuffer
                val paletteBinding = prepared.depthJointPaletteBinding
                val needsMaterial = pipeline?.handle?.hasBindingGroup(0) == true
                if (pipeline != null && vBuffer != null && (!needsMaterial || depthMaterial != null)) {
                    if (boundPipeline !== pipeline) {
                        recorder.bindPipeline(pipeline.handle)
                        if (pipeline.hasCascadeBlock) {
                            recorder.bindMaterial(
                                ShadowCascadePassBinding,
                                pipeline.cascadeBinding(cascade),
                            )
                        }
                        boundPipeline = pipeline
                    }
                    recorder.bindVertexBuffer(0, vBuffer)
                    if (kind != DepthCasterKind.Ordinary && instanceBuffer != null) {
                        recorder.bindVertexBuffer(1, instanceBuffer)
                        prepared.instanceColorBuffer?.let {
                            recorder.bindVertexBuffer(2, it)
                        }
                        prepared.instanceFrameBuffer?.let {
                            recorder.bindVertexBuffer(3, it)
                        }
                    }
                    val indexBuffer = prepared.indexBuffer
                    if (kind == DepthCasterKind.SkinnedInstanced && paletteBinding != null) {
                        recorder.bindMaterial(BindingSemantic.JointPalette, paletteBinding)
                    }
                    if (indexBuffer != null) {
                        recorder.bindIndexBuffer(indexBuffer)
                        if (needsMaterial && depthMaterial != null) {
                            recorder.bindMaterial(BindingSemantic.Material, depthMaterial)
                        }
                        recorder.drawIndexed(prepared.elementCount, prepared.instances)
                    } else {
                        if (needsMaterial && depthMaterial != null) {
                            recorder.bindMaterial(BindingSemantic.Material, depthMaterial)
                        }
                        recorder.draw(prepared.elementCount, prepared.instances)
                    }
                }
                drawIndex += 1
            }
            end()
        }
    }

    fun destroy() {
        buildList {
            add(depthOnlyPipeline)
            addAll(variantPipelines.values)
            addAll(formatPipelines.values)
        }.distinct().forEach { it.destroy() }
        depthTarget.destroy()
    }
}
