/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

/**
 * Dependency resolution container supporting singletons, factories, instances, and child scopes.
 */
interface Container {
    /**
     * Resolves the instance corresponding to [key].
     *
     * @throws MissingBindingException if no binding is registered.
     * @throws CyclicDependencyException if a cyclic dependency is detected.
     */
    fun <T : Any> get(key: Key<T>): T

    /**
     * Resolves the instance corresponding to [key], or returns null if not bound.
     */
    fun <T : Any> getOrNull(key: Key<T>): T?

    /**
     * Creates a hierarchical child container that inherits this container's bindings
     * and can override or provide child-scoped bindings.
     */
    fun createChild(configure: ModuleBuilder.() -> Unit = {}): Container

    /**
     * Creates a hierarchical child container using definitions from [module].
     */
    fun createChild(module: Module): Container
}

/**
 * Resolves an instance of [T] with optional [qualifier].
 */
inline fun <reified T : Any> Container.get(qualifier: String? = null): T =
    get(Key(T::class, qualifier))

/**
 * Resolves an instance of [T] with optional [qualifier] (alias for [get]).
 */
inline fun <reified T : Any> Container.resolve(qualifier: String? = null): T =
    get(Key(T::class, qualifier))

/**
 * Resolves an instance of [T] with optional [qualifier], or returns null if not bound.
 */
inline fun <reified T : Any> Container.getOrNull(qualifier: String? = null): T? =
    getOrNull(Key(T::class, qualifier))

/**
 * Creates a lazy property delegate that resolves [T] with optional [qualifier] on first access.
 */
inline fun <reified T : Any> Container.inject(qualifier: String? = null): Lazy<T> =
    lazy { get(Key(T::class, qualifier)) }

/**
 * Creates a new [Container] from a list of [modules].
 */
fun container(modules: Iterable<Module>): Container {
    val builder = ModuleBuilder()
    modules.forEach { builder.include(it) }
    return DefaultContainer(builder.build().bindings)
}

/**
 * Creates a new [Container] from one or more [modules] and optional inline [block].
 */
fun container(vararg modules: Module, block: (ModuleBuilder.() -> Unit)? = null): Container {
    val builder = ModuleBuilder()
    modules.forEach { builder.include(it) }
    block?.let { builder.apply(it) }
    return DefaultContainer(builder.build().bindings)
}

/**
 * Default implementation of [Container].
 */
class DefaultContainer internal constructor(
    private val bindings: Map<Key<*>, Binding<*>>,
    private val parent: Container? = null,
) : Container {
    private val resolving = mutableSetOf<Key<*>>()

    override fun <T : Any> get(key: Key<T>): T = getOrNull(key) ?: throw MissingBindingException(
        "No binding found for key: $key",
    )

    override fun <T : Any> getOrNull(key: Key<T>): T? {
        if (key in resolving) {
            throw CyclicDependencyException("Cyclic dependency detected while resolving: $key")
        }
        val binding = bindings[key]
        if (binding != null) {
            resolving.add(key)
            try {
                @Suppress("UNCHECKED_CAST")
                return (binding as Binding<T>).resolve(this)
            } finally {
                resolving.remove(key)
            }
        }
        return parent?.getOrNull(key)
    }

    override fun createChild(configure: ModuleBuilder.() -> Unit): Container {
        val childModule = ModuleBuilder().apply(configure).build()
        return DefaultContainer(childModule.bindings, parent = this)
    }

    override fun createChild(module: Module): Container = DefaultContainer(module.bindings, parent = this)
}
