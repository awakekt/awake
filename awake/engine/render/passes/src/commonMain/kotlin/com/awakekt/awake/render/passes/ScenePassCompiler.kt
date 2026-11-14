/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.ClipSpace
import com.awakekt.awake.core.math.Lens
import com.awakekt.awake.core.math.Vec3f
import com.awakekt.awake.render.command.GpuDrawPreparationContext
import com.awakekt.awake.render.command.GpuDrawPreparer
import com.awakekt.awake.render.command.GpuPassInput
import com.awakekt.awake.render.command.GpuResolvedDraw
import com.awakekt.awake.render.command.GpuShadowCascadeData
import com.awakekt.awake.render.command.GpuSubPass
import com.awakekt.awake.render.passes.RenderDrawCommand
import com.awakekt.awake.render.passes.uniforms.DEFAULT_SCENE_LIGHT
import com.awakekt.awake.render.passes.uniforms.EnvironmentUniforms
import com.awakekt.awake.render.passes.uniforms.SceneLight
import com.awakekt.awake.render.passes.uniforms.sceneLightUniforms
import com.awakekt.awake.render.passes.uniforms.shadowCascades
import com.awakekt.awake.render.renderer.RenderViewport

/**
 * Shared scene-to-HAL compiler. Scene vocabulary ends at this boundary; backends receive only
 * [GpuPassInput] and must not repeat camera, light, sorting, or pass-planning policy.
 */
object ScenePassCompiler {
    /**
     * Compiles scene render requests and camera parameters into an immutable backend-neutral [GpuPassInput].
     *
     * @param lens Camera lens specifying projection and view parameters.
     * @param drawCalls List of draw commands to prepare and sort.
     * @param light Optional primary directional/scene light source.
     * @param environment Environment and atmospheric parameters.
     * @param clipSpace Target graphics API clip space coordinates.
     * @param aspect Viewport aspect ratio (width / height).
     * @param viewport Optional viewport bounding configuration.
     * @param drawPreparer Optional backend-provided GPU draw preparer.
     * @param maskLayers The mask pass's layers, by index: each one's uniforms, which its sub-pass
     *   carries to whatever samples the layer. A draw whose [RenderDrawCommand.maskLayer] names one
     *   is also drawn into it; a layer no draw names has no sub-pass.
     * @param postPassChain Image-pass chain run over the finished scene colour, such as a camera's
     *   post-processing stack; [ImagePassChain.Empty] runs nothing.
     * @return Prepared [GpuPassInput] containing subpasses and uniform blocks ready for recording.
     */
    fun compile(
        lens: Lens,
        drawCalls: List<RenderDrawCommand>,
        light: SceneLight? = null,
        environment: EnvironmentUniforms = EnvironmentUniforms.Default,
        clipSpace: ClipSpace,
        aspect: Float,
        viewport: RenderViewport? = null,
        drawPreparer: GpuDrawPreparer? = null,
        maskLayers: List<FloatArray> = emptyList(),
        postPassChain: com.awakekt.awake.render.imagepass.ImagePassChain = com.awakekt.awake.render.imagepass.ImagePassChain.Empty,
    ): GpuPassInput = compile(lens, drawCalls, light, environment, clipSpace, aspect, viewport, drawPreparer, maskLayers, postPassChain, null)

    /**
     * [compile], also counting into [drawStats], when it is not null, the draws each pass receives
     * and why main-pass draws did not fold into instances. Null counts nothing and costs nothing.
     * [drawStats] is reset first, so it describes this frame alone.
     */
    @Suppress("LongParameterList") // The overload above's parameters, and the stats.
    fun compile(
        lens: Lens,
        drawCalls: List<RenderDrawCommand>,
        light: SceneLight?,
        environment: EnvironmentUniforms,
        clipSpace: ClipSpace,
        aspect: Float,
        viewport: RenderViewport?,
        drawPreparer: GpuDrawPreparer?,
        maskLayers: List<FloatArray>,
        postPassChain: com.awakekt.awake.render.imagepass.ImagePassChain,
        drawStats: SceneDrawStats?,
    ): GpuPassInput = compile(
        lens, drawCalls, light, environment, clipSpace, aspect, viewport, drawPreparer, maskLayers, postPassChain, drawStats,
        ScenePassScratch(),
    )

    /**
     * [compile], folding instances in [scratch]'s memory, which a caller keeps from frame to frame
     * so a frame like the last one allocates nothing to fold. One scratch serves one caller.
     */
    @Suppress("LongParameterList") // The overload above's parameters, and the scratch.
    fun compile(
        lens: Lens,
        drawCalls: List<RenderDrawCommand>,
        light: SceneLight?,
        environment: EnvironmentUniforms,
        clipSpace: ClipSpace,
        aspect: Float,
        viewport: RenderViewport?,
        drawPreparer: GpuDrawPreparer?,
        maskLayers: List<FloatArray>,
        postPassChain: com.awakekt.awake.render.imagepass.ImagePassChain,
        drawStats: SceneDrawStats?,
        scratch: ScenePassScratch,
    ): GpuPassInput {
        // Recorded only where a frame may be drawn again: elsewhere comparing would buy nothing.
        val redraws = drawPreparer?.redrawsPreparedFrames == true
        if (!redraws) scratch.signature.clear()
        val unchanged = redraws &&
            scratch.signature.record(lens, drawCalls, light, environment, clipSpace, aspect, viewport, maskLayers, postPassChain)
        val last = scratch.lastInput
        scratch.reusedLastFrame = unchanged && last != null && drawStats === scratch.lastStats
        if (scratch.reusedLastFrame && last != null) return last
        drawStats?.reset()
        return compileFrame(
            lens, drawCalls, light, environment, clipSpace, aspect, viewport, drawPreparer, maskLayers, postPassChain, drawStats, scratch,
        ).also {
            scratch.lastInput = it
            scratch.lastStats = drawStats
        }
    }

