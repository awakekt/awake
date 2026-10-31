/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.renderer.MOON_DISC_COLOR
import com.awakekt.awake.render.renderer.SUN_DISC_COLOR
import com.awakekt.awake.render.renderer.SkyboxFields

/**
 * The three shared-pass features, written once for every backend.
 *
 * Each pairs a `Shared*RenderFeature` body with a small port a backend implements -- the same
 * split [com.awakekt.awake.render.command.CommandRecorder] already uses, extended to
 * cover the *wiring* around a body and not just the draw calls inside it. Without this the two
 * backends each carried their own near-identical feature class, differing only in whether a
 * resource is frame-indexed.
 *
 * Every port method takes `frameIndex` even though only Vulkan uses it: its GPU resources are
 * per-frame-in-flight and WebGPU's are single-buffered. A backend that does not need it ignores
 * the parameter, which is honest about the difference rather than pushing it back into two
 * copies of the caller.
 *
 * The shadow pass is deliberately absent -- it owns its own render pass on both backends, so it
 * stays each backend's own class with its own signature.
 */

/**
 * The backend half of [OpaqueRenderFeature]: the debug-line pipeline and its staged geometry.
 *
 * @param C The backend's frame-context type -- the staged line mesh is reached through that,
 * since it is per-frame state a `Renderer` owns rather than something this pass can hold.
 */
interface LinePass<in C : RenderFrameContext> {
    /** Writes the model-view-projection matrix for debug lines into uniform storage for [frameIndex]. */
    fun writeMvp(frameIndex: Int, mvp: FloatArray)

    /** This frame's staged lines, or `null` when none were staged. A zero-vertex draw is skipped
     * by [SharedOpaqueRenderFeature] anyway, so returning one is equally valid. */
    fun lineDraw(context: C): PreparedDraw?

    /** Releases any resources allocated for debug line rendering. */
    fun destroy()
}

/**
 * The procedural sky, drawn FIRST in the scene pass with depth test and write both off. Register
 * it before [OpaqueRenderFeature] so scene geometry still paints over it.
 *
 * Authored content, not a capability (docs/reference/render-extensibility.md): construct it only
 * when the app opted into a skybox shader set, and even then it draws nothing unless
 * [RenderFrameContext.environment]'s [GpuEnvironmentState.showSky] flag is on.
 */
class SkyboxRenderFeature(
    private val pipeline: PipelineHandle,
    private val uniforms: UniformBlock,
) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    private val shared = SharedSkyboxRenderFeature()

    override fun recordCommands(context: RenderFrameContext): Unit = with(context) {
        if (!environment.showSky) return
        // Null when this frame's viewProjection can't be inverted -- nothing to draw a sky from.
        val inverse = viewProjection.inverse() ?: return
        uniforms.write(frameIndex) {
            put(SkyboxFields.InverseViewProjection, inverse)
            put(SkyboxFields.CameraEye, cameraEye)
            put(SkyboxFields.SunDirection, light.direction)
            put(SkyboxFields.HorizonColor, environment.horizonColor)
            put(SkyboxFields.ZenithColor, environment.zenithColor)
            put(SkyboxFields.SunColor, SUN_DISC_COLOR)
            put(SkyboxFields.MoonColor, MOON_DISC_COLOR)
        }
        shared.recordCommands(
            recorder = recorder,
            pipeline = pipeline,
            uniformBinding = uniforms.binding(frameIndex),
        )
    }

    /** Nothing: the registry owns the pipeline it built, and the block lives on it. */
    override fun destroy() = Unit
}

/**
 * The scene pass's geometry: the primary pipeline's draws, then debug lines, then every other
 * resolved pipeline's group -- that order lives in [SharedOpaqueRenderFeature].
 *
 * A capability, not authored content: always present, and draws nothing of its own when a frame
 * has no draw calls and no staged lines.
 *
 * @param C The backend's frame-context type.
 * @param lines The backend's debug-line pipeline and staged geometry. Owned here: [destroy] frees it.
 * @param defersOverCoverings Leaves the [RenderFrameContext.overCoveringDrawCalls], the wireframe
 * edges, the transparent draws and the debug lines to an [OverCoveringsRenderFeature] sharing
 * [lines], recorded after the covering content features. False draws them all here, in that
 * order after the opaque groups, for a host with no coverings.
 */
class OpaqueRenderFeature<C : RenderFrameContext>(
    private val lines: LinePass<C>,
    private val defersOverCoverings: Boolean = false,
) : RenderFeature<C> {
    override val pass = RenderPassSlot.Scene

    private val shared = SharedOpaqueRenderFeature()
    private val transparent = SharedTransparentRenderFeature(shared)

    /** [OpaqueRenderFeature] as it was before `defersOverCoverings`: hidden, and kept so a consumer compiled against it still links. */
    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(lines: LinePass<C>) : this(lines, defersOverCoverings = false)

    override fun recordCommands(context: C): Unit = with(context) {
        // Debug lines are already in world space (no per-line model matrix), so their MVP is
        // exactly this frame's viewProjection.
        lines.writeMvp(frameIndex, viewProjection.data)
        shared.recordCommands(
            recorder = recorder,
            primaryPipeline = primaryPipeline,
            grouped = groupedDrawCalls,
        )
        if (defersOverCoverings) return
        shared.recordSceneOverlays(context, transparent, lines)
    }

    override fun destroy() = lines.destroy()
}

/**
 * What follows the opaque groups in the scene pass, in this order: the over-coverings draws (an
 * unlit material, opaque, against the depth the opaque draws wrote), the wireframe edges over every
 * opaque surface, the transparent draws back to front, blending over all of it, and last the debug
 * lines, an overlay over every surface -- a skeleton inside its skinned mesh included.
 *
 * All in the same scene pass: a separate RenderPassSlot would put them after the UI, which paints
 * over the scene.
 */
internal fun <C : RenderFrameContext> SharedOpaqueRenderFeature.recordSceneOverlays(
    context: C,
    transparent: SharedTransparentRenderFeature,
    lines: LinePass<C>,
): Unit = with(context) {
    val late = overCoveringDrawCalls
    if (late.isNotEmpty()) recordCommands(recorder = recorder, primaryPipeline = primaryPipeline, grouped = late)
    recordEdges(recorder, edgeDrawCalls)
    transparent.recordCommands(transparentDrawCalls, recorder)
    recordLines(recorder, lines.lineDraw(context))
}
