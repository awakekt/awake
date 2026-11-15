/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

/**
 * GPU buffers the per-frame draw path created, by site, over a renderer's life: for tests and probes.
 *
 * Every buffer is a new wgpu4k `Buffer`, whose web wrapper builds a logger by capturing and parsing
 * a JS stack trace, so a warm frame should create none at any site (#1555). Retained bundles'
 * argument buffers are counted in their store's `BundleCounts.argsBuffersCreated`.
 */
internal class GpuBufferCreations {
    /** Instanced draws' model buffers: one per pooled `InstanceBuffer`, and one each time it grows. */
    var instanceBuffers = 0L

    /** Per-draw uniform buffers, one per new `uniformSlotForDraw` slot. */
    var uniformSlots = 0L
}
