/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.passes.ContentDepthSource
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.BindingSemantic
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.pipeline.ShadowCascadePassBinding
import com.awakekt.awake.render.renderer.RenderStatsCounter
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
 *
 * Content features add draws of their own through `contentCasters`, as on Vulkan.
 */
class DepthPrePassFeature(
    val depthTarget: DepthTarget,
    private val depthOnlyPipeline: DepthOnlyPipeline,
    private val variantPipelines: Map<DepthCasterKind, DepthOnlyPipeline> = emptyMap(),
    private val formatPipelines: Map<VertexFormat, DepthOnlyPipeline> = emptyMap(),
    private val keyedVariantPipelines: Map<DepthRenderKey, DepthOnlyPipeline> = emptyMap(),
    private val instancedFormatPipelines: Map<VertexFormat, DepthOnlyPipeline> = emptyMap(),
) {
    /** The owning `Renderer`'s counter, set when it takes this feature: a pass is built first. */
    internal var stats: RenderStatsCounter? = null

    /** A content feature's depth pipeline, over that feature's own group 0, and what hands it a
     * draw each frame. Its pipeline is the caller's to destroy, once removed from `contentCasters`. */
    internal class ContentCaster(val pipeline: DepthOnlyPipeline, val source: ContentDepthSource)

    /** Drawn in every rendered sub-pass, after the frame's draws. */
    internal val contentCasters = ArrayList<ContentCaster>()

    /** Every pipeline the cascade matrices are written to, content casters' included. */
    private val cascadePipelines: List<DepthOnlyPipeline>
        get() = buildList {
            add(depthOnlyPipeline)
            addAll(variantPipelines.values)
            addAll(keyedVariantPipelines.values)
            addAll(formatPipelines.values)
            addAll(instancedFormatPipelines.values)
            contentCasters.forEach { add(it.pipeline) }
        }.distinct()

    /** This pass own pipeline, for a caller that has to build the prepared draws it takes. */
    val depthOnlyHandle get() = depthOnlyPipeline.handle

    internal fun pipelineFor(kind: DepthCasterKind, format: VertexFormat): DepthOnlyPipeline? {
        val pipeline = when (kind) {
            DepthCasterKind.Ordinary -> formatPipelines[format] ?: depthOnlyPipeline
            DepthCasterKind.Instanced -> instancedFormatPipelines[format] ?: variantPipelines[kind]
            // A skinned pipeline's own depth shader first; the plan's variant covers the plain format.
            DepthCasterKind.Skinned -> formatPipelines[format] ?: variantPipelines[kind]
            else -> variantPipelines[kind]
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
        val allPipelines = cascadePipelines
        val activeCount = minOf(viewProjections.size, depthTarget.layers)
        for (cascade in 0 until activeCount) {
            val source = viewProjections[cascade]
            allPipelines.forEach { it.writeCascade(cascade, source) }
            recordCascade(encoder, draws, cascade)
        }
        for (layer in activeCount until minOf(4, depthTarget.layers)) {
            allPipelines.forEach { it.writeCascade(layer, Mat4()) }
            recordCascade(encoder, emptyList(), layer)
        }
    }

    /**
     * Records only the requested subpasses, avoiding redundant passes over unused layers. Each one
     * also draws the content casters that [environment] lets draw.
     */
    fun recordCommands(
        encoder: GPUCommandEncoder,
        subPasses: List<GpuSubPass>,
        environment: GpuEnvironmentState,
    ) {
        if (subPasses.isEmpty()) return
        val allPipelines = cascadePipelines
        for (subPass in subPasses) {
            val layer = subPass.targetLayer
            if (layer in 0 until depthTarget.layers) {
                allPipelines.forEach { it.writeCascade(layer, subPass.viewProjection) }
                recordCascade(encoder, subPass.resolvedDraws, layer, environment)
            }
        }
        val activeLayers = subPasses.map { it.targetLayer }.toSet()
        for (cascade in 0 until minOf(4, depthTarget.layers)) {
            if (cascade !in activeLayers) {
                allPipelines.forEach { it.writeCascade(cascade, Mat4()) }
                recordCascade(encoder, emptyList(), cascade)
            }
        }
    }

    private fun recordCascade(
        encoder: GPUCommandEncoder,
        draws: List<PreparedDraw>,
        cascade: Int,
        content: GpuEnvironmentState? = null,
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
            val recorder = WebGpuCommandRecorder(this, stats)
            recorder.setScissor(0, 0, depthTarget.size, depthTarget.size)
            var boundPipeline: DepthOnlyPipeline? = null
            var drawIndex = 0
            while (drawIndex < draws.size) {
                val prepared = draws[drawIndex]
                // An instance buffer, not a count above one, makes a draw instanced: a lone copy
                // still takes its model from the buffer.
                val instanced = prepared.instanceVertexBuffer != null
                val kind = when {
                    instanced && prepared.jointPaletteBinding != null ->
                        DepthCasterKind.SkinnedInstanced
                    instanced && prepared.instanceColorBuffer != null ->
                        DepthCasterKind.Particle
                    instanced -> DepthCasterKind.Instanced
                    prepared.vertexFormat?.isSkinned == true -> DepthCasterKind.Skinned
                    else -> DepthCasterKind.Ordinary
                }
                val format = prepared.vertexFormat
                // Keyed by coverage too: a masked caster draws through its cut-out shader, or not at all.
                val alphaMode = prepared.depthRenderKey?.alphaMode ?: AlphaMode.Opaque
                val pipeline = if (format == null) null else pipelineFor(DepthRenderKey(kind, alphaMode), format)
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
            if (content != null) recorder.recordContentCasters(contentCasters, cascade, content)
            end()
        }
    }

    /**
     * Destroys every depth-only pipeline this feature built, each exactly once, and then its depth
     * target. Call once, after the last frame that renders or samples the depth.
     */
    fun destroy() {
        buildList {
            add(depthOnlyPipeline)
            addAll(variantPipelines.values)
            addAll(formatPipelines.values)
            addAll(instancedFormatPipelines.values)
            addAll(keyedVariantPipelines.values)
        }.distinct().forEach { it.destroy() }
        depthTarget.destroy()
    }
}

/** Each of [casters]' draw for this frame, through its own pipeline and its own group 0. */
private fun WebGpuCommandRecorder.recordContentCasters(
    casters: List<DepthPrePassFeature.ContentCaster>,
    cascade: Int,
    environment: GpuEnvironmentState,
) {
    for (index in casters.indices) {
        val caster = casters[index]
        // One frame in flight, so one uniform slot: frame 0, as the scene pass records it.
        val prepared = caster.source.depthDraw(0, environment)
        val vertexBuffer = prepared?.vertexBuffer
        if (prepared == null || vertexBuffer == null) continue
        bindPipeline(caster.pipeline.handle)
        bindMaterial(ShadowCascadePassBinding, caster.pipeline.cascadeBinding(cascade))
        bindMaterial(BindingSemantic.Material, prepared.materialBinding)
        bindVertexBuffer(0, vertexBuffer)
        val indexBuffer = prepared.indexBuffer
        if (indexBuffer != null) {
            bindIndexBuffer(indexBuffer)
            drawIndexed(prepared.elementCount, prepared.instances)
        } else {
            draw(prepared.elementCount, prepared.instances)
        }
    }
}
