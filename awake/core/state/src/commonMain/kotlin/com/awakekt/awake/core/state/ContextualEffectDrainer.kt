/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

/**
 * Coordinates synchronous per-frame syncing and effect draining across one or more [EffectSource]s
 * ([ReducerStore], [MutableEffectStore]) using a caller-supplied frame context [C].
 *
 * Unlike [EffectDrainer], bindings receive [C] at drain/sync time rather than closing over mutable
 * fields (such as an active `World` or local player handle). Supports both single-phase [drain]
 * and split two-phase frame bridges ([drainEffects] before simulation advances, [sync] after
 * simulation advances and before UI composes) with zero per-frame allocations.
 */
class ContextualEffectDrainer<C> : AutoCloseable {
    private val bindings = ArrayList<Binding<C>>()

    /** Number of bindings currently registered. */
    val size: Int get() = bindings.size

    /**
     * Registers [source] so [sync] invokes the optional [sync] hook and [drainEffects] routes
     * every pending effect to [onEffect] with the frame context [C].
     *
     * @param E The one-shot effect type.
     * @param source Store or effect queue to drain on each frame.
     * @param closeWithDrainer When `true` and [source] implements [AutoCloseable], [close] closes it too.
     * @param sync Optional hook invoked with [C] during [sync] and before draining in [drain].
     * @param onEffect Handler invoked synchronously in FIFO order for each pending effect.
     * @return Handle whose [AutoCloseable.close] removes this binding (without closing [source]).
     */
    fun <E> bind(
        source: EffectSource<E>,
        closeWithDrainer: Boolean = false,
        sync: ((C) -> Unit)? = null,
        onEffect: (C, E) -> Unit,
    ): AutoCloseable {
        val binding = SourceBinding(
            source = source,
            closeable = if (closeWithDrainer && source is AutoCloseable) source else null,
            syncHook = sync,
            onEffect = onEffect,
        )
        bindings.add(binding)
        return AutoCloseable { bindings.remove(binding) }
    }

    /**
     * Registers [store] with a typed [sync] hook receiving both the frame context [C] and the
     * store's current [S] state snapshot, plus an [onEffect] handler receiving [C] and each [E].
     */
    fun <S, I, E> bind(
        store: ReducerStore<S, I, E>,
        closeWithDrainer: Boolean = false,
        sync: ((C, S) -> Unit)? = null,
        onEffect: (C, E) -> Unit,
    ): AutoCloseable = bind(
        source = store,
        closeWithDrainer = closeWithDrainer,
        sync = sync?.let { hook -> { context -> hook(context, store.value) } },
        onEffect = onEffect,
    )

    /**
     * Registers a context-only sync hook (for read-only projections or UI pieces that emit no
     * effects). If [closeable] is supplied, [close] closes it on teardown.
     */
    fun onSync(
        closeable: AutoCloseable? = null,
        sync: (C) -> Unit,
    ): AutoCloseable {
        val binding = SyncOnlyBinding(closeable = closeable, syncHook = sync)
        bindings.add(binding)
        return AutoCloseable { bindings.remove(binding) }
    }

    /** Runs every registered sync hook in registration order with [context]. */
    fun sync(context: C) {
        var index = 0
        while (index < bindings.size) {
            bindings[index].sync(context)
            index += 1
        }
    }

    /** Drains every registered effect source in registration order with [context]. */
    fun drainEffects(context: C) {
        var index = 0
        while (index < bindings.size) {
            bindings[index].drainEffects(context)
            index += 1
        }
    }

    /** Runs each binding's optional sync step and drains its pending effects in registration order. */
    fun drain(context: C) {
        var index = 0
        while (index < bindings.size) {
            val binding = bindings[index]
            binding.sync(context)
            binding.drainEffects(context)
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

    private interface Binding<C> {
        val closeable: AutoCloseable?

        fun sync(context: C)

        fun drainEffects(context: C)
    }

    private class SyncOnlyBinding<C>(
        override val closeable: AutoCloseable?,
        private val syncHook: (C) -> Unit,
    ) : Binding<C> {
        override fun sync(context: C) = syncHook(context)

        override fun drainEffects(context: C) = Unit
    }

    private class SourceBinding<C, E>(
        private val source: EffectSource<E>,
        override val closeable: AutoCloseable?,
        private val syncHook: ((C) -> Unit)?,
        private val onEffect: (C, E) -> Unit,
    ) : Binding<C> {
        private var activeContext: Any? = null

        // Allocated once per binding so drainEffects(context) allocates nothing per frame.
        private val drainCallback: (E) -> Unit = { effect ->
            @Suppress("UNCHECKED_CAST")
            onEffect(activeContext as C, effect)
        }

        override fun sync(context: C) {
            syncHook?.invoke(context)
        }

        override fun drainEffects(context: C) {
            activeContext = context
            try {
                source.drainEffects(drainCallback)
            } finally {
                activeContext = null
            }
        }
    }
}
