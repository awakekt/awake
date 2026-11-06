/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.state

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.RememberObserver
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.core.state.Reducer
import com.awakekt.awake.core.state.ReducerStore
import com.awakekt.awake.core.state.Store
import com.awakekt.awake.core.state.StoreScope
import com.awakekt.awake.core.state.childStoreScope
import com.awakekt.awake.core.state.reducerStore
import com.awakekt.awake.core.state.store
import kotlinx.coroutines.CoroutineScope

/** Holds a remembered store and closes it when the composition forgets it. */
private class RememberedStore<T : StoreScope>(val store: T) : RememberObserver {
    override fun onForgotten() {
        store.close()
    }
}

@Suppress("UNCHECKED_CAST")
/**
 * Remembers the store [create] makes while [key1] and [key2] stay the same, and closes it when the
 * composition forgets it: a key changed, the call stopped being made, or its node left the tree.
 */
context(composer: Composer)
internal fun <T : StoreScope> rememberClosing(key1: Any?, key2: Any?, create: () -> T): T =
    remember<RememberedStore<*>>(key1, key2) { RememberedStore(create()) }.store as T

context(composer: Composer)
/**
 * Creates and remembers a lightweight [Store] that runs as a child of [parent], normally the app's
 * coroutine scope (see [childStoreScope]).
 *
 * An uncaught failure in the store's coroutines reaches [parent]'s exception handler, and cancelling
 * [parent] cancels the store. The store is closed, cancelling only its own coroutines, when the
 * composition forgets it: [key] or [parent] changed, the call stopped being made, or its node left
 * the tree.
 *
 * ```kotlin
 * val hud = rememberStore(appScope) { HudState() }
 * ```
 *
 * @param S The state type stored in the container.
 * @param parent The scope the store's coroutines run under.
 * @param key Optional key that recreates the store, closing the previous one, when it changes.
 * @param initialState Factory producing the initial state value.
 * @return The remembered [Store].
 */
fun <S> rememberStore(
    parent: CoroutineScope,
    key: Any? = null,
    initialState: () -> S,
): Store<S> = rememberClosing(key, parent) { store(initialState(), parent.childStoreScope()) }

context(composer: Composer)
/**
 * Creates and remembers a [ReducerStore] that runs as a child of [parent]. Lifetime as the
 * [rememberStore] overload that takes a parent.
 *
 * @param S The state type.
 * @param I The intent/action type.
 * @param E The side-effect type.
 * @param parent The scope the store's coroutines run under.
 * @param key Optional key that recreates the store, closing the previous one, when it changes.
 * @param initialState Factory producing the initial state value.
 * @param reducer Reducer instance handling intents and state transitions.
 * @return The remembered [ReducerStore].
 */
fun <S, I, E> rememberReducerStore(
    parent: CoroutineScope,
    key: Any? = null,
    initialState: () -> S,
    reducer: Reducer<S, I, E>,
): ReducerStore<S, I, E> = rememberClosing(key, parent) {
    reducerStore(initialState(), parent.childStoreScope(), reducer)
}

context(composer: Composer)
/**
 * Creates and remembers a [ReducerStore] using a lambda reducer that runs as a child of [parent].
 * Lifetime as the [rememberStore] overload that takes a parent.
 *
 * @param S The state type.
 * @param I The intent/action type.
 * @param E The side-effect type.
 * @param parent The scope the store's coroutines run under.
 * @param key Optional key that recreates the store, closing the previous one, when it changes.
 * @param initialState Factory producing the initial state value.
 * @param reducer Reducer lambda handling intents and returning updated state with optional side effect.
 * @return The remembered [ReducerStore].
 */
fun <S, I, E> rememberReducerStore(
    parent: CoroutineScope,
    key: Any? = null,
    initialState: () -> S,
    reducer: (state: S, intent: I) -> Pair<S, E?>,
): ReducerStore<S, I, E> = rememberClosing(key, parent) {
    reducerStore(initialState(), parent.childStoreScope(), Reducer(reducer))
}
