/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.toGpuResolvedDraw
import com.awakekt.awake.render.material.Material
import com.awakekt.awake.render.passes.drawsEdges
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.canInstance
import com.awakekt.awake.render.pipeline.depthRenderKey
import com.awakekt.awake.render.pipeline.edges
import com.awakekt.awake.vulkan.pipeline.RenderPipeline

/** Prepares one generic draw through Vulkan's existing resource preparation code.
 *
 * Named [VulkanDrawPreparer] to mirror [WebGpuDrawPreparer] on the sibling backend --
 * "Gpu" was redundant because Vulkan is already a GPU backend, not a qualifier.
 */
internal class VulkanDrawPreparer(
    private val renderer: Renderer,
) : GpuDrawPreparer {
    /** Uniform slots are allocated per material across the complete frame batch. Resolving one
     * command at a time must retain that allocation map; resetting it for every command makes all
     * draws sharing a material overwrite slot zero, so the presented frame shows only the last
     * transform. The compiler starts each batch at source index zero, which is the frame boundary
     * for this resolver seam. */
    private val materialUsage = mutableMapOf<Material, Int>()

    /** Instanced draws so far this batch: each gets the next instance buffer, so the pool grows
     * with how many instanced draws a frame has rather than with where they sit in it. */
    private var instancedRuns = 0

    override fun canInstance(format: VertexFormat): Boolean = format in renderer.instancedPipelinesByFormat

    override fun canInstance(format: VertexFormat, cullMode: CullMode): Boolean = renderer.pipelines.canInstance(format, cullMode)

    override fun prepare(
        request: GpuDrawRequest,
        sourceIndex: Int,
        context: GpuDrawPreparationContext,
    ): GpuResolvedDraw? {
        if (sourceIndex == 0) {
            materialUsage.clear()
            instancedRuns = 0
            // The GPU may still be reading this frame slot's uniforms from two frames ago. The UI
            // phase usually waited for it already, but a frame with no UI never did, and preparing
            // runs before acquiring the image, which is the next wait. Free when already waited.
            renderer.waitForCurrentFrameResourceSlot()
            // Submitted offscreen work may still read the slots this batch is about to rewrite.
            renderer.awaitSubmittedOffscreenCommandsFor(renderer.swapchainManager.currentFrame)
        }
        val edgePipeline = if (context.edges) edgePipelineFor(request) ?: return null else null
        val cascades = context.shadowCascadeData ?: context.shadowViewProjections
            .takeIf { it.isNotEmpty() }
            ?.let { GpuShadowCascadeData(it, FloatArray(it.size) { Float.MAX_VALUE }) }
        val prepared = renderer.prepareGpuDraw(
            cmd = request,
            frameIndex = renderer.swapchainManager.currentFrame,
            instancedIndex = if (request.instanceModels != null) instancedRuns++ else sourceIndex,
            isTransparent = request.transparent,
            materialUsage = materialUsage,
            viewProjection = context.viewProjection,
            cameraPosition = context.cameraEye,
            cameraForward = context.cameraForward,
            lightUniforms = context.passUniforms,
            shadowCascades = cascades,
            fogColor = context.environment.fogColor,
            fogDensity = context.environment.fogDensity,
            debugView = context.environment.debugView,
            exposure = context.environment.exposure,
            edgePipeline = edgePipeline,
        ) ?: return null
        // An edge draws over the frame and casts nothing, so it has no depth pass to join.
        if (edgePipeline != null) return prepared.toGpuResolvedDraw()
        val format = prepared.vertexFormat
        val depthFeature = renderer.depthPrePassFeature ?: renderer.sceneDepthPassFeature
        val depthKey = request.depthRenderKey()
        // Keyed even when it casts nothing, so the depth pass leaves a masked draw out rather than
        // drawing its whole card.
        val resolved = prepared.toGpuResolvedDraw().copy(depthRenderKey = depthKey)
        val depthPipeline = depthFeature?.pipelineFor(depthKey, format) ?: return resolved
        val sourceMaterial = request.material as? com.awakekt.awake.vulkan.material.Material
            ?: return resolved
        if (request.alphaMode == com.awakekt.awake.render.pipeline.AlphaMode.Masked &&
            !sourceMaterial.hasTexture
        ) {
            return resolved
        }
        return resolved.copy(
            depthRenderKey = depthKey,
            alphaCutoff = request.alphaCutoff,
            depthPipeline = depthPipeline,
            depthMaterialBinding = prepared.materialBinding,
            depthJointPaletteBinding = prepared.jointPaletteBinding,
        )
    }

    /** The edge pipeline that draws [request]'s edges, or null when a wireframe overlay draws none. */
    private fun edgePipelineFor(request: GpuDrawRequest): RenderPipeline? =
        (request.material as? com.awakekt.awake.vulkan.material.Material)
            ?.takeIf { request.drawsEdges(it.uniformFloatCount) }
            ?.let { renderer.pipelines.edges(request.mesh.format) }
}
