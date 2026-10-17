/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ai

import kotlinx.serialization.KSerializer

/**
 * A behaviour node or state that keeps something between ticks, such as a child index or a timer,
 * and can hand it over as data so a running tree or machine can be saved and restored.
 *
 * The state is a typed value with its own [stateSerializer]; snapshots store it as JSON. A node
 * that keeps nothing between ticks does not implement this.
 *
 * @param S The type of the kept state.
 */
interface Snapshottable<S : Any> {
    /** Writes and reads [S]. */
    val stateSerializer: KSerializer<S>

    /** What this keeps now. */
    fun captureState(): S

    /**
     * Puts back [state], taken by [captureState] on an equal node or state.
     *
     * @throws IllegalArgumentException when [state] cannot belong to this one, such as a child index
     *   past its children.
     */
    fun restoreState(state: S)
}
