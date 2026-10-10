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
import com.awakekt.awake.render.command.toGpuResolvedDraw
import com.awakekt.awake.render.passes.drawsEdges
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.canInstance
import com.awakekt.awake.render.pipeline.depthRenderKey
import com.awakekt.awake.render.pipeline.edges
import com.awakekt.awake.webgpu.material.Material
import com.awakekt.awake.webgpu.pipeline.RenderPipeline

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

    override fun canInstance(format: VertexFormat): Boolean = format in renderer.instancedPipelines

    override fun canInstance(format: VertexFormat, cullMode: CullMode): Boolean = renderer.pipelines.canInstance(format, cullMode)

    override fun prepare(
        request: GpuDrawRequest,
        sourceIndex: Int,
        context: GpuDrawPreparationContext,
    ): GpuResolvedDraw? {
        if (sourceIndex == 0) startBatch()
        val edgePipeline = if (context.edges) edgePipelineFor(request) ?: return null else null
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
        ) ?: return null
        val resolved = draw.toGpuResolvedDraw()
        // An edge draws over the frame and casts nothing, so it has no depth pass to join.
        if (edgePipeline != null) return resolved
        // A renderer may request camera-space depth without requesting shadow cascades. The
        // resolved packet still needs the depth pipeline and material binding for that pass.
        val depthFeature = renderer.depthPrePass ?: renderer.sceneDepthPass
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
        val depthMaterial = if (request.alphaMode == com.awakekt.awake.render.pipeline.AlphaMode.Masked) {
            draw.uniformBuffer?.let {
                depthFeature.materialBinding(depthPipeline, sourceMaterial, it)
            }
        } else {
            draw.uniformBuffer?.let { depthFeature.materialBinding(depthPipeline, it) }
        }
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

    private fun startBatch() {
        instancedRuns = 0
        edgeRuns = 0
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
