/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.render.passes

import io.github.awakelab.awake.core.color.Color
import io.github.awakelab.awake.core.math.Mat4
import io.github.awakelab.awake.core.math.Vec3f
import io.github.awakelab.awake.render.command.CommandRecorder
import io.github.awakelab.awake.render.command.MaterialBinding
import io.github.awakelab.awake.render.command.PipelineHandle
import io.github.awakelab.awake.render.command.PreparedDraw
import io.github.awakelab.awake.render.renderer.SceneLight

/**
 * Which of the two render passes a feature's commands are valid inside. Not a feature
 * *hierarchy*: both slots share one [RenderFeature] interface, this only says which pass a
 * backend must have begun before calling it.
 *
 * The shadow pass is absent on purpose -- it owns its own render pass and is not a
 * [RenderFeature] at all. Add a third slot only when a second standalone-pass feature actually
 * exists, not on spec.
 */
enum class RenderPassSlot {
    Scene,
    Ui,
}

/**
 * Everything a [RenderFeature] may see of the frame being recorded -- deliberately narrow, and
 * carrying no `Renderer` behavior: nothing here can drive a frame, only read what one already
 * staged.
 *
 * This is the backend-neutral floor. A backend whose features need more (its own command-buffer
 * handle, lazily built UI pipelines, a pooled mesh allocator) extends this with its own
 * sub-interface and parameterizes [RenderFeature] on it -- see that type's [C].
 */
interface RenderFrameContext {
    val frameIndex: Int

    /** Computed once per frame by whoever builds this context -- every scene feature needs the
     * same grouping, so it is not recomputed per feature. */
    val groupedDrawCalls: Map<out PipelineHandle, List<PreparedDraw>>

    /**
     * This frame's transparent draws, already sorted back-to-front against [cameraEye].
     *
     * A flat list, not grouped by pipeline: grouping exists to batch state changes, and batching
     * reorders. Transparency needs the order preserved, so it trades that batching away. Empty in
     * the common case, which costs one iteration of nothing.
     */
    val transparentDrawCalls: List<PreparedDraw>
        get() = emptyList()
    val primaryPipeline: PipelineHandle

    /** This frame's camera matrices and light, for features that write their own pipeline's
     * uniform block (debug lines, sky) before drawing with it. */
    val viewProjection: Mat4
    val cameraEye: Vec3f
    val light: SceneLight

    val showEnvironment: Boolean
    val horizonColor: Color
    val zenithColor: Color

    /** The surface this pass draws into, for scissor clamping. */
    val surfaceWidth: Int
    val surfaceHeight: Int

    /** Already aimed at this frame's pass -- a feature records through this rather than issuing
     * graphics calls itself, which is what lets a feature body be shared across backends. */
    val recorder: CommandRecorder

    /**
     * This frame's camera-space depth group for [pipeline], or null when there is nothing to
     * bind -- the app opted into no scene-depth pass, or the backend binds it without being asked.
     *
     * Per pipeline because a WebGPU bind group is built against one pipeline's layout; Vulkan
     * returns null and binds its own set when the pipeline is bound, exactly as
     * [PreparedDraw.sceneDepthBinding] is null there. A feature calls this and binds whatever
     * comes back, which is correct on both.
     */
    fun sceneDepthBinding(pipeline: PipelineHandle): MaterialBinding? = null
}

/**
 * One unit of drawing inside a render pass someone else begins and ends. Registration order in a
 * backend's feature list is paint order within a slot.
 *
 * [C] is contravariant so a feature written against the neutral [RenderFrameContext] alone
 * slots into any backend's list, while a feature needing backend specifics declares that
 * backend's own context type and only fits there.
 */
interface RenderFeature<in C : RenderFrameContext> {
    val pass: RenderPassSlot

    fun recordCommands(context: C)

    fun destroy()
}

/**
 * Records every feature registered for [slot], in order, into a pass the caller has begun.
 *
 * @param C The backend's frame-context type.
 * @param features The backend's ordered feature list.
 * @param slot Which pass is currently open.
 * @param context This frame's context.
 */
fun <C : RenderFrameContext> recordPassFeatures(
    features: List<RenderFeature<C>>,
    slot: RenderPassSlot,
    context: C,
) {
    var index = 0
    while (index < features.size) {
        val feature = features[index]
        if (feature.pass == slot) feature.recordCommands(context)
        index += 1
    }
}
