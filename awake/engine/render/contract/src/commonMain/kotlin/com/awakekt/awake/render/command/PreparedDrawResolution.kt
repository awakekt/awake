/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

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
    batchKey = batchKey,
    instances = instances,
    instanceVertexBuffer = instanceVertexBuffer,
    jointPaletteBinding = jointPaletteBinding,
    shadowBinding = shadowBinding,
    sceneDepthBinding = sceneDepthBinding,
    instanceColorBuffer = instanceColorBuffer,
    instanceFrameBuffer = instanceFrameBuffer,
)
