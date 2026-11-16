/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.device.GraphicsDevice
import com.awakekt.awake.vulkan.gen.VulkanImages

/**
 * Samplers a texture replaced for a new [Renderer.maxAnisotropy], kept until no work still recorded
 * against them can run (#1660). A frame recorded before the change may still be in flight with the
 * old sampler bound, so one is destroyed only once every frame slot has been submitted again after it
 * was retired -- each slot's fence is waited before it records -- and offscreen work submitted before
 * it has finished.
 */
internal class RetiredSamplers(private val graphicsDevice: GraphicsDevice) {
    private var handles = LongArray(INITIAL)
    private var retiredAt = LongArray(INITIAL)
    private var count = 0

    /** Frames submitted so far; a retired sampler waits until this has moved past its frame slots. */
    var framesSubmitted = 0L
        private set

    /** Keeps [sampler] until it is safe to destroy; 0 is no sampler. */
    fun retire(sampler: Long) {
        if (sampler == 0L) return
        if (count == handles.size) {
            handles = handles.copyOf(count * 2)
            retiredAt = retiredAt.copyOf(count * 2)
        }
        handles[count] = sampler
        retiredAt[count] = framesSubmitted
        count++
    }

    /**
     * Counts a submitted frame and destroys every sampler retired more than [framesInFlight] frames
     * ago, after [awaitOffscreen] lets offscreen work recorded before the change finish. Nothing
     * retired, as on almost every frame, is one comparison.
     */
    fun frameSubmitted(framesInFlight: Int, awaitOffscreen: () -> Unit) {
        framesSubmitted++
        if (count == 0 || framesSubmitted - retiredAt[0] <= framesInFlight) return
        awaitOffscreen()
        var kept = 0
        for (index in 0 until count) {
            if (framesSubmitted - retiredAt[index] > framesInFlight) {
                VulkanImages.vkDestroySampler(graphicsDevice.device, handles[index])
            } else {
                handles[kept] = handles[index]
                retiredAt[kept] = retiredAt[index]
                kept++
            }
        }
        count = kept
    }

    /** Destroys every retired sampler; the caller has waited for the device to go idle. */
    fun destroyAll() {
        for (index in 0 until count) VulkanImages.vkDestroySampler(graphicsDevice.device, handles[index])
        count = 0
    }

    /** Retired samplers not yet destroyed, for tests. */
    val pending: Int get() = count

    private companion object {
        const val INITIAL = 8
    }
}
