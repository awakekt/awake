/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:Suppress("FunctionNaming", "ktlint:standard:function-naming")

package com.awakekt.awake.compose.state

import com.awakekt.awake.compose.runtime.Composer
import com.awakekt.awake.compose.runtime.CompositionLocal
import com.awakekt.awake.compose.runtime.CompositionLocalProvider
import com.awakekt.awake.compose.runtime.compositionLocalOf
import com.awakekt.awake.compose.runtime.current
import com.awakekt.awake.compose.runtime.remember
import com.awakekt.awake.core.state.Reducer
import com.awakekt.awake.core.state.ReducerStore
import com.awakekt.awake.core.state.Store
import com.awakekt.awake.core.state.reducerStore
import com.awakekt.awake.core.state.store

/**
 * Ambient [Store] provided to the current Compose tree.
 */
val LocalStore: CompositionLocal<Store<*>?> = compositionLocalOf { null }

context(composer: Composer)
/**
 * Resolves the ambient [Store] managing state of type [S].
 *
 * @param S The state type stored in the container.
 * @return The ambient [Store] managing state of type [S].
 */
inline fun <reified S : Any> useStore(): Store<S> {
    val store = LocalStore.current ?: error(
        "No Store provided in CompositionLocal. " +
            "Provide one using CompositionLocalProvider(LocalStore provides store) or ProvideStore(store).",
    )
    if (store.value is S) {
        @Suppress("UNCHECKED_CAST")
        return store as Store<S>
    }
    error("Ambient Store holds state of type ${store.value::class.simpleName}, but expected ${S::class.simpleName}.")
}

context(composer: Composer)
/**
 * Creates and remembers a lightweight [Store] initialized with [initialState].
 *
 * The store is retained across composition frames as long as [key] does not change.
 *
 * @param S The state type stored in the container.
 * @param key Optional key that triggers recreation of the store when modified.
 * @param initialState Factory producing initial state value.
 * @return The remembered [Store].
 */
fun <S> rememberStore(
    key: Any? = null,
    initialState: () -> S,
): Store<S> = remember(key) { store(initialState()) }

context(composer: Composer)
/**
 * Creates and remembers a [ReducerStore] with the given [initialState] and [reducer].
 *
 * @param S The state type.
 * @param I The intent/action type.
 * @param E The side-effect type.
 * @param key Optional key that triggers recreation of the store when modified.
 * @param initialState Factory producing initial state value.
 * @param reducer Reducer instance handling intents and state transitions.
 * @return The remembered [ReducerStore].
 */
fun <S, I, E> rememberReducerStore(
    key: Any? = null,
    initialState: () -> S,
    reducer: Reducer<S, I, E>,
): ReducerStore<S, I, E> = remember(key) { reducerStore(initialState(), reducer = reducer) }

context(composer: Composer)
/**
 * Creates and remembers a [ReducerStore] using a lambda reducer.
 *
 * @param S The state type.
 * @param I The intent/action type.
 * @param E The side-effect type.
 * @param key Optional key that triggers recreation of the store when modified.
 * @param initialState Factory producing initial state value.
 * @param reducer Reducer lambda handling intents and returning updated state with optional side effect.
 * @return The remembered [ReducerStore].
 */
fun <S, I, E> rememberReducerStore(
    key: Any? = null,
    initialState: () -> S,
    reducer: (state: S, intent: I) -> Pair<S, E?>,
): ReducerStore<S, I, E> = remember(key) {
    reducerStore(initialState(), reducer = Reducer(reducer))
}

context(composer: Composer)
/**
 * Convenience wrapper providing [store] to [content].
 *
 * @param store The ambient [Store] instance to provide.
 * @param content Composable block executed within the provided store scope.
 */
fun ProvideStore(
    store: Store<*>,
    content: context(Composer) () -> Unit,
) {
    CompositionLocalProvider(LocalStore, store, content)
}
