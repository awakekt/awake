/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.passes

/**
 * A grow-on-demand pool of GPU resources addressed by a per-frame index.
 *
 * The frame path asks for "the mesh for run 3" or "the buffer for primitive 7"; the pool creates
 * one the first time that index is reached and hands back the same instance every frame after.
 * Nothing shrinks: a frame that once needed N keeps N allocated, on the assumption that a scene's
 * complexity is roughly stable and reallocating per frame is the thing being avoided.
 *
 * The policy is here rather than in each backend because it was previously written out eight
 * times per backend, identical apart from the resource being constructed.
 *
 * Not thread-safe, and deliberately so -- every caller is on the render thread inside one frame.
 *
 * @param T The pooled resource type.
 * @param create Builds one resource. Called once per index, never per frame.
 */
class GpuResourcePool<T>(private val create: () -> T) {
    private val items = mutableListOf<T>()

    /** How many resources have actually been created -- for teardown and tests, not the frame path. */
    val size: Int get() = items.size

    /** The resource at [index], creating it and any lower-indexed gaps on first use. */
    operator fun get(index: Int): T {
        while (items.size <= index) items += create()
        return items[index]
    }

    /** Runs [action] over every created resource, for teardown. */
    fun forEach(action: (T) -> Unit) {
        var index = 0
        while (index < items.size) {
            action(items[index])
            index += 1
        }
    }
}
