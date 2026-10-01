/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

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
 * @param T The dependency type.
 * @param instance The pre-created instance to return on resolution.
 */
class InstanceBinding<T : Any>(private val instance: T) : Binding<T> {
    override fun resolve(container: Container): T = instance
}

/**
 * Binding that evaluates [provider] once lazily and caches the instance for the container lifecycle.
 *
 * @param T The dependency type.
 * @param provider Factory lambda invoked in the context of the container on first resolution.
 */
class SingletonBinding<T : Any>(private val provider: Container.() -> T) : Binding<T> {
    private var cached: T? = null
    private var initialized = false

    override fun resolve(container: Container): T {
        if (!initialized) {
            val created = container.provider()
            cached = created
            initialized = true
            return created
        }
        @Suppress("UNCHECKED_CAST")
        return cached as T
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
