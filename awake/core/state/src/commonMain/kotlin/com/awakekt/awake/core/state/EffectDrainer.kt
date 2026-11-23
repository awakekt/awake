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
            sync = sync,
            drain = { source.drainEffects(onEffect) },
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

    /**
     * Registers a sync-only hook (for read-only UI projections or stateful coordinators that emit
     * no effects). If [closeable] is supplied, [close] closes it on teardown.
     */
    fun onSync(
        closeable: AutoCloseable? = null,
        sync: () -> Unit,
    ): AutoCloseable {
        val binding = Binding(closeable = closeable, sync = sync, drain = null)
        bindings.add(binding)
        return AutoCloseable { bindings.remove(binding) }
    }

    /** Runs every registered sync hook in registration order without draining effects. */
    fun sync() {
        var index = 0
        while (index < bindings.size) {
            bindings[index].sync?.invoke()
            index += 1
        }
    }

    /** Drains every registered effect source in registration order without running sync hooks. */
    fun drainEffects() {
        var index = 0
        while (index < bindings.size) {
            bindings[index].drain?.invoke()
            index += 1
        }
    }

    /** Runs each binding's optional sync step and drains its pending effects in registration order. */
    fun drain() {
        var index = 0
        while (index < bindings.size) {
            val binding = bindings[index]
            binding.sync?.invoke()
            binding.drain?.invoke()
            index += 1
        }
    }

    /** Removes all bindings and closes every store or resource owned by this drainer in reverse order. */
    override fun close() {
        var failure: Throwable? = null
        var index = bindings.size - 1
        while (index >= 0) {
            val closeable = bindings[index].closeable
            if (closeable != null) {
                @Suppress("TooGenericExceptionCaught") // Every remaining closeable must still run on teardown.
                try {
                    closeable.close()
                } catch (error: Throwable) {
                    if (failure == null) failure = error else failure.addSuppressed(error)
                }
            }
            index -= 1
        }
        bindings.clear()
        failure?.let { throw it }
    }

    private class Binding(
        val closeable: AutoCloseable?,
        val sync: (() -> Unit)?,
        val drain: (() -> Unit)?,
    )
}
