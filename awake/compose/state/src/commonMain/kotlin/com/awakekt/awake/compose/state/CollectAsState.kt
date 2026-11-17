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

/**
 * One part of a [StateFlow]'s value, [select]ed from it. Kept until the flow holds a different object:
 * a poll of an unchanged flow is one identity check and returns the same instance, with no [select]
 * call, no boxing and no `equals` on the whole state. When the flow's object changes, [select] runs
 * once, and the part already held is kept when the new one equals it.
 */
private class SelectedState<S, T> : PolledState<T>() {
    lateinit var flow: StateFlow<S>
    lateinit var select: (S) -> T
    private var source: Any? = UNREAD
    private var selected: T? = null

    @Suppress("UNCHECKED_CAST") // [selected] holds a T from [select] once [source] is no longer UNREAD.
    override fun read(): T {
        val state = flow.value
        if (state !== source) {
            val next = select(state)
            if (source === UNREAD || next != selected) selected = next
            source = state
        }
        return selected as T
    }

    private companion object {
        val UNREAD = Any()
    }
}

context(composer: Composer)
/**
 * The part of the flow's value [select] picks, as a [State] tracked by the composition that reads it
 * (#1512): a pass that read it is invalid once the selected part differs, and only then. A flow
 * whose value changes elsewhere, a field another composable shows, leaves this one valid.
 *
 * [select] runs only when the flow holds a new object, and the part is compared with `equals` only
 * then: a frame whose flow did not change costs one identity check. Keep [select] cheap and free of
 * side effects; a new lambda on each pass is fine.
 *
 * ```kotlin
 * val hp by store.state.collectAsState { it.player.hp }
 * ```
 */
fun <S, T> StateFlow<S>.collectAsState(select: (S) -> T): State<T> {
    @Suppress("UNCHECKED_CAST")
    val state = remember<SelectedState<*, *>>(this) { SelectedState<Any?, Any?>() } as SelectedState<S, T>
    state.flow = this
    state.select = select
    return state
}

context(composer: Composer)
/**
 * The part of the store's state [select] picks, as a [State] tracked as [StateFlow.collectAsState]
 * with a selector tracks [Store.state].
 *
 * ```kotlin
 * val hp by useStore<HudState>().collectAsState { it.hp }
 * ```
 */
fun <S, T> Store<S>.collectAsState(select: (S) -> T): State<T> = state.collectAsState(select)

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
