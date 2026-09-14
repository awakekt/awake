/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes.debug

import com.awakekt.awake.render.pipeline.CullMode

/**
 * Backend-neutral meaning of the engine's debug-line pipeline.
 *
 * The Vulkan and WebGPU descriptors cannot be shared: they use different native types and are
 * created by different device APIs. Their semantic inputs can be shared, however. Keeping this
 * policy beside [DebugLineLayout] prevents a backend from silently changing an editor overlay into
 * scene geometry by inheriting a default cull or depth mode.
 */
object DebugLinePipelinePolicy {
    /** Debug lines are editor overlays and must not be rejected by scene depth. */
    val depthMode: DebugLineDepthMode = DebugLineDepthMode.AlwaysVisible

    /** Line lists have no triangle faces to cull. */
    val cullMode: CullMode = CullMode.None
}

/** Logical depth behavior that each backend translates into its own pipeline descriptor. */
enum class DebugLineDepthMode {
    /** The overlay is visible even when its projected segment overlaps scene geometry. */
    AlwaysVisible,
}
