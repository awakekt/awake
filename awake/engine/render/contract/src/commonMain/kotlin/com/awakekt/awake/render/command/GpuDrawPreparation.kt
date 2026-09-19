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
    val cameraForward: Vec3f,
    val shadowCascadeData: GpuShadowCascadeData?,
) {
    /** Retains the original constructor for callers that do not supply shadow-camera metadata. */
    constructor(
        frameIndex: Int = 0,
        viewProjection: Mat4,
        cameraEye: Vec3f,
        passUniforms: FloatArray = FloatArray(0),
        environment: GpuEnvironmentState = GpuEnvironmentState.Default,
        viewport: RenderViewport? = null,
        shadowViewProjections: List<Mat4> = emptyList(),
    ) : this(
        frameIndex,
        viewProjection,
        cameraEye,
        passUniforms,
        environment,
        viewport,
        shadowViewProjections,
        Vec3f(0f, 0f, -1f),
        null,
    )

    /** Retains the original data-class copy shape; newly-added shadow metadata is preserved. */
    fun copy(
        frameIndex: Int = this.frameIndex,
        viewProjection: Mat4 = this.viewProjection,
        cameraEye: Vec3f = this.cameraEye,
        passUniforms: FloatArray = this.passUniforms,
        environment: GpuEnvironmentState = this.environment,
        viewport: RenderViewport? = this.viewport,
        shadowViewProjections: List<Mat4> = this.shadowViewProjections,
    ): GpuDrawPreparationContext = GpuDrawPreparationContext(
        frameIndex,
        viewProjection,
        cameraEye,
        passUniforms,
        environment,
        viewport,
        shadowViewProjections,
        cameraForward,
        shadowCascadeData,
    )
}

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
