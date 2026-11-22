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
import com.awakekt.awake.render.effects.effectsForAntiAliasing
import com.awakekt.awake.render.passes.DEFAULT_SHADOW_CASCADES
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.SceneDrawStats
import com.awakekt.awake.render.passes.ScenePassCompiler
import com.awakekt.awake.render.passes.ScenePassScratch
import com.awakekt.awake.render.passes.ShadowCasterVolume
import com.awakekt.awake.render.pipeline.CullMode
import com.awakekt.awake.render.pipeline.EffectQuality
import com.awakekt.awake.render.renderer.AntiAliasing
import com.awakekt.awake.render.renderer.RenderDrawStage
import com.awakekt.awake.render.renderer.RenderDrawStages
import com.awakekt.awake.render.renderer.RenderViewport
import com.awakekt.awake.render.renderer.ShadowBudget
import com.awakekt.awake.scene.rendering.debug.CullingProbe
import com.awakekt.awake.scene.rendering.debug.TransparentDrawSources
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
    /** The cascades and map size each frame plans a directional shadow with: the renderer's `shadowBudget` (#1688). */
    shadowBudget: () -> ShadowBudget = { ShadowBudget.Default },
    /** The renderer's anti-aliasing mode; [AntiAliasing.Fxaa] adds an FXAA stage to every camera (#1791). */
    private val antiAliasing: () -> AntiAliasing = { AntiAliasing.Off },
    /** The quality that FXAA stage runs at: the renderer's `effectQuality`. */
    private val effectQuality: () -> EffectQuality = { EffectQuality.High },
    /** Preparer for offscreen captures; defaults to [drawPreparer] when the backend shares one slot (#1687, #1709). */
    captureDrawPreparer: GpuDrawPreparer? = drawPreparer,
) {
    private val drawPreparer = RejectionCountingPreparer(
        requireNotNull(drawPreparer) {
            "SceneRenderPlanner3D requires a GpuDrawPreparer from the render-pipeline bootstrap"
        },
    )
    private val capturePreparer = if (captureDrawPreparer === drawPreparer || captureDrawPreparer == null) {
        this.drawPreparer
    } else {
        RejectionCountingPreparer(captureDrawPreparer)
    }
    private val geometryFeature = SceneGeometryFeature3D(rendererClipSpace)
    private val featureCollector = SceneFeatureCollector3D(rendererClipSpace, features, shadowBudget)
    private val drawCalls = ArrayList<RenderDrawCommand>()
    private val shadowCasters = ShadowCasterVolume()
    private val passScratch = ScenePassScratch()
    private val captureScratch = if (capturePreparer === this.drawPreparer) passScratch else ScenePassScratch()

    /** Whether a frame this planner prepared may be drawn again later; see `GpuDrawPreparer.redrawsPreparedFrames`. */
    val redrawsPreparedFrames: Boolean get() = drawPreparer.redrawsPreparedFrames

    val lastFrustumCulledCount: Int get() = geometryFeature.lastFrustumCulledCount
    val lastOccludedCount: Int get() = geometryFeature.lastOccludedCount
    val lastShadowOnlyCount: Int get() = geometryFeature.lastShadowOnlyCount

    /** See `RenderSystem3D.cullingProbe`. */
    var cullingProbe: CullingProbe?
        get() = geometryFeature.cullingProbe
        set(value) {
            geometryFeature.cullingProbe = value
        }

    /** See `RenderSystem3D.minShadowCasterRatio`. */
    var minShadowCasterRatio: Float
        get() = geometryFeature.minShadowCasterRatio
        set(value) {
            geometryFeature.minShadowCasterRatio = value
        }

    /** Told around [plan]'s collect and compile stages while set; [RenderSystem3D] sets it for its own frame only. */
    var stages: RenderDrawStages? = null

    /** Plans [camera]'s view at [aspect], into [viewport] when it fills only part of the target. */
    @Suppress("LongParameterList")
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
        transparentSources: TransparentDrawSources? = null,
        forCapture: Boolean = false,
    ): PlannedFrame {
        val stages = stages
        stages?.begin(RenderDrawStage.Collect)
        drawCalls.clear()
        transparentSources?.reset()
        // Features first, though their draws go after the opaque geometry: the light they settle
        // decides which meshes the camera culls are still drawn into the shadow maps.
        val contributions = featureCollector.collect(world, camera, elapsedTimeSeconds, aspect, transparentSources)
        shadowCasters.set(contributions.light, contributions.environment)
        val geometryFrame = geometryFeature.begin(world, camera, elapsedTimeSeconds, aspect, shadowCasters)
        drawCalls.append(geometryFrame.beforeParticles)
        drawCalls.append(contributions.particleDraws)
        val afterParticles = geometryFeature.finish(world, geometryFrame, camera)
        drawCalls.append(afterParticles)
        transparentSources?.addMeshRenderers(geometryFrame.beforeParticles)
        transparentSources?.addMeshRenderers(afterParticles)
        drawCalls.append(contributions.authoredDraws)

        val postPassChain = postPassChain(world, cameraEntity, targetWidth, targetHeight)

        stages?.end(RenderDrawStage.Collect)
        stages?.begin(RenderDrawStage.Compile)
        val activePreparer = if (forCapture) capturePreparer else drawPreparer
        val activeScratch = if (forCapture) captureScratch else passScratch
        val rejectedBefore = activePreparer.rejected
        activePreparer.rejected = 0
        val passInput = ScenePassCompiler.compile(
            lens = camera.lens,
            drawCalls = drawCalls,
            light = contributions.light,
            environment = contributions.environment,
            clipSpace = rendererClipSpace,
            aspect = aspect,
            viewport = viewport,
            drawPreparer = activePreparer,
            maskLayers = world.outlineMaskLayers(),
            postPassChain = postPassChain,
            drawStats = drawStats,
            scratch = activeScratch,
        )
        stages?.end(RenderDrawStage.Compile)
        return PlannedFrame(
            passInput = passInput,
            submittedDrawCalls = drawCalls.size,
            submittedInstances = drawCalls.sumOf { it.instanceModels?.size ?: 1 },
            frustumCulled = geometryFeature.lastFrustumCulledCount,
            occluded = geometryFeature.lastOccludedCount,
            // A frame handed back unchanged prepared nothing; its draws are the last frame's.
            unresolved = if (activeScratch.reusedLastFrame) rejectedBefore.also { activePreparer.rejected = it } else activePreparer.rejected,
            reused = activeScratch.reusedLastFrame,
        )
    }

    /**
     * The camera's post-processing chain: its own effects when its component is enabled, plus the
     * FXAA stage the renderer's anti-aliasing mode adds (#1791), or none at a zero-sized target.
     */
    private fun postPassChain(
        world: World,
        cameraEntity: com.awakekt.awake.ecs.Entity?,
        targetWidth: Int,
        targetHeight: Int,
    ): com.awakekt.awake.render.imagepass.ImagePassChain {
        val postProcessing = cameraEntity?.let { world.get<com.awakekt.awake.scene.rendering.postprocessing.PostProcessing>(it) }
        val cameraEffects = postProcessing?.takeIf { it.enabled }?.effects.orEmpty()
        val postEffects = effectsForAntiAliasing(cameraEffects, antiAliasing(), effectQuality())
        val hasDimensions = targetWidth > 0 && targetHeight > 0
        return if (postEffects.isNotEmpty() && hasDimensions) {
            com.awakekt.awake.render.effects.PostProcessingStack.buildChain(
                effects = postEffects,
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
    }

    /** This planner's feature names, as [TransparentDrawSources] lists them. */
    val featureNames: List<String> get() = featureCollector.featureNames

    internal data class PlannedFrame(
        val passInput: com.awakekt.awake.render.command.GpuPassInput,
        val submittedDrawCalls: Int,
        val submittedInstances: Int,
        val frustumCulled: Int,
        val occluded: Int,
        /** Draws the backend's preparer rejected, counted after instancing folded them. */
        val unresolved: Int,
        /** Whether nothing the compile reads changed, so the last frame's input was drawn again. */
        val reused: Boolean = false,
    )
}

/** Adds every one of [draws] by index: `addAll` copies its argument into a new array first. */
private fun ArrayList<RenderDrawCommand>.append(draws: List<RenderDrawCommand>) {
    for (index in draws.indices) add(draws[index])
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

    // Forwarded too, or the compiler sees the interface's defaults rather than the backend's.
    override val clampsShadowDepth: Boolean get() = delegate.clampsShadowDepth

    override val redrawsPreparedFrames: Boolean get() = delegate.redrawsPreparedFrames

    override fun canTintInstances(format: VertexFormat, cullMode: CullMode): Boolean = delegate.canTintInstances(format, cullMode)
}
