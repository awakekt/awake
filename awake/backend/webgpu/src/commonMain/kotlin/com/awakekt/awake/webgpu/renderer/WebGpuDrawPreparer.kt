/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.toGpuResolvedDraw
import com.awakekt.awake.render.pipeline.DepthCasterKind
import com.awakekt.awake.render.pipeline.depthRenderKey

/** Prepares one generic draw through WebGPU's existing resource preparation code. */
internal class WebGpuDrawPreparer(
    private val renderer: Renderer,
) : GpuDrawPreparer {
    override fun prepare(
        request: GpuDrawRequest,
        sourceIndex: Int,
        context: GpuDrawPreparationContext,
    ): GpuResolvedDraw? {
        val cascades = context.shadowViewProjections.takeIf { it.isNotEmpty() }?.let {
            GpuShadowCascadeData(it, FloatArray(it.size) { Float.MAX_VALUE })
        }
        val primary = PrimaryPipelineBinding(
            renderer.renderPipeline.handle,
            renderer.wireframe && renderer.wireframeRenderPipeline != null,
        )
        val draw = renderer.prepareGpuDraw(
            cmd = request,
            singleIndex = sourceIndex,
            instancedIndex = sourceIndex,
            isTransparent = request.transparent,
            primary = primary,
            viewProjection = context.viewProjection,
            cameraEye = context.cameraEye,
            lightUniforms = context.passUniforms,
            shadowCascades = cascades,
            fogColor = context.environment.fogColor,
            fogDensity = context.environment.fogDensity,
        ) ?: return null
        val resolved = draw.toGpuResolvedDraw()
        val gpuPrepared = draw
        // A renderer may request camera-space depth without requesting shadow cascades. The
        // resolved packet still needs the depth pipeline and material binding for that pass.
        val depthFeature = renderer.depthPrePass ?: renderer.sceneDepthPass
        val format = draw.vertexFormat ?: return resolved
        val depthKey = request.depthRenderKey()
        val depthPipeline = depthFeature?.pipelineFor(depthKey, format) ?: return resolved
        val sourceMaterial = request.material as? com.awakekt.awake.webgpu.material.Material
            ?: return resolved
        if (request.alphaMode == com.awakekt.awake.render.pipeline.AlphaMode.Masked &&
            !sourceMaterial.hasTexture
        ) {
            return resolved
        }
        val depthMaterial = if (request.alphaMode == com.awakekt.awake.render.pipeline.AlphaMode.Masked) {
            gpuPrepared.uniformBuffer?.let {
                depthFeature.materialBinding(depthPipeline, sourceMaterial, it)
            }
        } else {
            gpuPrepared.uniformBuffer?.let { depthFeature.materialBinding(depthPipeline, it) }
        }
        val depthPalette = if (depthKey.kind == DepthCasterKind.SkinnedInstanced) {
            gpuPrepared.jointPaletteGpuBuffer?.let { depthFeature.paletteBinding(depthPipeline, it) }
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
}
