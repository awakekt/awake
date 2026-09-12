/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import com.awakekt.awake.core.math.Mat4
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.passes.debug.lineSegmentVertices
import com.awakekt.awake.render.renderer.LineSegment
import io.ygdrasil.webgpu.GPURenderPassEncoder

/** 3D lowering helpers used by [RendererGpuPassExecutor]. The packet entrypoint is executor-owned;
 * all scene lowering has already happened before this file is reached. */

/** Stages this frame's world-space debug lines (e.g. a frustum wireframe) -- rewrites
 * [Renderer.lineMesh]'s buffer but issues no GPU commands itself, same "stage now, consume
 * on next draw" pattern as `performDrawUi`. Call before the packet draw each frame. */
/** One pass's context. Built per pass, not per frame -- a WebGPU pass encoder is only valid
 * inside the `beginRenderPass` that made it. */
internal fun Renderer.sceneContext(
    encoder: io.ygdrasil.webgpu.GPURenderPassEncoder,
    opaqueDraws: com.awakekt.awake.render.command.SortedDraws<out com.awakekt.awake.render.command.PreparedDraw>,
    primaryPipeline: PipelineHandle,
    viewProjection: Mat4,
    cameraEye: Vec3f,
    surface: SurfaceSize,
    environmentState: GpuEnvironmentState = GpuEnvironmentState.Default,
): WebGpuFrameContext = WebGpuFrameContext(
    renderer = this,
    encoder = encoder,
    groupedDrawCalls = opaqueDraws.opaqueByPipeline,
    transparentDrawCalls = opaqueDraws.transparent,
    primaryPipeline = primaryPipeline,
    viewProjection = viewProjection,
    cameraEye = cameraEye,
    environmentState = environmentState,
    surfaceWidth = surface.width,
    surfaceHeight = surface.height,
)

/** What a pass is drawing into, so [sceneContext] takes one argument for it rather than two. */
internal class SurfaceSize(val width: Int, val height: Int)

internal fun Renderer.performDrawDebugLines(lines: List<LineSegment>) {
    // No capacity guard: LineMesh grows to fit and enforces DebugLineLayout's ceiling itself.
    lineMesh.update(lineSegmentVertices(lines))
}
