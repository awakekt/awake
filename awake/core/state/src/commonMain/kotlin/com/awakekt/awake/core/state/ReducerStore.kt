/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

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
     *
     * @param state The current state snapshot.
     * @param intent The incoming action or intent to process.
     * @return A pair containing the updated state and an optional side effect.
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
     *
     * @param intent The user action or event to dispatch.
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
 * Base store for synchronous MVI/UDF state holders that mutate [state] and emit one-shot [E]
 * effects without launching coroutines on the frame thread.
 *
 * Subclasses expose domain-specific `dispatch` or action methods that call [updateState] and
 * [emitEffect], while game loops drain queued effects synchronously once per frame via
 * [drainEffects].
 *
 * @param S The state type.
 * @param E The one-shot effect type.
 * @param initialState The initial state value.
 * @param scope Coroutine scope governing derived state projections.
 */
open class MutableEffectStore<S, E>(
    initialState: S,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : MutableStore<S>(initialState, scope) {

    private val effectChannel = Channel<E>(Channel.UNLIMITED)
    private val flowChannel = Channel<E>(Channel.UNLIMITED)

    /** Observable stream of one-shot effects for asynchronous or UI collectors. */
    val effects: Flow<E> = flowChannel.receiveAsFlow()

    /**
     * Atomically updates the current state using [transform].
     *
     * @param transform Pure function computing the next state from the current state.
     */
    protected fun updateState(transform: (S) -> S) {
        update(transform)
    }

    /**
     * Synchronously enqueues a one-shot [effect] for [drainEffects] and emits it to [effects]
     * without launching a coroutine.
     *
     * @param effect The one-shot side effect to emit.
     */
    protected fun emitEffect(effect: E) {
        effectChannel.trySend(effect)
        flowChannel.trySend(effect)
    }

    /**
     * Synchronously drains all pending effects in FIFO order.
     *
     * @return The list of effects queued since the previous drain, or [emptyList] if none.
     */
    fun drainEffects(): List<E> {
        val first = effectChannel.tryReceive().getOrNull() ?: return emptyList()
        val drained = ArrayList<E>()
        drained.add(first)
        while (true) {
            val next = effectChannel.tryReceive().getOrNull() ?: break
            drained.add(next)
        }
        return drained
    }
}

/**
 * Production implementation of [ReducerStore].
 *
 * @param S The state type.
 * @param I The intent/action type.
 * @param E The one-shot effect type.
 * @param initialState The initial state value.
 * @param reducer The state transition function.
 * @param scope Coroutine scope governing state flow updates and effect emissions.
 */
open class DefaultReducerStore<S, in I, out E>(
    initialState: S,
    private val reducer: Reducer<S, I, E>,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : MutableStore<S>(initialState, scope),
    ReducerStore<S, I, E> {

    private val effectChannel = Channel<E>(Channel.UNLIMITED)
    private val flowChannel = Channel<E>(Channel.UNLIMITED)
    override val effects: Flow<E> = flowChannel.receiveAsFlow()

    override fun dispatch(intent: I) {
        var effectToEmit: E? = null
        update { current ->
            val (nextState, effect) = reducer.reduce(current, intent)
            effectToEmit = effect
            nextState
        }

        effectToEmit?.let { eff ->
            effectChannel.trySend(eff)
            flowChannel.trySend(eff)
        }
    }

    override fun drainEffects(): List<E> {
        val first = effectChannel.tryReceive().getOrNull() ?: return emptyList()
        val drained = ArrayList<E>()
        drained.add(first)
        while (true) {
            val next = effectChannel.tryReceive().getOrNull() ?: break
            drained.add(next)
        }
        return drained
    }
}

/**
 * Factory creating a [ReducerStore] with the given [initialState] and [reducer].
 *
 * @param S The state type.
 * @param I The intent type.
 * @param E The effect type.
 * @param initialState The initial state value.
 * @param scope Coroutine scope governing background flow emissions.
 * @param reducer The pure state transition reducer.
 * @return An initialized [ReducerStore] instance.
 */
fun <S, I, E> reducerStore(
    initialState: S,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    reducer: Reducer<S, I, E>,
): ReducerStore<S, I, E> = DefaultReducerStore(initialState, reducer, scope)

/**
 * Factory creating a [ReducerStore] using a lambda reducer.
 *
 * @param S The state type.
 * @param I The intent type.
 * @param E The effect type.
 * @param initialState The initial state value.
 * @param scope Coroutine scope governing background flow emissions.
 * @param reducer Lambda taking state and intent, returning the updated state and optional effect.
 * @return An initialized [ReducerStore] instance.
 */
fun <S, I, E> reducerStore(
    initialState: S,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    reducer: (state: S, intent: I) -> Pair<S, E?>,
): ReducerStore<S, I, E> = DefaultReducerStore(initialState, Reducer(reducer), scope)
