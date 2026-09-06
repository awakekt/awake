/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Functional reducer signature for unidirectional state transitions.
 *
 * @param S The state type.
 * @param I The intent/action type.
 * @param E The effect type.
 */
fun interface Reducer<S, in I, out E> {
    /**
     * Given the current [state] and incoming [intent], returns the new state and an optional [E] effect.
     */
    fun reduce(state: S, intent: I): Pair<S, E?>
}

/**
 * Unidirectional Data Flow (UDF) / MVI contract store.
 *
 * Enforces strict separation between immutable [State], user [Intent], pure state transitions,
 * and one-shot side [Effect]s.
 *
 * Frame-loop safe:
 * Game loops and engine sessions can call [drainEffects] synchronously once per frame without
 * launching coroutines or using Compose invalidation.
 *
 * Equivalent to:
 * - **Redux Toolkit** (React): `createSlice` / Reducer with actions & listener middleware
 * - **MVI** (Android / Kotlin): Model-View-Intent pattern
 * - **The Elm Architecture** (TEA): `update : Msg -> Model -> (Model, Cmd Msg)`
 * - **UDF Store**: Unidirectional Data Flow architecture
 *
 * @param S The state type.
 * @param I The intent/action type.
 * @param E The one-shot effect type.
 */
interface ReducerStore<S, in I, out E> : Store<S> {
    /**
     * Dispatches an [intent] to mutate state via the pure [Reducer].
     */
    fun dispatch(intent: I)

    /**
     * Synchronously drains all pending effects in order.
     *
     * Consumed effects are removed from the buffer; a subsequent drain call will be empty
     * unless new effects were queued in the interim.
     */
    fun drainEffects(): List<E>

    /**
     * Observable stream of one-shot effects for asynchronous or UI collectors.
     */
    val effects: Flow<E>
}

/**
 * Production implementation of [ReducerStore].
 */
open class DefaultReducerStore<S, in I, out E>(
    initialState: S,
    private val reducer: Reducer<S, I, E>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : MutableStore<S>(initialState, scope),
    ReducerStore<S, I, E> {

    private val effectQueue = ArrayDeque<E>()
    private val queueLock = PlatformLock()

    private val _effects = MutableSharedFlow<E>(extraBufferCapacity = 64)
    override val effects: Flow<E> = _effects.asSharedFlow()

    override fun dispatch(intent: I) {
        var effectToEmit: E? = null
        update { current ->
            val (nextState, effect) = reducer.reduce(current, intent)
            effectToEmit = effect
            nextState
        }

        effectToEmit?.let { eff ->
            queueLock.withLock {
                effectQueue.addLast(eff)
            }
            coroutineScope.launch {
                _effects.emit(eff)
            }
        }
    }

    override fun drainEffects(): List<E> = queueLock.withLock {
        if (effectQueue.isEmpty()) {
            emptyList()
        } else {
            val drained = ArrayList<E>(effectQueue.size)
            while (effectQueue.isNotEmpty()) {
                drained.add(effectQueue.removeFirst())
            }
            drained
        }
    }
}

/**
 * Factory creating a [ReducerStore] with the given [initialState] and [reducer].
 */
fun <S, I, E> reducerStore(
    initialState: S,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    reducer: Reducer<S, I, E>,
): ReducerStore<S, I, E> = DefaultReducerStore(initialState, reducer, scope)

/**
 * Factory creating a [ReducerStore] using a lambda reducer.
 */
fun <S, I, E> reducerStore(
    initialState: S,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    reducer: (state: S, intent: I) -> Pair<S, E?>,
): ReducerStore<S, I, E> = DefaultReducerStore(initialState, Reducer(reducer), scope)
