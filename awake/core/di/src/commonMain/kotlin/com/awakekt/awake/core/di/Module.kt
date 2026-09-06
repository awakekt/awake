/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

/**
 * Encapsulates a collection of [Binding] definitions.
 */
class Module internal constructor(
    internal val bindings: Map<Key<*>, Binding<*>>,
) {
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
     * Declares a lazy singleton binding evaluated once and cached.
     */
    inline fun <reified T : Any> singleton(
        qualifier: String? = null,
        noinline provider: Container.() -> T,
    ) {
        bind(Key(T::class, qualifier), SingletonBinding(provider))
    }

    /**
     * Declares a factory binding evaluated fresh on each request.
     */
    inline fun <reified T : Any> factory(
        qualifier: String? = null,
        noinline factory: Container.() -> T,
    ) {
        bind(Key(T::class, qualifier), FactoryBinding(factory))
    }

    /**
     * Declares a direct instance binding.
     */
    inline fun <reified T : Any> instance(
        value: T,
        qualifier: String? = null,
    ) {
        bind(Key(T::class, qualifier), InstanceBinding(value))
    }

    /**
     * Registers a custom [binding] under [key].
     */
    fun <T : Any> bind(key: Key<T>, binding: Binding<T>) {
        bindings[key] = binding
    }

    /**
     * Merges definitions from another [module] into this builder.
     */
    fun include(module: Module) {
        bindings.putAll(module.bindings)
    }

    internal fun build(): Module = Module(bindings.toMap())
}

/**
 * Builds a [Module] using the [ModuleBuilder] DSL.
 */
fun module(block: ModuleBuilder.() -> Unit): Module =
    ModuleBuilder().apply(block).build()
