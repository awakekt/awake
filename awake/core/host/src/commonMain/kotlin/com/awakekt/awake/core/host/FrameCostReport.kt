/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * What the renderer tells the frame loop about the frame it just submitted, so the loop can tell a
 * frame's own work from its waits (#1699): how long the recording thread waited on fences and the
 * swapchain, and the GPU's time for the frame.
 *
 * A loop that times a frame sees the vsync wait inside it, so on a FIFO swapchain every frame seems
 * to cost a whole refresh. [SteadyFrameRate] needs the work alone. Written and read on the render
 * thread, as the frame loop and the renderer both run there: plain fields, no allocation.
 */
object FrameCostReport {
    private var waitNanos = 0L
    private var gpuNanos = 0L

    /** The renderer submitted a frame after waiting [waitedNanos]; its GPU took [gpuNanos], or 0 when not measured. */
    fun submitted(waitedNanos: Long, gpuNanos: Long) {
        waitNanos += waitedNanos.coerceAtLeast(0L)
        if (gpuNanos > 0L) this.gpuNanos = maxOf(this.gpuNanos, gpuNanos)
    }

    /** The renderer submitted a frame after waiting [waitedNanos]; its GPU took [gpuNanos], or null when not measured. */
    fun submitted(waitedNanos: Long, gpuNanos: Long?) {
        submitted(waitedNanos, gpuNanos ?: 0L)
    }

    /**
     * The cost of a frame whose loop iteration took [frameNanos]: the CPU's time less the waits the
     * renderer reported for it, or the GPU's time when that is longer. Clears the reports for the next
     * frame. With nothing reported, as with no renderer, the whole iteration counts.
     */
    fun takeCost(frameNanos: Long): Long {
        val cpu = (frameNanos - waitNanos).coerceAtLeast(0L)
        val cost = maxOf(cpu, gpuNanos)
        waitNanos = 0L
        gpuNanos = 0L
        return cost
    }
}
