/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

/**
 * Coordinates synchronous per-frame draining (and optional pre-drain sync) across one or more
 * [EffectSource]s ([ReducerStore], [MutableEffectStore]) in registration order.
 *
 * Register each store once via [bind], then call [drain] once per frame at the application or
 * session boundary instead of repeating manual `drainEffects()` loops in every system. Call
 * [close] on teardown to unbind all sources and close any bound with `closeWithDrainer = true`.
 */
class EffectDrainer : AutoCloseable {
    private val bindings = ArrayList<Binding>()

    /** Number of store bindings currently registered. */
    val size: Int get() = bindings.size

    /**
     * Registers [source] so [drain] runs optional [sync] and routes every pending effect to [onEffect].
     *
     * @param E The one-shot effect type.
     * @param source Store or effect queue to drain on every [drain] call.
     * @param closeWithDrainer When `true` and [source] implements [AutoCloseable], [close] closes it too.
     * @param sync Optional hook invoked before draining [source] on each [drain] call.
     * @param onEffect Handler invoked synchronously in FIFO order for each pending effect.
     * @return Handle whose [AutoCloseable.close] removes this binding (without closing [source]).
     */
    fun <E> bind(
        source: EffectSource<E>,
        closeWithDrainer: Boolean = false,
        sync: (() -> Unit)? = null,
        onEffect: (E) -> Unit,
    ): AutoCloseable {
        val binding = Binding(
            closeable = if (closeWithDrainer && source is AutoCloseable) source else null,
            step = {
                sync?.invoke()
                source.drainEffects(onEffect)
            },
        )
        bindings.add(binding)
        return AutoCloseable { bindings.remove(binding) }
    }

    /**
     * Registers [store] with a typed [sync] hook receiving the store's current [S] state snapshot
     * before draining queued [E] effects into [onEffect].
     */
    fun <S, I, E> bind(
        store: ReducerStore<S, I, E>,
        closeWithDrainer: Boolean = false,
        sync: ((S) -> Unit)? = null,
        onEffect: (E) -> Unit,
    ): AutoCloseable = bind(
        source = store,
        closeWithDrainer = closeWithDrainer,
        sync = sync?.let { hook -> { hook(store.value) } },
        onEffect = onEffect,
    )

    /** Runs each binding's optional sync step and drains its pending effects in registration order. */
    fun drain() {
        var index = 0
        while (index < bindings.size) {
            bindings[index].step()
            index += 1
        }
    }

    /** Removes all bindings and closes every store bound with `closeWithDrainer = true`. */
    override fun close() {
        var index = 0
        while (index < bindings.size) {
            bindings[index].closeable?.close()
            index += 1
        }
        bindings.clear()
    }

    private class Binding(
        val closeable: AutoCloseable?,
        val step: () -> Unit,
    )
}
