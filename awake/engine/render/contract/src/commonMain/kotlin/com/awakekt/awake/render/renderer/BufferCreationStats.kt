/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.renderer

/**
 * GPU buffers the draw path created during one submitted frame, by the site that created them.
 *
 * The draw path pools its buffers, so a warm frame reads zero everywhere. A figure that stays above
 * zero names the pool that is not holding: every buffer is a driver allocation, and on the web a
 * wrapper object besides. The first frames of a scene read above zero while the pools fill.
 *
 * @property argumentBuffers Indirect-argument buffers of retained render bundles. Zero on a backend
 * that keeps no bundles.
 * @property instanceBuffers Per-instance model buffers of instanced draws, counted when one is
 * made and each time one grows.
 * @property uniformBuffers Per-draw uniform buffers.
 */
data class BufferCreationStats(
    val argumentBuffers: Int,
    val instanceBuffers: Int,
    val uniformBuffers: Int,
) {
    /** Buffers created across every site. */
    val total: Int get() = argumentBuffers + instanceBuffers + uniformBuffers
}