    @Suppress("LongParameterList") // [compile]'s.
    private fun compileFrame(
        lens: Lens,
        drawCalls: List<RenderDrawCommand>,
        light: SceneLight?,
        environment: EnvironmentUniforms,
        clipSpace: ClipSpace,
        aspect: Float,
        viewport: RenderViewport?,
        drawPreparer: GpuDrawPreparer?,
        maskLayers: List<FloatArray>,
        postPassChain: com.awakekt.awake.render.imagepass.ImagePassChain,
        drawStats: SceneDrawStats?,
        scratch: ScenePassScratch,
    ): GpuPassInput {
        val viewProjection = lens.viewProjectionMatrix(aspect, clipSpace)
        val cameraForward = lens.forwardDirection()
        // A backend that clamps shadow depth flattens a caster in front of a cascade's near plane
        // onto it; for one that clips there, the plane moves back to the casters instead. Before
        // preparation, because prepared draws carry the cascade matrices their receivers sample.
        val shadowCascadeData = shadowCascadeData(light, environment)?.let { cascades ->
            if (drawPreparer?.clampsShadowDepth == true) cascades else cascades.reachingCasters(drawCalls, clipSpace)
        }
        val shadowViewProjections = shadowCascadeData?.viewProjections.orEmpty()
        val packedPassUniforms = sceneLightUniforms(light ?: DEFAULT_SCENE_LIGHT, lens.eye).packed
        val gpuEnvironment = environment.toGpuState(viewDepthRange = lens.far)

        // One preparation pass in source order, so every draw keeps its own source index; the
        // shadow-only ones are then kept out of the scene and handed to the shadow passes. A draw
        // that does not cast stays out of the shadow passes, whichever way it was drawn.
        val draws = scratch.draws
        draws.reset(maskLayers)
        var edges = emptyList<GpuResolvedDraw>()
        if (drawPreparer != null) {
            val context = GpuDrawPreparationContext(
                viewProjection = viewProjection,
                cameraEye = lens.eye,
                cameraForward = cameraForward,
                passUniforms = packedPassUniforms,
                environment = gpuEnvironment,
                viewport = viewport,
                shadowViewProjections = shadowViewProjections,
                shadowCascadeData = shadowCascadeData,
            )
            val requests = foldInstances(drawCalls, drawPreparer, drawStats, scratch.batcher)
            // Folded again by what a depth pass reads, when something casts: tinted copies cast as one.
            scratch.casters.fold(requests, light != null && environment.shadowsEnabled, scratch.batcher.canInstanceWith(drawPreparer))
            draws.prepare(requests, drawPreparer, context, scratch.casters)
            if (environment.wireframe) edges = drawPreparer.prepareEdges(requests, context)
        }
        val prePasses = draws.shadowPrePasses(shadowViewProjections, drawPreparer != null, light, environment)
        val maskPasses = draws.maskSubPasses(viewProjection)
        drawStats?.recordPasses(prePasses, draws.opaque.size, draws.transparent.size, maskPasses, edges.size)
        return GpuPassInput(
            prePasses = prePasses,
            viewProjection = viewProjection,
            cameraEye = lens.eye,
            viewport = viewport,
            passUniforms = packedPassUniforms,
            environment = gpuEnvironment,
            resolvedOpaqueDraws = draws.opaque,
            resolvedTransparentDraws = draws.transparent,
            resolvedPath = drawPreparer != null,
            cameraForward = cameraForward,
            shadowCascadeData = shadowCascadeData,
            resolvedEdgeDraws = edges,
            maskPasses = maskPasses,
            postPassChain = postPassChain,
        ).also {
            // Sorted above, once: the executors record these lists as they are.
            it.drawsInRecordingOrder = drawPreparer != null
        }
    }
}

/**
 * [drawCalls] with their copies folded into instanced draws as [preparer] can draw them, after
 * counting into [stats], when there are any, what folds and why the rest does not.
 */
private fun foldInstances(
    drawCalls: List<RenderDrawCommand>,
    preparer: GpuDrawPreparer,
    stats: SceneDrawStats?,
    batcher: InstanceBatcher,
): List<RenderDrawCommand> {
    val canInstance = batcher.canInstanceWith(preparer)
    val canTint = batcher.canTintWith(preparer)
    if (stats != null) diagnoseInstanceFolds(drawCalls, canInstance, stats, canTint)
    return batcher.batch(drawCalls, canInstance, canTint)
}

/** Counts the draws each pass of a compiled frame receives. */
private fun SceneDrawStats.recordPasses(prePasses: List<GpuSubPass>, opaque: Int, transparent: Int, maskPasses: List<GpuSubPass>, edges: Int) {
    prePasses.forEach { recordShadowPass(it.resolvedDraws.size) }
    recordMainPass(opaque, transparent, maskPasses.sumOf { it.resolvedDraws.size }, edges)
}

private fun Lens.forwardDirection(): Vec3f = (center - eye).let { direction ->
    val length = direction.length3()
    if (length > 0.0001f) direction.scale(1f / length) else Vec3f(0f, 0f, -1f)
}

/** The directional cascades a frame renders from [light], or null when it renders none. */
internal fun shadowCascadeData(
    light: SceneLight?,
    environment: EnvironmentUniforms,
): GpuShadowCascadeData? =
    if (light != null && environment.shadowsEnabled) light.shadowCascades() else null
