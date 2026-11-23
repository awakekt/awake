/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.isActive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextualEffectDrainerTest {
    private data class FrameContext(var tick: Int = 0, var label: String = "")

    private data class PanelState(val count: Int = 0)

    private sealed interface PanelIntent {
        data class SetCount(val value: Int) : PanelIntent
        data class Trigger(val tag: String) : PanelIntent
    }

    private sealed interface PanelEffect {
        data class Action(val tag: String) : PanelEffect
    }

    private class RawEffectSource : MutableEffectStore<Int, String>(0) {
        fun push(value: String) = emitEffect(value)
    }

    @Test
    fun supportsSplitSyncAndDrainEffectsAsWellAsCombinedDrain() {
        val store = reducerStore<PanelState, PanelIntent, PanelEffect>(PanelState()) { state, intent ->
            when (intent) {
                is PanelIntent.SetCount -> state.copy(count = intent.value) to null
                is PanelIntent.Trigger -> state to PanelEffect.Action("${intent.tag}:${state.count}")
            }
        }
        val raw = RawEffectSource()
        val log = mutableListOf<String>()

        val drainer = ContextualEffectDrainer<FrameContext>()
        drainer.bind(
            store = store,
            closeWithDrainer = true,
            sync = { ctx, state ->
                log += "sync-store:${ctx.tick}:${state.count}"
                if (state.count != ctx.tick) store.dispatch(PanelIntent.SetCount(ctx.tick))
            },
            onEffect = { ctx, effect ->
                log += "effect-store:${ctx.label}:${(effect as PanelEffect.Action).tag}"
            },
        )
        drainer.bind(
            source = raw,
            closeWithDrainer = true,
            sync = { ctx -> log += "sync-raw:${ctx.tick}" },
            onEffect = { ctx, effect -> log += "effect-raw:${ctx.label}:$effect" },
        )
        drainer.onSync { ctx -> log += "sync-only:${ctx.tick}" }

        assertEquals(3, drainer.size)

        val ctx = FrameContext(tick = 1, label = "f1")
        store.dispatch(PanelIntent.Trigger("click"))
        raw.push("ping")

        // Two-phase bridge pattern: drain UI effects before simulation, sync fresh state after.
        drainer.drainEffects(ctx)
        assertEquals(listOf("effect-store:f1:click:0", "effect-raw:f1:ping"), log)

        log.clear()
        drainer.sync(ctx)
        assertEquals(listOf("sync-store:1:0", "sync-raw:1", "sync-only:1"), log)
        assertEquals(1, store.value.count)

        // Single-phase combined drain reuses the same context instance across frames.
        log.clear()
        ctx.tick = 2
        ctx.label = "f2"
        store.dispatch(PanelIntent.Trigger("again"))
        drainer.drain(ctx)
        assertEquals(
            listOf(
                "sync-store:2:1",
                "effect-store:f2:again:1",
                "sync-raw:2",
                "sync-only:2",
            ),
            log,
        )

        drainer.close()
        assertEquals(0, drainer.size)
        assertFalse(store.coroutineScope.isActive)
        assertFalse(raw.coroutineScope.isActive)
    }

    @Test
    fun unbindStopsCallbacksWithoutClosingAndCloseRunsAllCloseablesInReverseOrder() {
        val closed = mutableListOf<String>()
        val store = reducerStore<PanelState, PanelIntent, PanelEffect>(PanelState()) { state, _ -> state to null }
        val drainer = ContextualEffectDrainer<FrameContext>()

        val handle = drainer.bind(store, closeWithDrainer = true) { _, _ -> }
        drainer.onSync(closeable = AutoCloseable { closed += "first" }) {}
        drainer.onSync(
            closeable = AutoCloseable {
                closed += "second"
                error("boom")
            },
        ) {}
        drainer.onSync(closeable = AutoCloseable { closed += "third" }) {}

        handle.close()
        assertEquals(3, drainer.size)
        assertTrue(store.coroutineScope.isActive)

        val err = assertFailsWith<IllegalStateException> { drainer.close() }
        assertEquals("boom", err.message)
        assertEquals(listOf("third", "second", "first"), closed)
        assertEquals(0, drainer.size)
        assertTrue(store.coroutineScope.isActive)
        store.close()
    }

    @Test
    fun effectDrainerSupportsOnSyncAndSplitPhases() {
        val store = reducerStore<PanelState, PanelIntent, PanelEffect>(PanelState()) { state, intent ->
            when (intent) {
                is PanelIntent.SetCount -> state.copy(count = intent.value) to null
                is PanelIntent.Trigger -> state to PanelEffect.Action(intent.tag)
            }
        }
        val events = mutableListOf<String>()
        val drainer = EffectDrainer()
        drainer.bind(
            store = store,
            closeWithDrainer = true,
            sync = { state -> events += "sync:${state.count}" },
            onEffect = { effect -> events += "effect:${(effect as PanelEffect.Action).tag}" },
        )
        drainer.onSync(closeable = AutoCloseable { events += "closed-hook" }) {
            events += "hook"
        }

        store.dispatch(PanelIntent.SetCount(5))
        store.dispatch(PanelIntent.Trigger("go"))

        drainer.drainEffects()
        assertEquals(listOf("effect:go"), events)

        events.clear()
        drainer.sync()
        assertEquals(listOf("sync:5", "hook"), events)

        events.clear()
        drainer.close()
        assertEquals(listOf("closed-hook"), events)
        assertFalse(store.coroutineScope.isActive)
    }
}
