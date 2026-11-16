/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Desktop implementation of [FrameLoop] supporting window focus detection and background throttling.
 */
object DesktopFrameLoop : FrameLoop {
    /**
     * Whether the host window currently has input focus.
     */
    @Volatile
    var isWindowFocused: Boolean = true

    /**
     * Optional background frame rate cap applied when [isWindowFocused] is false.
     * When null, the caller-specified [FrameRateMode] is used directly without background throttling.
     */
    @Volatile
    var backgroundFrameRate: Int? = null

    /**
     * Optional explicit frame rate mode override set dynamically at runtime (e.g. by editor preferences).
     * When null, the caller-specified [FrameRateMode] is used.
     */
    @Volatile
    var frameRateOverride: FrameRateMode? = null

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

        val baseMode = frameRateOverride ?: mode
        val effectiveMode = if (!isWindowFocused && backgroundFrameRate != null) {
            FrameRateMode.Capped(backgroundFrameRate!!.coerceAtLeast(1))
        } else {
            baseMode
        }

        val pacer = FramePacing.pacer(effectiveMode, presentPacing = false)
        val sleepNanos = FramePacing.sleepNanos(effectiveMode, pacer, System.nanoTime() - currentFrameTime)
        if (sleepNanos > 0) {
            Thread.sleep(sleepNanos / NANOS_PER_MILLI, (sleepNanos % NANOS_PER_MILLI).toInt())
        }
    }

    override fun tick(onUpdate: (deltaTime: Double) -> Unit) {
        tick(FrameRateMode.Auto, onUpdate)
    }

    internal fun resetForTest() {
        isWindowFocused = true
        backgroundFrameRate = null
        frameRateOverride = null
        previousFrameTime = System.nanoTime()
    }

    private const val NANOS_PER_MILLI = 1_000_000L
}
