/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import platform.posix.usleep
import kotlin.native.concurrent.ThreadLocal
import kotlin.time.TimeSource

/**
 * Mirrors [DesktopFrameLoop]/[AndroidFrameLoop]'s measure-delta/throttle contract using
 * [TimeSource.Monotonic], since `System.nanoTime()` is unavailable on Kotlin/Native.
 *
 * No live call site yet (iOS via MoltenVK is post-desktop). Implemented rather than left a
 * no-op stub, which would silently never render for whoever wires up the first iOS surface.
 * Not hardware-verified.
 */
@ThreadLocal
object IOSFrameLoop : FrameLoop {
    private var previousMark = TimeSource.Monotonic.markNow()

    override fun tick(
        mode: FrameRateMode,
        onUpdate: (deltaTime: Double) -> Unit,
    ) {
        val currentMark = TimeSource.Monotonic.markNow()
        val rawDeltaTime = (currentMark - previousMark).inWholeNanoseconds / 1e9
        val deltaTime = rawDeltaTime.coerceAtMost(MAX_FRAME_DELTA_SECONDS)
        previousMark = currentMark

        onUpdate(deltaTime)

        val workMicros = (TimeSource.Monotonic.markNow() - currentMark).inWholeMicroseconds
        when (mode) {
            is FrameRateMode.Auto, FrameRateMode.Unlimited -> {}
            is FrameRateMode.Capped -> {
                val desiredFrameTimeMicros = 1_000_000L / mode.targetFps
                val sleepTimeMicros = desiredFrameTimeMicros - workMicros
                if (sleepTimeMicros > 0) {
                    usleep(sleepTimeMicros.toUInt())
                }
            }
        }
    }

    override fun tick(onUpdate: (deltaTime: Double) -> Unit) {
        tick(FrameRateMode.Auto, onUpdate)
    }
}
