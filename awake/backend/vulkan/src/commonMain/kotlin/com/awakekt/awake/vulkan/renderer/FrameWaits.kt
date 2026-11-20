/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.vulkan.renderer

import kotlin.time.TimeSource

/**
 * How long the recording thread waited during a frame: on fences for the GPU to finish a frame
 * slot's or an image's earlier work, and in acquiring the next swapchain image (#1709). Summed over
 * the frame, published when it is submitted. Reads the monotonic clock only; nothing is allocated.
 */
internal class FrameWaits {
    internal var fenceNanos = 0L
    internal var acquireNanos = 0L

    /** The last submitted frame's fence waits, in milliseconds. */
    var lastFenceMs = 0f
        private set

    /** The last submitted frame's swapchain acquire, in milliseconds. */
    var lastAcquireMs = 0f
        private set

    /** Runs [block], a fence wait, and adds its time to the frame's. */
    inline fun <T> fence(block: () -> T): T {
        val start = TimeSource.Monotonic.markNow()
        try {
            return block()
        } finally {
            fenceNanos += start.elapsedNow().inWholeNanoseconds
        }
    }

    /** Runs [block], an image acquire, and adds its time to the frame's. */
    inline fun <T> acquire(block: () -> T): T {
        val start = TimeSource.Monotonic.markNow()
        try {
            return block()
        } finally {
            acquireNanos += start.elapsedNow().inWholeNanoseconds
        }
    }

    /** Closes the frame's sums and starts the next frame's. */
    fun publish() {
        lastFenceMs = fenceNanos / NANOS_PER_MS
        lastAcquireMs = acquireNanos / NANOS_PER_MS
        fenceNanos = 0L
        acquireNanos = 0L
    }

    private companion object {
        const val NANOS_PER_MS = 1_000_000f
    }
}
