/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

import kotlin.concurrent.Volatile

/**
 * Strategy for instantiating and managing the lifecycle of a dependency.
 *
 * @param T The type of dependency managed by this binding.
 */
sealed interface Binding<T : Any> {
    /**
     * Resolves the instance using [container].
     *
     * @param container The resolving dependency container.
     * @return The resolved instance of type [T].
     */
    fun resolve(container: Container): T
}

/**
 * Binding that returns an existing, pre-instantiated [instance].
 *
 * By default (`closeOnDispose = false`), pre-instantiated values remain owned by the caller and are
 * not closed when the container is closed. Set [closeOnDispose] to `true` to transfer ownership of
 * an [AutoCloseable] instance to the container.
 *
 * @param T The dependency type.
 * @param instance The pre-created instance to return on resolution.
 * @property closeOnDispose Whether [Container.close] should close [instance] if it implements [AutoCloseable].
 */
class InstanceBinding<T : Any>(
    private val instance: T,
    val closeOnDispose: Boolean,
) : Binding<T> {
    /**
     * Creates an [InstanceBinding] whose lifecycle remains owned by the caller (`closeOnDispose = false`).
     *
     * @param instance The pre-created instance to return on resolution.
     */
    constructor(instance: T) : this(instance, closeOnDispose = false)

    override fun resolve(container: Container): T = instance

    internal fun rawInstance(): T = instance
}

/**
 * Binding that evaluates [provider] once lazily in a thread-safe manner and caches the instance
 * for the container lifecycle.
 *
 * @param T The dependency type.
 * @param provider Factory lambda invoked in the context of the container on first resolution.
 */
class SingletonBinding<T : Any>(private val provider: Container.() -> T) : Binding<T> {
    private val lock = DiLock()

    @Volatile
    private var cached: T? = null

    @Volatile
    private var initialized = false

    override fun resolve(container: Container): T {
        if (initialized) {
            @Suppress("UNCHECKED_CAST")
            return cached as T
        }
        return lock.withLock {
            if (!initialized) {
                val created = container.provider()
                cached = created
                initialized = true
                created
            } else {
                @Suppress("UNCHECKED_CAST")
                cached as T
            }
        }
    }

    internal fun cachedOrNull(): T? = if (initialized) cached else null

    internal fun resolveUnderContainerLock(container: Container, onCreated: (T) -> Unit): T {
        if (initialized) {
            @Suppress("UNCHECKED_CAST")
            return cached as T
        }
        val created = container.provider()
        cached = created
        initialized = true
        onCreated(created)
        return created
    }

    internal fun freshCopy(): SingletonBinding<T> = SingletonBinding(provider)

    internal fun clear() {
        cached = null
        initialized = false
    }
}

/**
 * Binding that evaluates [factory] fresh on every resolution request.
 *
 * @param T The dependency type.
 * @param factory Factory lambda invoked in the context of the container on every resolution.
 */
class FactoryBinding<T : Any>(private val factory: Container.() -> T) : Binding<T> {
    override fun resolve(container: Container): T = container.factory()
}
