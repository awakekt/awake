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
    private val desiredFrameTimeMicros = (1_000_000L / TARGET_FPS)
    private var previousMark = TimeSource.Monotonic.markNow()

    override fun tick(onUpdate: (deltaTime: Double) -> Unit) {
        val currentMark = TimeSource.Monotonic.markNow()
        val elapsedNanos = (currentMark - previousMark).inWholeNanoseconds

        onUpdate(elapsedNanos / 1e9)

        val sleepTimeMicros = desiredFrameTimeMicros - elapsedNanos / 1_000L
        if (sleepTimeMicros > 0) {
            usleep(sleepTimeMicros.toUInt())
        }

        previousMark = currentMark
    }
}
