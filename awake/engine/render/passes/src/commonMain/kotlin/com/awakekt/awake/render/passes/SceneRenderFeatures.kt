/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

import com.awakekt.awake.core.color.Color
import com.awakekt.awake.core.math.inverse
import com.awakekt.awake.render.command.GpuEnvironmentState
import com.awakekt.awake.render.command.PipelineHandle
import com.awakekt.awake.render.command.PreparedDraw
import com.awakekt.awake.render.command.UniformBlock
import com.awakekt.awake.render.renderer.MOON_DISC_COLOR
import com.awakekt.awake.render.renderer.RenderPassKind
import com.awakekt.awake.render.renderer.SUN_DISC_COLOR
import com.awakekt.awake.render.renderer.SkyboxFields
import com.awakekt.awake.render.texture.TextureUploadRecorder

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
            put(SkyboxFields.SunColor, if (environment.skyBodies) SUN_DISC_COLOR else Color.Black)
            put(SkyboxFields.MoonColor, if (environment.skyBodies) MOON_DISC_COLOR else Color.Black)
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
 * @param lines The backend's debug-line pipeline and staged geometry.
 * @param beforeTransparent Content features recorded after opaque geometry and before transparent scene draws.
 * @param opaqueGeometry Content features recorded after opaque scene meshes (e.g. terrain) so meshes reject occluded terrain via Early-Z.
 * @param afterOpaque Content features recorded after opaque geometry and terrain (e.g. skybox and celestial bodies with `LESS_EQUAL` depth test).
 */
class OpaqueRenderFeature<C : RenderFrameContext>(
    private val lines: LinePass<C>,
    private val beforeTransparent: List<RenderFeature<C>>,
    private val opaqueGeometry: List<RenderFeature<C>> = emptyList(),
    private val afterOpaque: List<RenderFeature<C>> = emptyList(),
) : RenderFeature<C> {
    constructor(lines: LinePass<C>) : this(lines, emptyList(), emptyList(), emptyList())

    @Deprecated("Kept for binary compatibility", level = DeprecationLevel.HIDDEN)
    constructor(
        lines: LinePass<C>,
        beforeTransparent: List<RenderFeature<C>>,
    ) : this(lines, beforeTransparent, emptyList(), emptyList())

    override val pass = RenderPassSlot.Scene

    private val shared = SharedOpaqueRenderFeature()
    private val transparent = SharedTransparentRenderFeature(shared)

    override fun prepareFrame(frameIndex: Int, uploads: TextureUploadRecorder) {
        for (i in opaqueGeometry.indices) opaqueGeometry[i].prepareFrame(frameIndex, uploads)
        for (i in afterOpaque.indices) afterOpaque[i].prepareFrame(frameIndex, uploads)
        for (i in beforeTransparent.indices) beforeTransparent[i].prepareFrame(frameIndex, uploads)
    }

    override fun needsCoveringExemptMask(environment: GpuEnvironmentState): Boolean =
        anyNeedsCoveringExemptMask(opaqueGeometry, environment) ||
            anyNeedsCoveringExemptMask(afterOpaque, environment) ||
            anyNeedsCoveringExemptMask(beforeTransparent, environment)

    override fun needsSceneDepth(environment: GpuEnvironmentState): Boolean =
        anyNeedsSceneDepth(opaqueGeometry, environment) ||
            anyNeedsSceneDepth(afterOpaque, environment) ||
            anyNeedsSceneDepth(beforeTransparent, environment)

    private fun anyNeedsCoveringExemptMask(features: List<RenderFeature<C>>, environment: GpuEnvironmentState): Boolean {
        for (i in features.indices) {
            if (features[i].needsCoveringExemptMask(environment)) return true
        }
        return false
    }

    private fun anyNeedsSceneDepth(features: List<RenderFeature<C>>, environment: GpuEnvironmentState): Boolean {
        for (i in features.indices) {
            if (features[i].needsSceneDepth(environment)) return true
        }
        return false
    }

    override fun recordCommands(context: C): Unit = with(context) {
        // Debug lines are already in world space (no per-line model matrix), so their MVP is
        // exactly this frame's viewProjection.
        lines.writeMvp(frameIndex, viewProjection.data)
        stats?.countAs(RenderPassKind.SceneOpaque)
        // Opaque meshes draw first so terrain pixels hidden behind buildings, props, and
        // characters are hardware-rejected by Early-Z before running terrain fragment fetches.
        shared.recordCommands(
            recorder = recorder,
            primaryPipeline = primaryPipeline,
            grouped = groupedDrawCalls,
        )
        for (i in opaqueGeometry.indices) opaqueGeometry[i].recordCommands(this)
        // Over the opaque draws, against the depth they wrote, and before anything blends over them.
        shared.recordEdges(recorder, edgeDrawCalls)
        // Far-plane skyboxes and celestial bodies draw after all opaque geometry with LESS_EQUAL
        // depth testing so Early-Z rejects every sky pixel covered by terrain or meshes.
        for (i in afterOpaque.indices) afterOpaque[i].recordCommands(this)
        // World-space blended surfaces (such as water and cloud sheets) paint after opaque geometry
        // and sky, and before transparent scene draws so particles and splashes blend over them.
        for (i in beforeTransparent.indices) beforeTransparent[i].recordCommands(this)
        // After the opaque draws, in the same scene pass: a transparent surface must depth-test
        // against opaque geometry already written, and blend over the colour it left behind.
        // A separate RenderPassSlot would put it after the UI, which paints over the scene.
        stats?.countAs(RenderPassKind.SceneTransparent)
        transparent.recordCommands(transparentDrawCalls, recorder)
        stats?.countAs(RenderPassKind.Other)
        // Last in the scene: debug lines are an overlay over every surface, a skeleton inside its
        // skinned mesh included. Recorded earlier, a later pipeline group painted over them.
        shared.recordLines(recorder, lines.lineDraw(context))
    }

    override fun destroy() {
        lines.destroy()
        for (i in opaqueGeometry.indices) opaqueGeometry[i].destroy()
        for (i in afterOpaque.indices) afterOpaque[i].destroy()
        for (i in beforeTransparent.indices) beforeTransparent[i].destroy()
    }
}
