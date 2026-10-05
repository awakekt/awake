/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.RenderViewport

/** Per-frame, scene-free inputs used to lower [GpuDrawRequest] into a resolved draw. */
data class GpuDrawPreparationContext(
    /** Current monotonically increasing frame index. */
    val frameIndex: Int = 0,
    /** View-projection matrix of the active camera. */
    val viewProjection: Mat4,
    /** World-space position of the camera eye. */
    val cameraEye: Vec3f,
    /** Shared pass-level uniform float values uploaded for this pass. */
    val passUniforms: FloatArray = FloatArray(0),
    /** Environment lighting, sky, and fog configuration. */
    val environment: GpuEnvironmentState = GpuEnvironmentState.Default,
    /** Viewport rectangle bounds, or `null` to use the target dimensions. */
    val viewport: RenderViewport? = null,
    /** View-projection matrices for directional shadow cascades. */
    val shadowViewProjections: List<Mat4> = emptyList(),
    /** World-space forward viewing direction vector of the camera. */
    val cameraForward: Vec3f,
    /** Shadow cascade parameters and matrix data, or `null` if shadows are disabled. */
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
    /**
     * Prepares and lowers an individual [request] into a resolved backend draw packet.
     *
     * @return The resolved draw packet, or `null` if the request was culled or dropped.
     */
    fun prepare(
        request: GpuDrawRequest,
        sourceIndex: Int,
        context: GpuDrawPreparationContext,
    ): GpuResolvedDraw?

    /** Whether [prepare] can draw a request's copies of a [format] mesh as one instanced draw. */
    fun canInstance(format: VertexFormat): Boolean = false

    /** [canInstance] for copies drawn with [cullMode]; only unculled copies unless a backend says more. */
    fun canInstance(format: VertexFormat, cullMode: CullMode): Boolean = cullMode == CullMode.None && canInstance(format)
}

/** Composition capability exposed by a backend bootstrap to scene/render-pipeline code. */
interface GpuDrawPreparationSource {
    /** The backend draw preparer instance, or `null` if preparation is not supported. */
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
