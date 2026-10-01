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
     * @param T The expected return type.
     * @param key The dependency lookup key.
     * @return The resolved instance of type [T].
     * @throws MissingBindingException if no binding is registered.
     * @throws CyclicDependencyException if a cyclic dependency is detected.
     */
    fun <T : Any> get(key: Key<T>): T

    /**
     * Resolves the instance corresponding to [key], or returns null if not bound.
     *
     * @param T The expected return type.
     * @param key The dependency lookup key.
     * @return The resolved instance of type [T], or null if unbound.
     * @throws CyclicDependencyException if a cyclic dependency is detected.
     */
    fun <T : Any> getOrNull(key: Key<T>): T?

    /**
     * Creates a hierarchical child container that inherits this container's bindings
     * and can override or provide child-scoped bindings.
     *
     * @param configure DSL block configuring child bindings.
     * @return A new child [Container].
     */
    fun createChild(configure: ModuleBuilder.() -> Unit = {}): Container

    /**
     * Creates a hierarchical child container using definitions from [module].
     *
     * @param module The module containing child-scoped bindings.
     * @return A new child [Container].
     */
    fun createChild(module: Module): Container
}

/**
 * Resolves an instance of [T] with optional [qualifier].
 *
 * @param T The dependency type to resolve.
 * @param qualifier Optional qualifier string.
 * @return The resolved instance of type [T].
 * @throws MissingBindingException if no matching binding exists.
 */
inline fun <reified T : Any> Container.get(qualifier: String? = null): T =
    get(Key(T::class, qualifier))

/**
 * Resolves an instance of [T] with optional [qualifier] (alias for [get]).
 *
 * @param T The dependency type to resolve.
 * @param qualifier Optional qualifier string.
 * @return The resolved instance of type [T].
 * @throws MissingBindingException if no matching binding exists.
 */
inline fun <reified T : Any> Container.resolve(qualifier: String? = null): T =
    get(Key(T::class, qualifier))

/**
 * Resolves an instance of [T] with optional [qualifier], or returns null if not bound.
 *
 * @param T The dependency type to resolve.
 * @param qualifier Optional qualifier string.
 * @return The resolved instance of type [T], or null if unbound.
 */
inline fun <reified T : Any> Container.getOrNull(qualifier: String? = null): T? =
    getOrNull(Key(T::class, qualifier))

/**
 * Creates a lazy property delegate that resolves [T] with optional [qualifier] on first access.
 *
 * @param T The dependency type to resolve.
 * @param qualifier Optional qualifier string.
 * @return A [Lazy] delegate providing the resolved instance.
 */
inline fun <reified T : Any> Container.inject(qualifier: String? = null): Lazy<T> =
    lazy { get(Key(T::class, qualifier)) }

/**
 * Creates a new [Container] from a list of [modules].
 *
 * @param modules The collection of modules to assemble into the container.
 * @return An initialized [Container].
 */
fun container(modules: Iterable<Module>): Container {
    val builder = ModuleBuilder()
    modules.forEach { builder.include(it) }
    return DefaultContainer(builder.build().bindings)
}

/**
 * Creates a new [Container] from one or more [modules] and optional inline [block].
 *
 * @param modules Array of modules to include.
 * @param block Optional inline DSL block for additional bindings.
 * @return An initialized [Container].
 */
fun container(vararg modules: Module, block: (ModuleBuilder.() -> Unit)? = null): Container {
    val builder = ModuleBuilder()
    modules.forEach { builder.include(it) }
    block?.let { builder.apply(it) }
    return DefaultContainer(builder.build().bindings)
}

/**
 * Default implementation of [Container].
 *
 * @param bindings Map of registered dependency bindings.
 * @param parent Optional parent container for hierarchical fallback.
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
