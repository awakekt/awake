// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.compose.ui.layout

/**
 * Frame counters, off by default and free when off.
 *
 * [intrinsicQueries] exists because an intrinsic costs an extra tree walk and Compose never says
 * when you paid for one. `awake-ui-performance` Rule 4's whole lesson is that an expensive path
 * which is silent when unarmed is the one that ships wrong -- `cacheKey` was exactly that. Same
 * zero-cost-when-disabled contract `UiMeasureTrialStats` already has.
 */
object LayoutStats {
    var enabled: Boolean = false

    var intrinsicQueries: Int = 0
        private set

    fun recordIntrinsicQuery() {
        if (enabled) intrinsicQueries++
    }

    fun reset() {
        intrinsicQueries = 0
    }
}
