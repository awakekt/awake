/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * Snapshot of live GPU resources currently tracked by a
 * [com.awakekt.awake.render.pipeline.GpuDevice].
 *
 * Built for leak detection in tests, soak runs, and runtime diagnostics: creating meshes,
 * materials, textures, or render targets increases the matching counts and bytes, and
 * destroying them returns the counters to baseline. Calling `destroy()` on the renderer
 * clears all backend-owned textures, render targets, and pooled buffers.
 *
 * Reading this property is pull-only so steady-state frames allocate nothing.
 */
data class GpuResourceStats(
    /** Live caller-owned static and dynamic meshes not yet destroyed. */
    val liveMeshes: Int = 0,
    /** Live caller-owned materials not yet destroyed. */
    val liveMaterials: Int = 0,
    /** Live sampled GPU textures currently registered with the renderer (including cached neutral and font atlases). */
    val liveTextures: Int = 0,
    /** Live offscreen render targets not yet destroyed. */
    val liveRenderTargets: Int = 0,
    /** Live pooled dynamic GPU buffers (UI meshes, instance buffers, and per-draw uniform slots) held by the renderer. */
    val livePooledBuffers: Int = 0,
    /** Total GPU buffer bytes currently accounted to [liveMeshes]. */
    val liveMeshBytes: Long = 0L,
    /** Estimated GPU memory bytes currently accounted to [liveTextures]. */
    val estimatedTextureBytes: Long = 0L,
) {
    /** Sum of all tracked live GPU resource handles. */
    val totalLiveResources: Int
        get() = liveMeshes + liveMaterials + liveTextures + liveRenderTargets + livePooledBuffers
}
