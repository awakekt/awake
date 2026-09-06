/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Lightweight reactive state container.
 *
 * Provides a reactive [state] [StateFlow], direct synchronous access to current [value],
 * atomic [update] transforms, and derived [select] sub-state flows.
 *
 * Equivalent to:
 * - **Zustand** (React / Web): `create((set) => ({ ... }))`
 * - **StateHolder** (Android Jetpack): Plain class holding UI state flow
 * - **Pinia** (Vue)
 *
 * @param S The type of immutable state managed by this store.
 */
interface Store<S> : StoreScope {
    /** The observable stream of state values. */
    val state: StateFlow<S>

    /** Current snapshot value of the state. */
    val value: S get() = state.value

    /** Atomically updates the state by applying [transform]. */
    fun update(transform: (S) -> S)

    /**
     * Derives a distinct, read-only [StateFlow] representing a sub-projection of [state].
     */
    fun <T> select(
        scope: CoroutineScope = coroutineScope,
        selector: (S) -> T,
    ): StateFlow<T>
}

/**
 * Default implementation of a reactive [Store].
 */
open class MutableStore<S>(
    initialState: S,
    override val coroutineScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : Store<S> {

    private val _state = MutableStateFlow(initialState)
    override val state: StateFlow<S> = _state.asStateFlow()

    override fun update(transform: (S) -> S) {
        _state.update(transform)
    }

    override fun <T> select(
        scope: CoroutineScope,
        selector: (S) -> T,
    ): StateFlow<T> {
        val initialSub = selector(value)
        return _state
            .map { selector(it) }
            .distinctUntilChanged()
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 0),
                initialValue = initialSub,
            )
    }
}

/**
 * Factory function creating a lightweight [Store] initialized with [initialState].
 */
fun <S> store(
    initialState: S,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
): Store<S> = MutableStore(initialState, scope)
