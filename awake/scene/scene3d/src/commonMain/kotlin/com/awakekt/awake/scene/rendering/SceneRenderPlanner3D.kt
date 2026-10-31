/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.scene.rendering

import com.awakekt.awake.core.geometry.VertexFormat
import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.ecs.World
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuDrawRequest
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.SceneDrawStats
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.scene.rendering.outline.outlineMaskLayers

/**
 * Renderer-free 3D scene extraction and pass planning.
 *
 * The planner owns scene policy and deterministic draw ordering. It only produces the generic
 * render packet consumed by a renderer; frame acquisition, submission, and presentation stay in
 * [RenderSystem3D].
 */
internal class SceneRenderPlanner3D(
    private val rendererClipSpace: ClipSpace,
    drawPreparer: GpuDrawPreparer?,
    features: List<RenderFeature3D> = emptyList(),
) {
    private val drawPreparer = RejectionCountingPreparer(
        requireNotNull(drawPreparer) {
            "SceneRenderPlanner3D requires a GpuDrawPreparer from the render-pipeline bootstrap"
        },
    )
    private val geometryFeature = SceneGeometryFeature3D(rendererClipSpace)
    private val featureCollector = SceneFeatureCollector3D(rendererClipSpace, features)
    private val drawCalls = ArrayList<RenderDrawCommand>()
    private val shadowCasters = ShadowCasterVolume()

    val lastFrustumCulledCount: Int get() = geometryFeature.lastFrustumCulledCount
    val lastOccludedCount: Int get() = geometryFeature.lastOccludedCount
    val lastShadowOnlyCount: Int get() = geometryFeature.lastShadowOnlyCount

    /** See `RenderSystem3D.minShadowCasterRatio`. */
    var minShadowCasterRatio: Float
        get() = geometryFeature.minShadowCasterRatio
        set(value) {
            geometryFeature.minShadowCasterRatio = value
        }

    /** Plans [camera]'s view at [aspect], into [viewport] when it fills only part of the target. */
    fun plan(
        world: World,
        camera: Camera,
        elapsedTimeSeconds: Float,
        aspect: Float,
        viewport: RenderViewport?,
        cameraEntity: com.awakekt.awake.ecs.Entity? = null,
        targetWidth: Int = 0,
        targetHeight: Int = 0,
        drawStats: SceneDrawStats? = null,
    ): PlannedFrame {
        drawCalls.clear()
        // Features first, though their draws go after the opaque geometry: the light they settle
        // decides which meshes the camera culls are still drawn into the shadow maps.
        val contributions = featureCollector.collect(world, camera, elapsedTimeSeconds, aspect)
        shadowCasters.set(contributions.light, contributions.environment)
        val geometryFrame = geometryFeature.begin(world, camera, elapsedTimeSeconds, aspect, shadowCasters)
        drawCalls += geometryFrame.beforeParticles
        drawCalls += contributions.particleDraws
        drawCalls += geometryFeature.finish(world, geometryFrame, camera)
        drawCalls += contributions.authoredDraws

        val postProcessing = cameraEntity?.let { world.get<com.awakekt.awake.scene.rendering.postprocessing.PostProcessing>(it) }
        val hasPostEffects = postProcessing != null && postProcessing.enabled && postProcessing.effects.isNotEmpty()
        val hasDimensions = targetWidth > 0 && targetHeight > 0
        val postPassChain = if (hasPostEffects && hasDimensions) {
            com.awakekt.awake.render.effects.PostProcessingStack.buildChain(
                effects = postProcessing.effects,
                targetContext = com.awakekt.awake.render.imagepass.ImagePassTargetContext(
                    width = targetWidth,
                    height = targetHeight,
                    clipSpace = rendererClipSpace,
                    // Every lit shader applies exposure, the tone curve and the encode itself, so
                    // the camera's colour is display-ready; ScenePostProcessing.validate relies on it.
                    colorEncoding = com.awakekt.awake.render.imagepass.ImagePassColorEncoding.DisplayReferred,
                ),
            )
        } else {
            com.awakekt.awake.render.imagepass.ImagePassChain.Empty
        }

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
            maskLayers = world.outlineMaskLayers(),
            postPassChain = postPassChain,
            drawStats = drawStats,
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

    // Forwarded, or the interface's default answers for unculled copies only, and copies of a
    // back-culled prop never reach the back-culled instanced pipeline the backend built for them.
    override fun canInstance(format: VertexFormat, cullMode: CullMode): Boolean = delegate.canInstance(format, cullMode)
}
