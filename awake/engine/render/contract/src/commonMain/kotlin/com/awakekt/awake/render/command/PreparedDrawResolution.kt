/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.render.pipeline.DepthRenderKey

/**
 * Copies a backend-prepared draw into the canonical packet without exposing backend classes.
 *
 * A driver resolver may continue using its existing preparation code (uniform uploads, pipeline
 * selection and instance-buffer creation), then use this adapter at the contract boundary. The
 * executor receives only the resulting handles and never needs to inspect the prepared object.
 */
fun PreparedDraw.toGpuResolvedDraw(): GpuResolvedDraw = GpuResolvedDraw(
    pipeline = pipeline,
    vertexFormat = vertexFormat,
    depthPipeline = depthPipeline,
    depthMaterialBinding = depthMaterialBinding,
    depthJointPaletteBinding = depthJointPaletteBinding,
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

/**
 * [toGpuResolvedDraw], with the depth-pass fields a resolver decides after preparing the draw given
 * here rather than by copying the packet: a resolver that set them with `copy` built the draw two or
 * three times over, per draw per frame.
 *
 * @param depthRenderKey The draw's depth-pass key, or null when it joins no depth pass.
 * @param alphaCutoff The cutoff its depth pass discards below; the prepared draw's by default.
 * @param depthPipeline The pipeline its depth pass draws it with; the prepared draw's by default.
 * @param depthMaterialBinding What that pipeline binds for it; the prepared draw's by default.
 * @param depthJointPaletteBinding Its joint palette in the depth pass; the prepared draw's by default.
 */
fun PreparedDraw.toGpuResolvedDraw(
    depthRenderKey: DepthRenderKey?,
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
