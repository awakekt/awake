/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.MaterialBinding
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.pipeline.DepthRenderKey

/**
 * [this] draw as the canonical packet, its depth-pass fields given here: built once a draw.
 *
 * The preparer learns a draw's depth pass only after preparing it, and built the packet first and
 * then a `copy` with those fields: two packets a draw, every draw of every moving frame, about a
 * quarter of the WebGPU backend's garbage on a moving camera (#1555).
 */
internal fun WebGpuPreparedDraw.resolved(
    depthRenderKey: DepthRenderKey? = null,
    alphaCutoff: Float = this.alphaCutoff,
    depthPipeline: PipelineHandle? = this.depthPipeline,
    depthMaterialBinding: MaterialBinding? = this.depthMaterialBinding,
    depthJointPaletteBinding: MaterialBinding? = this.depthJointPaletteBinding,
): GpuResolvedDraw = GpuResolvedDraw(
    pipeline = pipeline,
    vertexFormat = vertexFormat,
    depthPipeline = depthPipeline,
    depthMaterialBinding = depthMaterialBinding,
    depthJointPaletteBinding = depthJointPaletteBinding,
    depthRenderKey = depthRenderKey,
    alphaCutoff = alphaCutoff,
    materialBinding = materialBinding,
    vertexBuffer = vertexBuffer,
    indexBuffer = indexBuffer,
    elementCount = elementCount,
    transparent = transparent,
    depthSortKey = depthSortKey,
    sortOrder = sortOrder,
    batchKey = batchKey,
    instances = instances,
    instanceVertexBuffer = instanceVertexBuffer,
    jointPaletteBinding = jointPaletteBinding,
    shadowBinding = shadowBinding,
    sceneDepthBinding = sceneDepthBinding,
    instanceColorBuffer = instanceColorBuffer,
    instanceFrameBuffer = instanceFrameBuffer,
    sortLayer = sortLayer,
)
