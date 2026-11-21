/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * A part of a presented frame's GPU work, in the order the frame records them.
 *
 * Each part is timed from where the part before it ended to where it ends, so the parts add up to
 * [RenderFrameStats.gpuTimeMs] for the frame's own command buffer. A part the frame does not draw
 * reads close to zero.
 */
enum class GpuFramePart {
    /** The directional shadow cascades' depth passes, and the frame's uploads recorded before them. */
    ShadowDepth,

    /** The camera's own depth pass, recorded when something reads scene depth (water, fog). */
    SceneDepth,

    /** The mask passes, when something is masked. */
    Mask,

    /** The scene pass: opaque and transparent draws, at the render scale. */
    Scene,

    /** Image passes on the scene before it is scaled to the output. */
    Post,

    /**
     * Scaling the scene to the output, and the image passes drawn there. On a scaled Vulkan frame it
     * shares one render pass with [Ui] (#1709); read [FrameGpuTiming.upscaleAndUiMs] there.
     */
    Upscale,

    /**
     * The UI pass over the output, and its transition to present. On a scaled Vulkan frame it is drawn
     * in the same render pass as [Upscale].
     */
    Ui,
}

/**
 * Where the last presented frame's GPU time went, part by part, and how long the CPU waited for the
 * GPU and for the swapchain while recording it (#1709).
 *
 * The GPU parts are timestamps the device wrote between the parts, so they include any time the GPU
 * spent waiting inside the frame: a pass that writes colour waits for the acquired swapchain image,
 * and a tile-based GPU may overlap one pass's work with the next. They lag the frame by the frames in
 * flight, as [RenderFrameStats.gpuTimeMs] does.
 *
 * The waits are CPU time on the thread that records the frame:
 * - [frameResourceWaitMs]: blocked on the fence of a frame slot's previous GPU work before reusing its
 *   resources. A GPU-bound frame shows its excess here, wherever the wait falls, in the render
 *   systems or the UI phase.
 * - [acquireWaitMs]: blocked acquiring the next swapchain image, which the present mode and the
 *   display's pace bound.
 */
class FrameGpuTiming(
    private val partMs: FloatArray,
    val frameResourceWaitMs: Float,
    val acquireWaitMs: Float,
) {
    init {
        require(partMs.size == GpuFramePart.entries.size) { "one reading per GpuFramePart, not ${partMs.size}" }
    }

    /** Milliseconds the GPU spent on [part], or null when the device has not reported it yet. */
    fun partMs(part: GpuFramePart): Float? = partMs[part.ordinal].takeUnless { it.isNaN() }

    /**
     * [GpuFramePart.Upscale] and [GpuFramePart.Ui] together, or null when either is not reported yet.
     *
     * A scaled Vulkan frame draws its upscale and its UI in one render pass on the output (#1709), and
     * the timestamp between them is written inside that pass. A tile-based GPU runs a render pass's
     * work tile by tile, not in the order it was recorded, so there the split between the two parts
     * says little and their sum is the reading to use.
     */
    val upscaleAndUiMs: Float?
        get() {
            val upscale = partMs(GpuFramePart.Upscale) ?: return null
            val ui = partMs(GpuFramePart.Ui) ?: return null
            return upscale + ui
        }

    override fun toString(): String =
        "FrameGpuTiming(" + GpuFramePart.entries.joinToString { "${it.name}=${partMs(it)}" } +
            ", frameResourceWaitMs=$frameResourceWaitMs, acquireWaitMs=$acquireWaitMs)"
}
