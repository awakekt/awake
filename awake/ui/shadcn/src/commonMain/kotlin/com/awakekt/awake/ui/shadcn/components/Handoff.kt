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
 * A value an input handler leaves for the next composition to take (a click, a chosen row).
 *
 * Input runs after the frame composed, so a component records what happened here and consumes it in
 * its next pass. A host that skips unchanged passes must still run that pass, so [take] makes the
 * pass depend on this handoff and [offer] invalidates it. The observable state behind that is created
 * by the first [take] made by a pass that tracks reads, so a host that composes every frame allocates
 * and records nothing (#1512).
 */
internal class Handoff<T : Any> {
    private var pending: T? = null
    private var version: MutableState<Int>? = null

    /** Leaves [value] for the next [take]. */
    fun offer(value: T) {
        pending = value
        version?.let { it.value += 1 }
    }

    /** What was last offered, once, or null. Call it from composition. */
    fun take(): T? {
        if (readsAreTracked) (version ?: mutableStateOf(0).also { version = it }).value
        return pending.also { pending = null }
    }
}
