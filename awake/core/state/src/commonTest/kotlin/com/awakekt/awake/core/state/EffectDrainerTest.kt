/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EffectDrainerTest {
    private data class CounterState(val count: Int = 0)

    private sealed interface CounterIntent {
        data class Add(val delta: Int) : CounterIntent
        data class Emit(val label: String) : CounterIntent
    }

    private sealed interface CounterEffect {
        data class Note(val label: String) : CounterEffect
    }

    private class CustomEffectStore : MutableEffectStore<CounterState, String>(CounterState()) {
        fun push(value: String) = emitEffect(value)
    }

    @Test
    fun callbackDrainEffectsConsumesInOrderWithoutReturningList() {
        val store = reducerStore<CounterState, CounterIntent, CounterEffect>(CounterState()) { state, intent ->
            when (intent) {
                is CounterIntent.Add -> state.copy(count = state.count + intent.delta) to null
                is CounterIntent.Emit -> state to CounterEffect.Note("${intent.label}:${state.count}")
            }
        }
        store.dispatch(CounterIntent.Add(3))
        store.dispatch(CounterIntent.Emit("a"))
        store.dispatch(CounterIntent.Add(2))
        store.dispatch(CounterIntent.Emit("b"))

        val seen = mutableListOf<String>()
        store.drainEffects { effect -> seen += (effect as CounterEffect.Note).label }
        assertEquals(listOf("a:3", "b:5"), seen)

        seen.clear()
        store.drainEffects { effect -> seen += (effect as CounterEffect.Note).label }
        assertTrue(seen.isEmpty(), "second callback drain must be empty")
        store.close()
    }

    @Test
    fun drainingConsumesFromSingleQueueSoEffectsFlowDoesNotRetainDrainedItems() = runTest {
        val store = reducerStore<CounterState, CounterIntent, CounterEffect>(CounterState(), scope = this) { state, intent ->
            when (intent) {
                is CounterIntent.Emit -> state to CounterEffect.Note(intent.label)
                is CounterIntent.Add -> state to null
            }
        }
        repeat(100) { index -> store.dispatch(CounterIntent.Emit("old-$index")) }
        var drainedCount = 0
        store.drainEffects { drainedCount += 1 }
        assertEquals(100, drainedCount)

        // Next emitted effect must be the first item observed on effects, proving drained effects were not leaked.
        store.dispatch(CounterIntent.Emit("fresh"))
        val seen = mutableListOf<String>()
        store.drainEffects { seen += (it as CounterEffect.Note).label }
        assertEquals(listOf("fresh"), seen)
    }

    @Test
    fun effectDrainerDrainsMultipleStoresInOrderAndSupportsUnbindAndClose() {
        val reducerStore = reducerStore<CounterState, CounterIntent, CounterEffect>(CounterState()) { state, intent ->
            when (intent) {
                is CounterIntent.Add -> state.copy(count = state.count + intent.delta) to null
                is CounterIntent.Emit -> state to CounterEffect.Note(intent.label)
            }
        }
        val customStore = CustomEffectStore()
        val log = mutableListOf<String>()

        val drainer = EffectDrainer()
        val firstHandle = drainer.bind(
            store = reducerStore,
            closeWithDrainer = true,
            sync = { state -> log += "sync:${state.count}" },
            onEffect = { effect -> log += "reducer:${(effect as CounterEffect.Note).label}" },
        )
        drainer.bind(
            source = customStore,
            closeWithDrainer = false,
            onEffect = { effect -> log += "custom:$effect" },
        )
        assertEquals(2, drainer.size)

        reducerStore.dispatch(CounterIntent.Add(4))
        reducerStore.dispatch(CounterIntent.Emit("click"))
        customStore.push("ping")

        drainer.drain()
        assertEquals(listOf("sync:4", "reducer:click", "custom:ping"), log)

        log.clear()
        drainer.drain()
        assertEquals(listOf("sync:4"), log)

        // Unbinding stops draining that store without closing it.
        firstHandle.close()
        assertEquals(1, drainer.size)
        assertTrue(reducerStore.coroutineScope.isActive)

        log.clear()
        customStore.push("pong")
        drainer.drain()
        assertEquals(listOf("custom:pong"), log)

        // Re-bind with closeWithDrainer = true and verify close() closes only owned stores.
        drainer.bind(reducerStore, closeWithDrainer = true) {}
        drainer.close()
        assertEquals(0, drainer.size)
        assertFalse(reducerStore.coroutineScope.isActive)
        assertTrue(customStore.coroutineScope.isActive)
        customStore.close()
    }
}
