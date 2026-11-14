/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.toGpuResolvedDraw
import com.awakekt.awake.render.passes.FrameUniformInputs
import com.awakekt.awake.render.passes.drawsEdges
import com.awakekt.awake.render.pipeline.AlphaMode
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.DepthRenderKey
import com.awakekt.awake.render.pipeline.InstancedDrawKind
import com.awakekt.awake.render.pipeline.canInstance
import com.awakekt.awake.render.pipeline.depthRenderKey
import com.awakekt.awake.render.pipeline.edges
import com.awakekt.awake.render.pipeline.resolveInstanced
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.pipeline.DepthOnlyPipeline
import com.awakekt.awake.webgpu.pipeline.DepthPrePassFeature
import com.awakekt.awake.webgpu.pipeline.RenderPipeline
import com.awakekt.awake.webgpu.pipeline.WebGpuPipelineHandle

/** Prepares one generic draw through WebGPU's existing resource preparation code. */
internal class WebGpuDrawPreparer(
    private val renderer: Renderer,
) : GpuDrawPreparer {
    /** Instanced draws so far this batch: each gets the next instance buffer, so the pool grows
     * with how many instanced draws a frame has rather than with where they sit in it. */
    private var instancedRuns = 0

    /** Edge draws so far this batch, numbering their uniform slots in the edge pipelines' pools
     * from 0 rather than after every visible draw. */
    private var edgeRuns = 0

    /** The frame's share of the uniform blocks, so a draw whose block would come out the same is not packed again. */
    private val frameInputs = FrameUniformInputs()
    private var frameVersion = NO_FRAME_VERSION

    /** Instanced casters' shared depth groups, for this batch. */
    private val sharedDepth = SharedDepthBindings()

    /** Whether this batch's wireframe-overlay edges have their frame block staged yet. */
    private var edgeFrameStaged = false

    override fun canInstance(format: VertexFormat): Boolean = format in renderer.instancedPipelines

    override fun canInstance(format: VertexFormat, cullMode: CullMode): Boolean = renderer.pipelines.canInstance(format, cullMode)

    override fun canTintInstances(format: VertexFormat, cullMode: CullMode): Boolean =
        renderer.pipelines.resolveInstanced(format, InstancedDrawKind.Plain, cullMode = cullMode)?.readsInstanceTint == true

    /** The shadow depth pipelines set `unclippedDepth` exactly when the device can honour it. */
    override val clampsShadowDepth: Boolean get() = renderer.graphicsDevice.unclippedDepth

    /** Queue writes are ordered with submissions, so a resolved frame's buffers hold until rewritten. */
    override val redrawsPreparedFrames: Boolean get() = true

    override fun prepare(
        request: GpuDrawRequest,
        sourceIndex: Int,
        context: GpuDrawPreparationContext,
    ): GpuResolvedDraw? {
        if (sourceIndex == 0) startBatch(context)
        val edgePipeline = if (context.edges) edgePipelineFor(request) ?: return null else null
        if (edgePipeline != null) stageEdgeFrame(context)
        val primary = PrimaryPipelineBinding(
            renderer.renderPipeline.handle,
            renderer.wireframe && renderer.wireframeRenderPipeline != null,
        )
        val draw = renderer.prepareGpuDraw(
            cmd = request,
            singleIndex = if (edgePipeline != null) edgeRuns++ else sourceIndex,
            instancedIndex = if (request.instanceModels != null) instancedRuns++ else sourceIndex,
            isTransparent = request.transparent,
            primary = primary,
            viewProjection = context.viewProjection,
            cameraEye = context.cameraEye,
            cameraForward = context.cameraForward,
            lightUniforms = context.passUniforms,
            shadowCascades = context.cascades(),
            fogColor = context.environment.fogColor,
            fogDensity = context.environment.fogDensity,
            debugView = context.environment.debugView,
            exposure = context.environment.exposure,
            edgePipeline = edgePipeline,
            frameVersion = frameVersion,
        ) ?: return null
        val resolved = draw.toGpuResolvedDraw()
        // An edge draws over the frame and casts nothing, so it has no depth pass to join.
        if (edgePipeline != null) return resolved
        // A renderer may request camera-space depth, or a mask, without requesting shadow cascades.
        // The resolved packet still needs a depth pipeline and material binding for that pass; one
        // group serves every depth pass, whose group-0 layouts are the same.
        val depthFeature = renderer.depthPrePass ?: renderer.sceneDepthPass ?: renderer.maskPass
        val format = draw.vertexFormat ?: return resolved
        val depthKey = request.depthRenderKey()
        // Keyed even when it casts nothing, so the depth pass leaves a masked draw out rather than
        // drawing its whole card.
        val keyed = resolved.copy(depthRenderKey = depthKey)
        val depthPipeline = depthFeature?.pipelineFor(depthKey, format) ?: return keyed
        val sourceMaterial = request.material as? com.awakekt.awake.webgpu.material.Material
            ?: return keyed
        if (request.alphaMode == com.awakekt.awake.render.pipeline.AlphaMode.Masked &&
            !sourceMaterial.hasTexture
        ) {
            return keyed
        }
        val depthMaterial = depthMaterialBinding(request, draw, depthKey, depthFeature, depthPipeline, sourceMaterial)
        val depthPalette = if (depthKey.kind == DepthCasterKind.SkinnedInstanced) {
            draw.jointPaletteGpuBuffer?.let { depthFeature.paletteBinding(depthPipeline, it) }
        } else {
            null
        }
        return resolved.copy(
            depthRenderKey = depthKey,
            alphaCutoff = request.alphaCutoff,
            depthPipeline = depthPipeline.handle,
            depthMaterialBinding = depthMaterial,
            depthJointPaletteBinding = depthPalette,
        )
    }

    /**
     * The group [draw]'s depth passes bind: a masked caster's through its material's textures, and
     * an opaque one's from its own uniform buffer, or the group an alike instanced caster already has.
     */
    @Suppress("LongParameterList")
    private fun depthMaterialBinding(
        request: GpuDrawRequest,
        draw: WebGpuPreparedDraw,
        depthKey: DepthRenderKey,
        depthFeature: DepthPrePassFeature,
        depthPipeline: DepthOnlyPipeline,
        sourceMaterial: Material,
    ): MaterialBinding? {
        val buffer = draw.uniformBuffer ?: return null
        val wholeBlock = draw.packsWholeBlock()
        return if (request.alphaMode == AlphaMode.Masked) {
            depthFeature.materialBinding(depthPipeline, sourceMaterial, buffer)
        } else {
            sharedDepth.bindingFor(request, depthKey, depthPipeline, draw.pipeline, wholeBlock) {
                depthFeature.materialBinding(depthPipeline, buffer, wholeBlock)
            }
        }
    }

    /** The batch's edges are a pass of their own: their view-projection and debug code, staged once. */
    private fun stageEdgeFrame(context: GpuDrawPreparationContext) {
        if (edgeFrameStaged) return
        renderer.graphicsDevice.frameUniforms.stage(renderer.uniformUploads, context, context.cascades(), edges = true)
        edgeFrameStaged = true
    }

    /** A draw whose single block starts with its MVP, which reads the identity frame block in a camera-depth pass. */
    private fun WebGpuPreparedDraw.packsWholeBlock(): Boolean = (pipeline as? WebGpuPipelineHandle)?.splitDrawBlockFloats == 0

    private fun startBatch(context: GpuDrawPreparationContext) {
        // A batch prepared and never drawn has its writes land now, so the stage holds one batch at most.
        renderer.flushUniformUploads()
        instancedRuns = 0
        edgeRuns = 0
        edgeFrameStaged = false
        sharedDepth.clear()
        val environment = context.environment
        // Before any draw of the batch: the block its lit draws read beside their own.
        renderer.graphicsDevice.frameUniforms.stage(renderer.uniformUploads, context, context.cascades())
        renderer.graphicsDevice.frameUniforms.stageIdentity(renderer.uniformUploads)
        frameVersion = frameInputs.update(
            viewProjection = context.viewProjection,
            cameraEye = context.cameraEye,
            cameraForward = context.cameraForward,
            lightPayload = context.passUniforms,
            shadowCascades = context.cascades(),
            fogColor = environment.fogColor,
            fogDensity = environment.fogDensity,
            debugView = environment.debugView,
            exposure = environment.exposure,
        )
    }

    /** These shadow cascades, or the bare shadow matrices as cascades without splits. */
    private fun GpuDrawPreparationContext.cascades(): GpuShadowCascadeData? = shadowCascadeData ?: shadowViewProjections
        .takeIf { it.isNotEmpty() }
        ?.let { GpuShadowCascadeData(it, FloatArray(it.size) { Float.MAX_VALUE }) }

    /** The edge pipeline that draws [request]'s edges, or null when a wireframe overlay draws none. */
    private fun edgePipelineFor(request: GpuDrawRequest): RenderPipeline? =
        (request.material as? Material)
            ?.takeIf { request.drawsEdges(it.uniformFloatCount) }
            ?.let { renderer.pipelines.edges(request.mesh.format) }
}
