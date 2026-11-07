/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.ui.layout

/**
 * Frame counters, off by default and free when off.
 *
 * [intrinsicQueries] exists because an intrinsic costs an extra tree walk and Compose never says
 * when you paid for one. `awake-ui-performance` Rule 2 is about exactly this: intrinsics are the
 * extra walk, and they are silent, so the counter is how a caller sees what they cost. Turning it
 * on is opt-in, and while it is off the record call is a single branch.
 */
object LayoutStats {
    /** Whether counting is on. While it is `false`, [recordIntrinsicQuery] does nothing. */
    var enabled: Boolean = false

    var intrinsicQueries: Int = 0
        private set

    /** Counts one intrinsic query toward [intrinsicQueries], if [enabled]. */
    fun recordIntrinsicQuery() {
        if (enabled) intrinsicQueries++
    }

    /** Sets [intrinsicQueries] back to zero. */
    fun reset() {
        intrinsicQueries = 0
    }
}
