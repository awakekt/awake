/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

object AndroidFrameLoop : FrameLoop {
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

        val frameWorkNanos = System.nanoTime() - currentFrameTime
        when (mode) {
            is FrameRateMode.Auto, FrameRateMode.Unlimited -> {
                // Display vsync / presentation controls cadence.
            }
            is FrameRateMode.Capped -> {
                val desiredFrameTimeNanos = 1_000_000_000L / mode.targetFps
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
}
