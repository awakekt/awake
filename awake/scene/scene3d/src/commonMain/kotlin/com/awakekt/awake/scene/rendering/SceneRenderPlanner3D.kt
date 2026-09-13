/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.renderer.RenderViewport

/**
 * Renderer-free 3D scene extraction and pass planning.
 *
 * The planner owns scene policy and deterministic draw ordering. It only produces the generic
 * render packet consumed by a renderer; frame acquisition, submission, and presentation stay in
 * [RenderSystem3D].
 */
internal class SceneRenderPlanner3D(
    private val rendererClipSpace: ClipSpace,
    private val rendererAspect: () -> Float,
    private val rendererViewport: () -> RenderViewport?,
    drawPreparer: GpuDrawPreparer?,
    features: List<RenderFeature3D> = emptyList(),
) {
    private val drawPreparer: GpuDrawPreparer = requireNotNull(drawPreparer) {
        "SceneRenderPlanner3D requires a GpuDrawPreparer from the render-pipeline bootstrap"
    }
    private val geometryFeature = SceneGeometryFeature3D(rendererClipSpace)
    private val featureCollector = SceneFeatureCollector3D(rendererClipSpace, features)
    private val drawCalls = ArrayList<RenderDrawCommand>()

    val lastFrustumCulledCount: Int get() = geometryFeature.lastFrustumCulledCount
    val lastOccludedCount: Int get() = geometryFeature.lastOccludedCount

    fun plan(world: World, camera: Camera, elapsedTimeSeconds: Float): PlannedFrame {
        drawCalls.clear()
        val geometryFrame = geometryFeature.begin(world, camera, elapsedTimeSeconds)
        drawCalls += geometryFrame.beforeParticles
        val contributions = featureCollector.collect(world, camera, elapsedTimeSeconds)
        drawCalls += contributions.particleDraws
        drawCalls += geometryFeature.finish(world, geometryFrame, camera)
        drawCalls += contributions.authoredDraws

        val viewport = rendererViewport()
        val passInput = ScenePassCompiler.compile(
            lens = camera.lens,
            drawCalls = drawCalls,
            light = contributions.light,
            environment = contributions.environment,
            clipSpace = rendererClipSpace,
            aspect = viewport?.aspect ?: rendererAspect(),
            viewport = viewport,
            drawPreparer = drawPreparer,
        )
        return PlannedFrame(
            passInput = passInput,
            submittedDrawCalls = drawCalls.size,
            submittedInstances = drawCalls.sumOf { it.instanceModels?.size ?: 1 },
            frustumCulled = geometryFeature.lastFrustumCulledCount,
            occluded = geometryFeature.lastOccludedCount,
        )
    }

    internal data class PlannedFrame(
        val passInput: com.awakekt.awake.render.command.GpuPassInput,
        val submittedDrawCalls: Int,
        val submittedInstances: Int,
        val frustumCulled: Int,
        val occluded: Int,
    )
}
