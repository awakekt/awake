/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.renderer.RenderViewport

/** Per-frame, scene-free inputs used to lower [GpuDrawRequest] into a resolved draw. */
data class GpuDrawPreparationContext(
    val frameIndex: Int = 0,
    val viewProjection: Mat4,
    val cameraEye: Vec3f,
    val passUniforms: FloatArray = FloatArray(0),
    val environment: GpuEnvironmentState = GpuEnvironmentState.Default,
    val viewport: RenderViewport? = null,
    val shadowViewProjections: List<Mat4> = emptyList(),
)

/** Generic backend capability that prepares opaque draw requests into resolved GPU packets. */
fun interface GpuDrawPreparer {
    fun prepare(
        request: GpuDrawRequest,
        sourceIndex: Int,
        context: GpuDrawPreparationContext,
    ): GpuResolvedDraw?
}

/** Composition capability exposed by a backend bootstrap to scene/render-pipeline code. */
interface GpuDrawPreparationSource {
    val gpuDrawPreparer: GpuDrawPreparer?
}

/** Resolves a batch in source order; a null result deliberately removes that draw. */
fun GpuDrawPreparer.prepareAll(
    requests: List<GpuDrawRequest>,
    context: GpuDrawPreparationContext,
): List<GpuResolvedDraw> = buildList(requests.size) {
    requests.forEachIndexed { index, request ->
        prepare(request, index, context)?.let(::add)
    }
}
