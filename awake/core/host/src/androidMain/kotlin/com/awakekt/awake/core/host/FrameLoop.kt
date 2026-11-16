/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Android implementation of [FrameLoop] using `System.nanoTime()` and thread sleeping.
 *
 * A cap is slept out here unless the present call paces the frame ([presentPacing]); see
 * [FramePacing.pacer]. The mode a frame runs at is [frameRateOverride] when set, as on desktop.
 */
object AndroidFrameLoop : FrameLoop {
    /**
     * Optional explicit frame rate mode set at runtime (a debug option, a settings screen), read each
     * frame. When null, the caller-specified [FrameRateMode] is used.
     */
    @Volatile
    var frameRateOverride: FrameRateMode? = null

    /**
     * Whether the renderer's present call holds each frame to the mode's interval, as a frame-pacing
     * library does. While true the loop does not also sleep, so a frame is never paced twice.
     */
    @Volatile
    var presentPacing: Boolean = false

    private var previousFrameTime = System.nanoTime()

    override fun tick(
        mode: FrameRateMode,
        onUpdate: (deltaTime: Double) -> Unit,
    ) {
        val currentFrameTime = System.nanoTime()
        val rawDeltaTime = (currentFrameTime - previousFrameTime) / 1e9
        val deltaTime = clampFrameDelta(rawDeltaTime)
        previousFrameTime = currentFrameTime

        onUpdate(deltaTime)

        val effectiveMode = frameRateOverride ?: mode
        val pacer = FramePacing.pacer(effectiveMode, presentPacing)
        val sleepNanos = FramePacing.sleepNanos(effectiveMode, pacer, System.nanoTime() - currentFrameTime)
        if (sleepNanos > 0) {
            Thread.sleep(sleepNanos / NANOS_PER_MILLI, (sleepNanos % NANOS_PER_MILLI).toInt())
        }
    }

    override fun tick(onUpdate: (deltaTime: Double) -> Unit) {
        tick(FrameRateMode.Auto, onUpdate)
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
