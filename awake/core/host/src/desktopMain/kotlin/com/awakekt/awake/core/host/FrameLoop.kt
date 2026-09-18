/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

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
        val deltaTime = rawDeltaTime.coerceAtMost(MAX_FRAME_DELTA_SECONDS)
        previousFrameTime = currentFrameTime

        onUpdate(deltaTime)

        val baseMode = frameRateOverride ?: mode
        val effectiveMode = if (!isWindowFocused && backgroundFrameRate != null) {
            FrameRateMode.Capped(backgroundFrameRate!!.coerceAtLeast(1))
        } else {
            baseMode
        }

        val frameWorkNanos = System.nanoTime() - currentFrameTime
        when (effectiveMode) {
            is FrameRateMode.Auto, FrameRateMode.Unlimited -> {
                // No artificial sleep throttling; display presentation / vsync controls cadence.
            }
            is FrameRateMode.Capped -> {
                val desiredFrameTimeNanos = 1_000_000_000L / effectiveMode.targetFps
                val sleepNanos = desiredFrameTimeNanos - frameWorkNanos
                if (sleepNanos > 0) {
                    val sleepMillis = sleepNanos / 1_000_000L
                    val sleepNanosRemainder = (sleepNanos % 1_000_000L).toInt()
                    Thread.sleep(sleepMillis, sleepNanosRemainder)
                }
            }
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
}
