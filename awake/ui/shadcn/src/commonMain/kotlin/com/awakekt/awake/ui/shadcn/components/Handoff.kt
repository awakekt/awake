/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ui.shadcn.components

import com.awakekt.awake.compose.runtime.MutableState
import com.awakekt.awake.compose.runtime.mutableStateOf
import com.awakekt.awake.compose.runtime.readsAreTracked

/**
 * What a component that keeps input-driven fields in plain properties needs so a host that skips
 * unchanged passes (#1512) still runs the pass that consumes them: the pass calls [trackRead] and
 * input calls [trackChange] after each write.
 *
 * The observable state behind them is created by the first [trackRead] made by a pass that tracks
 * reads, so a host that composes every frame allocates and records nothing, and a holder pays one
 * reference.
 */
internal open class ChangeSource {
    private var version: MutableState<Int>? = null

    fun trackRead() {
        if (readsAreTracked) (version ?: mutableStateOf(0).also { version = it }).value
    }

    fun trackChange() {
        version?.let { it.value += 1 }
    }
}

/**
 * A value an input handler leaves for the next composition to take (a click, a chosen row).
 *
 * Input runs after the frame composed, so a component records what happened here and consumes it in
 * its next pass: [take] makes that pass depend on this handoff and [offer] invalidates it.
 */
internal class Handoff<T : Any> : ChangeSource() {
    private var pending: T? = null

    /** Leaves [value] for the next [take]. */
    fun offer(value: T) {
        pending = value
        trackChange()
    }

    /** What was last offered, once, or null. Call it from composition. */
    fun take(): T? {
        trackRead()
        return pending.also { pending = null }
    }
}
