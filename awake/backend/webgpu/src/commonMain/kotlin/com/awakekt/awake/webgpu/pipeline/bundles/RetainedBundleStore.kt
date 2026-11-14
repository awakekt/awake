/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.pipeline.bundles

import io.ygdrasil.webgpu.GPUDevice

/**
 * Every pass's retained bundles, by the pass's own key: the target it draws into, or a shadow
 * cascade's layer of the depth target. A pass whose layout changed starts over, and one no longer
 * drawn (a destroyed or resized target) is released after [STALE_AFTER] passes have run since.
 */
internal class RetainedBundleStore(private val device: GPUDevice) {
    private val passes = HashMap<Any, RetainedPassBundles>()
    private var uses = 0L

    /** Passes with bundles kept: for tests. */
    val passCount: Int get() = passes.size

    fun pass(key: Any, layout: BundleLayout): RetainedPassBundles {
        uses += 1
        if (uses % STALE_AFTER == 0L) evictStale()
        val existing = passes[key]
        val pass = if (existing != null && existing.layout == layout) {
            existing
        } else {
            existing?.destroy()
            RetainedPassBundles(device, layout).also { passes[key] = it }
        }
        pass.lastUse = uses
        return pass
    }

    fun destroy() {
        passes.values.forEach(RetainedPassBundles::destroy)
        passes.clear()
    }

    private fun evictStale() {
        val each = passes.values.iterator()
        while (each.hasNext()) {
            val pass = each.next()
            if (uses - pass.lastUse >= STALE_AFTER) {
                pass.destroy()
                each.remove()
            }
        }
    }

    private companion object {
        /** About fifty frames of a shadowed scene: four passes or so a frame. */
        const val STALE_AFTER = 256L
    }
}
