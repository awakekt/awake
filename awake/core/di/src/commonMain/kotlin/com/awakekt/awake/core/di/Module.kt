/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

/**
 * Encapsulates a collection of [Binding] definitions.
 *
 * Modules are reusable blueprints: constructing multiple containers from the same [Module]
 * gives each container its own isolated singleton cache and disposal lifecycle.
 *
 * @property bindings Internal map of dependency keys to their corresponding bindings.
 */
class Module internal constructor(
    internal val bindings: Map<Key<*>, Binding<*>>,
) {
    /**
     * Combines this module with [other], with bindings in [other] taking precedence on collisions.
     *
     * @param other The module to merge into this module.
     * @return A new combined [Module].
     */
    operator fun plus(other: Module): Module {
        val merged = LinkedHashMap<Key<*>, Binding<*>>(bindings.size + other.bindings.size)
        merged.putAll(bindings)
        merged.putAll(other.bindings)
        return Module(merged)
    }
}

/**
 * DSL builder for declaring [Module] bindings.
 */
class ModuleBuilder {
    private val bindings = LinkedHashMap<Key<*>, Binding<*>>()

    /**
     * Declares a lazy singleton binding evaluated once and cached for the owning container lifecycle.
     *
     * If the created instance implements [AutoCloseable], it is closed automatically when the
     * owning [Container] is closed.
     *
     * @param T The dependency type to bind.
     * @param qualifier Optional qualifier string to differentiate bindings.
     * @param provider Factory lambda evaluated once to construct the singleton instance.
     */
    inline fun <reified T : Any> singleton(
        qualifier: String? = null,
        noinline provider: Container.() -> T,
    ) {
        bind(Key(T::class, qualifier), SingletonBinding(provider))
    }

    /**
     * Declares a factory binding evaluated fresh on each request.
     *
     * @param T The dependency type to bind.
     * @param qualifier Optional qualifier string to differentiate bindings.
     * @param factory Factory lambda executed each time an instance is requested.
     */
    inline fun <reified T : Any> factory(
        qualifier: String? = null,
        noinline factory: Container.() -> T,
    ) {
        bind(Key(T::class, qualifier), FactoryBinding(factory))
    }

    /**
     * Declares a direct instance binding.
     *
     * Pre-instantiated values remain owned by the caller by default ([closeOnDispose] = `false`).
     * Pass `closeOnDispose = true` to close [value] automatically when the container closes.
     *
     * @param T The dependency type to bind.
     * @param value The pre-existing instance to bind.
     * @param qualifier Optional qualifier string to differentiate bindings.
     * @param closeOnDispose Whether closing the container should close [value] if it implements [AutoCloseable].
     */
    inline fun <reified T : Any> instance(
        value: T,
        qualifier: String? = null,
        closeOnDispose: Boolean = false,
    ) {
        bind(Key(T::class, qualifier), InstanceBinding(value, closeOnDispose))
    }

    /**
     * Registers a custom [binding] under [key].
     *
     * @param T The dependency type.
     * @param key The dependency lookup key.
     * @param binding The resolution strategy to register.
     */
    fun <T : Any> bind(key: Key<T>, binding: Binding<T>) {
        bindings[key] = binding
    }

    /**
     * Merges definitions from another [module] into this builder.
     *
     * @param module The module whose bindings should be imported.
     */
    fun include(module: Module) {
        bindings.putAll(module.bindings)
    }

    internal fun build(): Module = Module(bindings.toMap())
}

/**
 * Builds a [Module] using the [ModuleBuilder] DSL.
 *
 * @param block DSL configuration block.
 * @return A compiled [Module] containing all declared bindings.
 */
fun module(block: ModuleBuilder.() -> Unit): Module =
    ModuleBuilder().apply(block).build()
