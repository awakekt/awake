/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.engine.platform.lifecycle

import com.awakekt.awake.core.input.Input
import com.awakekt.awake.engine.platform.config.WindowConfig
import com.awakekt.awake.engine.platform.dsl.AppServiceLookup
import kotlin.reflect.KClass

/**
 * A single game session. Pure delegation to a [AppLifecycle] implementation with
 * attached window configuration and services.
 */
class AwakeAppLifecycle internal constructor(
    private val delegate: AppLifecycle,
    val windowConfig: WindowConfig,
    private val services: Map<KClass<*>, Any>,
) : AppLifecycle by delegate,
    AppServiceLookup {

    private var disposed = false

    /** The session's input accumulator. Guaranteed to exist. */
    val input: Input get() = requireService(Input::class)

    /**
     * Drives one frame using this session's current input snapshot.
     *
     * For headless and test callers. A real host builds the [AppFrame] itself, so the snapshot
     * is taken at the point in the frame it actually belongs (see `GraphicsEngine.update`).
     */
    fun update(delta: Float, viewportWidth: Float, viewportHeight: Float) {
        update(AppFrame(delta, viewportWidth, viewportHeight, input.currentSnapshot))
    }

    /**
     * Runs app callbacks first, then closes owner-managed services in reverse map order.
     * Services are composition resources; render loops must not close them independently.
     */
    override fun dispose() {
        if (disposed) return
        disposed = true
        try {
            delegate.dispose()
        } finally {
            services.values.toList()
                .asReversed()
                .filterIsInstance<AutoCloseable>()
                .forEach(AutoCloseable::close)
        }
    }

    // services is keyed by the exact KClass<T> each value was registered under (see
    // MutableGameServices.register), so `as? T` matches the entry's real type or the lookup
    // legitimately returns null for an unregistered type.
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> service(type: KClass<T>): T? = services[type] as? T
}
