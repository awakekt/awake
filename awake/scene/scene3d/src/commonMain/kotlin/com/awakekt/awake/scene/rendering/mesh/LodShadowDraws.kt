/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering.mesh

import com.awakekt.awake.core.pool.ScratchPool
import com.awakekt.awake.render.material.GpuMaterial
import com.awakekt.awake.render.mesh.GpuMesh
import com.awakekt.awake.render.passes.RenderDrawCommand

/**
 * A shadow-only copy of [source] that casts with [mesh], a coarser level of detail than the one
 * [source] draws: placed, posed, masked and culled as [source] is, so the shadow matches what is
 * drawn but costs the coarser level's triangles in every cascade. [source] then casts nothing.
 */
internal fun ScratchPool<RenderDrawCommand>.shadowTwin(
    source: RenderDrawCommand,
    mesh: GpuMesh,
    material: GpuMaterial = source.material,
): RenderDrawCommand = obtain().set(
    mesh = mesh,
    material = material,
    model = source.model,
    extraUniformFloats = source.extraUniformFloats,
    vertexAnimation = source.vertexAnimation,
    timeSeconds = source.timeSeconds,
    cullMode = source.cullMode,
    alphaMode = source.alphaMode,
    alphaCutoff = source.alphaCutoff,
    shadowsOnly = true,
    worldBounds = source.worldBounds,
    castsShadows = true,
)
