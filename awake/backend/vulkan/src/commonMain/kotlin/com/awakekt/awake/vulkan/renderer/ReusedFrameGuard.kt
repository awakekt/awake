/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import com.awakekt.awake.vulkan.Vulkan

/**
 * Whether a batch about to be prepared must wait for every frame slot, not only its own (#1555).
 *
 * A prepared draw binds the uniforms of the frame slot it was prepared in. When nothing changed, the
 * planner hands the last prepared frame back, and it is submitted again from whichever slot is
 * current, still reading the slot it was prepared in. The next batch prepared into that slot would
 * then rewrite uniforms a reused frame in another slot may still be reading, and waiting on the
 * current slot's own fence does not cover that frame. So the first batch after any frame went out
 * unprepared waits for all of them; a frame prepared every time costs nothing extra.
 */
internal class ReusedFrameGuard {
    /** Frames submitted, prepared or not. */
    private var submitted = 0L

    /** [submitted] when the last batch was prepared, or -1 before any. */
    private var preparedAt = NEVER

    /** A frame was submitted. */
    fun frameSubmitted() {
        submitted++
    }

    /**
     * A batch is being prepared. True when a frame went out since the last prepared one without
     * being prepared: a reused frame, which the caller must let finish before writing any slot.
     */
    fun preparing(): Boolean {
        // The last prepared frame is submitted once; anything past that was a reused one.
        val reusedSince = preparedAt != NEVER && submitted - preparedAt > 1
        preparedAt = submitted
        return reusedSince
    }

    private companion object {
        const val NEVER = -1L
    }
}

/** Waits until no frame slot is referenced by the GPU: a reused frame may read any of them. */
internal fun Renderer.waitForAllFrameResourceSlots() {
    val fences = swapchainManager.inFlightFences.filter { it != 0L }
    if (fences.isNotEmpty()) Vulkan.vkWaitForFences(device, fences.toLongArray(), true, Long.MAX_VALUE)
}
