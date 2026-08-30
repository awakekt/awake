/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.compose.ui.platform

/** Optional composition-only timing for one [ComposeHost]. */
class ComposeFrameStats {
    var enabled: Boolean = false

    var compositionPasses: Long = 0
        private set

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
