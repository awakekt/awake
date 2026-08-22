// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.render.passes

import io.github.ronjunevaldoz.awake.render.command.MaterialBinding
import io.github.ronjunevaldoz.awake.render.command.PipelineHandle
import io.github.ronjunevaldoz.awake.render.command.PreparedDraw
import io.github.ronjunevaldoz.awake.render.renderer.skyboxUniformFloats

/**
 * The three shared-pass features, written once for every backend.
 *
 * Each pairs a `Shared*RenderFeature` body with a small port a backend implements -- the same
 * split [io.github.ronjunevaldoz.awake.render.command.CommandRecorder] already uses, extended to
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

/** The backend half of [SkyboxRenderFeature]. */
interface SkyboxPass {
    fun writeUniforms(frameIndex: Int, uniforms: FloatArray)
    fun pipeline(frameIndex: Int): PipelineHandle
    fun uniformBinding(frameIndex: Int): MaterialBinding
    fun destroy()
}

/**
 * The backend half of [OpaqueRenderFeature]: the debug-line pipeline and its staged geometry.
 *
 * @param C The backend's frame-context type -- the staged line mesh is reached through that,
 * since it is per-frame state a `Renderer` owns rather than something this pass can hold.
 */
interface LinePass<in C : RenderFrameContext> {
    fun writeMvp(frameIndex: Int, mvp: FloatArray)

    /** This frame's staged lines, or `null` when none were staged. A zero-vertex draw is skipped
     * by [SharedOpaqueRenderFeature] anyway, so returning one is equally valid. */
    fun lineDraw(context: C): PreparedDraw?

    fun destroy()
}


/**
 * The procedural sky, drawn FIRST in the scene pass with depth test and write both off. Register
 * it before [OpaqueRenderFeature] so scene geometry still paints over it.
 *
 * Authored content, not a capability (docs/reference/render-extensibility.md): construct it only
 * when the app opted into a skybox shader set, and even then it draws nothing unless
 * [RenderFrameContext.showEnvironment] is on.
 */
class SkyboxRenderFeature(private val sky: SkyboxPass) : RenderFeature<RenderFrameContext> {
    override val pass = RenderPassSlot.Scene

    private val shared = SharedSkyboxRenderFeature()

    override fun recordCommands(context: RenderFrameContext): Unit = with(context) {
        if (!showEnvironment) return
        // Null when this frame's viewProjection can't be inverted -- nothing to draw a sky from.
        val uniforms = skyboxUniformFloats(
            viewProjection, cameraEye, light.direction, horizonColor, zenithColor,
        ) ?: return
        sky.writeUniforms(frameIndex, uniforms)
        shared.recordCommands(
            recorder = recorder,
            pipeline = sky.pipeline(frameIndex),
            uniformBinding = sky.uniformBinding(frameIndex),
        )
    }

    override fun destroy() = sky.destroy()
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
 */
class OpaqueRenderFeature<C : RenderFrameContext>(
    private val lines: LinePass<C>,
) : RenderFeature<C> {
    override val pass = RenderPassSlot.Scene

    private val shared = SharedOpaqueRenderFeature()
    private val transparent = SharedTransparentRenderFeature(shared)

    override fun recordCommands(context: C): Unit = with(context) {
        // Debug lines are already in world space (no per-line model matrix), so their MVP is
        // exactly this frame's viewProjection.
        lines.writeMvp(frameIndex, viewProjection.data)
        shared.recordCommands(
            recorder = recorder,
            primaryPipeline = primaryPipeline,
            grouped = groupedDrawCalls,
            lines = lines.lineDraw(context),
        )
        // After the opaque draws, in the same scene pass: a transparent surface must depth-test
        // against opaque geometry already written, and blend over the colour it left behind.
        // A separate RenderPassSlot would put it after the UI, which paints over the scene.
        transparent.recordCommands(transparentDrawCalls, recorder)
    }

    override fun destroy() = lines.destroy()
}

