/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.texture.RenderTarget

/**
 * Builds the six generic cubemap-face passes for a point-shadow update.
 *
 * Face ordering and projection are owned by [PointShadowMatrices]. This function only lowers
 * that shared plan to hardware-neutral subpass requests; it does not select pipelines or inspect
 * the light, so Vulkan and WebGPU consume the same layer sequence.
 */
fun PointShadowMatrices.toSubPasses(
    target: RenderTarget,
    draws: List<GpuResolvedDraw>,
): List<GpuSubPass> = viewProjections.mapIndexed { face, viewProjection ->
    GpuSubPass(
        target = target,
        targetLayer = face,
        viewProjection = viewProjection,
        resolvedDraws = draws,
    )
}
