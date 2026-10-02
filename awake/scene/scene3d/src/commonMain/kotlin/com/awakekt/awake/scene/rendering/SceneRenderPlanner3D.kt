/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.ecs.World
import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.scene.rendering.terrain.TerrainShadowCasters

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
    private val terrainCasters: TerrainShadowCasters? = null,
) {
    private val drawPreparer = RejectionCountingPreparer(
        requireNotNull(drawPreparer) {
            "SceneRenderPlanner3D requires a GpuDrawPreparer from the render-pipeline bootstrap"
        },
    )
    private val geometryFeature = SceneGeometryFeature3D(rendererClipSpace)
    private val featureCollector = SceneFeatureCollector3D(rendererClipSpace, features)
    private val drawCalls = ArrayList<RenderDrawCommand>()

    val lastFrustumCulledCount: Int get() = geometryFeature.lastFrustumCulledCount
    val lastOccludedCount: Int get() = geometryFeature.lastOccludedCount

    fun plan(world: World, camera: Camera, elapsedTimeSeconds: Float): PlannedFrame {
        val viewport = rendererViewport()
        val aspect = viewport?.aspect ?: rendererAspect()
        drawCalls.clear()
        val geometryFrame = geometryFeature.begin(world, camera, elapsedTimeSeconds)
        drawCalls += geometryFrame.beforeParticles
        val contributions = featureCollector.collect(world, camera, elapsedTimeSeconds, aspect)
        drawCalls += contributions.particleDraws
        drawCalls += geometryFeature.finish(world, geometryFrame, camera)
        terrainCasters?.collect(world, drawCalls)
        drawCalls += contributions.authoredDraws

        drawPreparer.rejected = 0
        val passInput = ScenePassCompiler.compile(
            lens = camera.lens,
            drawCalls = drawCalls,
            light = contributions.light,
            environment = contributions.environment,
            clipSpace = rendererClipSpace,
            aspect = aspect,
            viewport = viewport,
            drawPreparer = drawPreparer,
        )
        return PlannedFrame(
            passInput = passInput,
            submittedDrawCalls = drawCalls.size,
            submittedInstances = drawCalls.sumOf { it.instanceModels?.size ?: 1 },
            frustumCulled = geometryFeature.lastFrustumCulledCount,
            occluded = geometryFeature.lastOccludedCount,
            unresolved = drawPreparer.rejected,
        )
    }

    internal data class PlannedFrame(
        val passInput: com.awakekt.awake.render.command.GpuPassInput,
        val submittedDrawCalls: Int,
        val submittedInstances: Int,
        val frustumCulled: Int,
        val occluded: Int,
        /** Draws the backend's preparer rejected, counted after instancing folded them. */
        val unresolved: Int,
    )
}

/**
 * Counts the requests [delegate] rejects.
 *
 * Counted here because the requests it sees are the ones instancing produced. Comparing the
 * extracted draws with the resolved ones instead reported every entity folded into a batch as
 * unresolved.
 */
private class RejectionCountingPreparer(private val delegate: GpuDrawPreparer) : GpuDrawPreparer {
    var rejected = 0

    override fun prepare(
        request: GpuDrawRequest,
        sourceIndex: Int,
        context: GpuDrawPreparationContext,
    ): GpuResolvedDraw? = delegate.prepare(request, sourceIndex, context).also { if (it == null) rejected += 1 }

    override fun canInstance(format: VertexFormat): Boolean = delegate.canInstance(format)
}
