/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StoreTest {

    data class CounterState(
        val count: Int = 0,
        val text: String = "",
    )

    @Test
    fun storeUpdatesStateAndReflectsInValue() {
        val store = store(CounterState())
        assertEquals(0, store.value.count)

        store.update { it.copy(count = 5) }
        assertEquals(5, store.value.count)
        assertEquals(5, store.state.value.count)
    }

    @Test
    fun storeSelectDerivesSubState() = runTest {
        val job = kotlinx.coroutines.Job()
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext + job)
        val store = store(CounterState(count = 1, text = "init"), scope = scope)
        val textFlow = store.select(scope) { it.text }
        assertEquals("init", textFlow.value)

        store.update { it.copy(text = "updated") }
        assertEquals("updated", textFlow.first { it == "updated" })
        job.cancel()
    }

    @Test
    fun storeScopeCancelsUnderlyingScopeOnClose() {
        val store = store(CounterState())
        assertTrue(store.coroutineScope.isActive)

        store.close()
        assertFalse(store.coroutineScope.isActive)
    }

    // ReducerStore tests
    sealed interface CounterIntent {
        data class Increment(val amount: Int = 1) : CounterIntent
        data object RequestAlert : CounterIntent
        data object Reset : CounterIntent
    }

    sealed interface CounterEffect {
        data class ShowAlert(val message: String) : CounterEffect
    }

    @Test
    fun reducerStoreProcessesIntentsAndDrainsEffects() {
        val reducer = Reducer<CounterState, CounterIntent, CounterEffect> { state, intent ->
            when (intent) {
                is CounterIntent.Increment -> state.copy(count = state.count + intent.amount) to null
                is CounterIntent.RequestAlert -> state to CounterEffect.ShowAlert("Count is ${state.count}")
                is CounterIntent.Reset -> CounterState() to null
            }
        }

        val store = reducerStore(CounterState(), reducer = reducer)

        // Initial state
        assertEquals(0, store.value.count)
        assertTrue(store.drainEffects().isEmpty())

        // Increment
        store.dispatch(CounterIntent.Increment(10))
        assertEquals(10, store.value.count)
        assertTrue(store.drainEffects().isEmpty())

        // Request alert effect
        store.dispatch(CounterIntent.RequestAlert)
        val drained = store.drainEffects()
        assertEquals(1, drained.size)
        assertEquals("Count is 10", (drained[0] as CounterEffect.ShowAlert).message)

        // Second drain must be empty (single-consumption rule)
        assertTrue(store.drainEffects().isEmpty(), "Second drain of effects must be empty")
    }

    @Test
    fun reducerStoreEmitsToEffectsFlow() = runTest {
        val store = reducerStore<CounterState, CounterIntent, CounterEffect>(
            initialState = CounterState(count = 42),
            scope = this,
        ) { state, intent ->
            when (intent) {
                is CounterIntent.RequestAlert -> state to CounterEffect.ShowAlert("Test ${state.count}")
                else -> state to null
            }
        }

        store.dispatch(CounterIntent.RequestAlert)
        val effect = store.effects.first()
        assertEquals("Test 42", (effect as CounterEffect.ShowAlert).message)
    }
}
