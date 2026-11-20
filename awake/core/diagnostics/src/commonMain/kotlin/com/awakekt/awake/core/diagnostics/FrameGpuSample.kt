/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.diagnostics

/**
 * Where a frame's GPU time went, part by part in the order the frame records them, and how long the
 * CPU waited for the GPU and for the swapchain while recording it (#1709). Each part is `null` until
 * the device has reported it, and they add up to [FrameRenderSample.gpuTimeMs].
 *
 * A GPU-bound frame shows its excess in [frameResourceWaitMs], whichever phase the wait fell in.
 *
 * @property shadowDepthMs The directional shadow cascades' depth passes, and the frame's uploads before them.
 * @property sceneDepthMs The camera's own depth pass, when something reads scene depth.
 * @property maskMs The mask passes.
 * @property sceneMs The scene pass at the render scale.
 * @property postMs Image passes on the scene before it is scaled to the output.
 * @property upscaleMs Scaling the scene to the output, and the image passes drawn there.
 * @property uiMs The UI pass over the output.
 * @property frameResourceWaitMs CPU milliseconds blocked on fences for the GPU's earlier work.
 * @property acquireWaitMs CPU milliseconds blocked acquiring the next swapchain image.
 */
data class FrameGpuSample(
    val shadowDepthMs: Float? = null,
    val sceneDepthMs: Float? = null,
    val maskMs: Float? = null,
    val sceneMs: Float? = null,
    val postMs: Float? = null,
    val upscaleMs: Float? = null,
    val uiMs: Float? = null,
    val frameResourceWaitMs: Float = 0f,
    val acquireWaitMs: Float = 0f,
)
