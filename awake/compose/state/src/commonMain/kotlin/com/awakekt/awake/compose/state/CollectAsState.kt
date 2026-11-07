/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.state

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.PolledState
import com.awakekt.awake.compose.runtime.State
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.core.state.Store
import kotlinx.coroutines.flow.StateFlow

/**
 * A [State] reading a [StateFlow]'s current value. A composition that read it is invalid once the
 * flow holds a different value. Nothing collects the flow: the value is read when it is needed, so
 * a flow updated from another thread is safe to read here.
 */
private class StateFlowState<T> : PolledState<T>() {
    lateinit var flow: StateFlow<T>

    override fun read(): T = flow.value
}

context(composer: Composer)
/**
 * The flow's current value as a [State], tracked by the composition that reads it.
 *
 * A pass that reads the returned state's `value` makes its [Composition][com.awakekt.awake.compose.runtime.Composition]
 * invalid once this flow holds a different value, so a host that composes only when invalid
 * recomposes then. A host that composes every frame reads the latest value every frame either way.
 *
 * No coroutine collects the flow, and the call allocates nothing after the first pass: the state is
 * remembered for as long as the flow stays the same.
 *
 * ```kotlin
 * val hud by store.state.collectAsState()
 * ```
 */
fun <T> StateFlow<T>.collectAsState(): State<T> {
    @Suppress("UNCHECKED_CAST")
    val state = remember<StateFlowState<*>>(this) { StateFlowState<Any?>() } as StateFlowState<T>
    state.flow = this
    return state
}

context(composer: Composer)
/**
 * The store's current state as a [State], tracked as [StateFlow.collectAsState] tracks
 * [Store.state].
 *
 * ```kotlin
 * val hud by useStore<HudState>().collectAsState()
 * ```
 */
fun <S> Store<S>.collectAsState(): State<S> = state.collectAsState()
