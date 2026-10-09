/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.platform

/** Optional composition-only timing for one [ComposeHost]. */
class ComposeFrameStats {
    /** Whether [ComposeHost.frame] times its composition pass. Off by default. */
    var enabled: Boolean = false

    /** Composition passes timed since construction or the last [reset]. */
    var compositionPasses: Long = 0
        private set

    /** Nanoseconds spent in the timed composition passes since construction or the last [reset]. */
    var compositionNanos: Long = 0
        private set

    /** Reset between a warmup and a measured window without reallocating the host. */
    fun reset() {
        compositionPasses = 0
        compositionNanos = 0
    }

    internal fun recordComposition(nanos: Long) {
        compositionPasses += 1
        compositionNanos += nanos
    }
}
