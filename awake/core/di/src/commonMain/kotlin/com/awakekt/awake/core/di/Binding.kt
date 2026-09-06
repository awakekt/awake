/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

/**
 * Strategy for instantiating and managing the lifecycle of a dependency.
 */
sealed interface Binding<T : Any> {
    /**
     * Resolves the instance using [container].
     */
    fun resolve(container: Container): T
}

/**
 * Binding that returns an existing, pre-instantiated [instance].
 */
class InstanceBinding<T : Any>(private val instance: T) : Binding<T> {
    override fun resolve(container: Container): T = instance
}

/**
 * Binding that evaluates [provider] once lazily and caches the instance for the container lifecycle.
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
 */
class FactoryBinding<T : Any>(private val factory: Container.() -> T) : Binding<T> {
    override fun resolve(container: Container): T = container.factory()
}
