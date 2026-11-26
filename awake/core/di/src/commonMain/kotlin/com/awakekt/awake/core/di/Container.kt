/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

import kotlin.concurrent.Volatile

/**
 * Thread-safe dependency resolution container supporting singletons, factories, instances,
 * hierarchical child scopes, fail-fast verification, and deterministic [AutoCloseable] teardown.
 */
interface Container : AutoCloseable {
    /**
     * Whether [close] has been invoked on this container.
     */
    val isClosed: Boolean get() = false

    /**
     * Resolves the instance corresponding to [key].
     *
     * @param T The expected return type.
     * @param key The dependency lookup key.
     * @return The resolved instance of type [T].
     * @throws MissingBindingException if no binding is registered.
     * @throws CyclicDependencyException if a cyclic dependency is detected.
     * @throws ContainerClosedException if this container has been closed.
     */
    fun <T : Any> get(key: Key<T>): T

    /**
     * Resolves the instance corresponding to [key], or returns null if not bound.
     *
     * @param T The expected return type.
     * @param key The dependency lookup key.
     * @return The resolved instance of type [T], or null if unbound.
     * @throws CyclicDependencyException if a cyclic dependency is detected.
     * @throws ContainerClosedException if this container has been closed.
     */
    fun <T : Any> getOrNull(key: Key<T>): T?

    /**
     * Creates a hierarchical child container that inherits this container's bindings
     * and can override or provide child-scoped bindings.
     *
     * Closing the parent container also closes any still-open child containers in reverse
     * creation order before closing parent-owned resources.
     *
     * @param configure DSL block configuring child bindings.
     * @return A new child [Container].
     * @throws ContainerClosedException if this container has been closed.
     */
    fun createChild(configure: ModuleBuilder.() -> Unit = {}): Container

    /**
     * Creates a hierarchical child container using definitions from [module].
     *
     * @param module The module containing child-scoped bindings.
     * @return A new child [Container].
     * @throws ContainerClosedException if this container has been closed.
     */
    fun createChild(module: Module): Container

    /**
     * Eagerly verifies that every binding registered directly in this container (and all
     * dependencies they transitively require) can be resolved with no missing bindings or cycles.
     *
     * Temporary [AutoCloseable] instances produced by [FactoryBinding]s during verification are
     * closed immediately before [verify] returns.
     *
     * @throws MissingBindingException if any binding requires an unregistered dependency.
     * @throws CyclicDependencyException if any binding participates in a dependency cycle.
     * @throws ContainerClosedException if this container has been closed.
     */
    fun verify() {}

    /**
     * Closes any open child containers first, then closes all [AutoCloseable] singletons and
     * owned instances (`closeOnDispose = true`) registered in this container in reverse creation
     * order. Subsequent calls to [close] are idempotent no-ops.
     */
    override fun close() {}
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

private class ResolutionFrame(
    val container: DefaultContainer,
    val key: Key<*>,
) {
    override fun equals(other: Any?): Boolean =
        other is ResolutionFrame && container === other.container && key == other.key

    override fun hashCode(): Int = 31 * container.hashCode() + key.hashCode()
}

/**
 * Default thread-safe, disposable implementation of [Container].
 *
 * @param bindings Map of registered dependency bindings.
 * @param parent Optional parent container for hierarchical fallback.
 */
class DefaultContainer internal constructor(
    bindings: Map<Key<*>, Binding<*>>,
    private val parent: Container? = null,
) : Container {
    private val rootLock: DiLock = (parent as? DefaultContainer)?.rootLock ?: DiLock()
    private val resolving: MutableSet<ResolutionFrame> =
        (parent as? DefaultContainer)?.resolving ?: LinkedHashSet()

    private val bindings: Map<Key<*>, Binding<*>> = bindings.mapValues { (_, binding) ->
        if (binding is SingletonBinding<*>) binding.freshCopy() else binding
    }
    private val ownedCloseables = mutableListOf<AutoCloseable>()
    private val children = mutableListOf<DefaultContainer>()

    @Volatile
    private var closed = false

    override val isClosed: Boolean get() = closed

    init {
        for (binding in this.bindings.values) {
            if (binding is InstanceBinding<*> && binding.closeOnDispose) {
                val instance = binding.rawInstance()
                if (instance is AutoCloseable && ownedCloseables.none { it === instance }) {
                    ownedCloseables.add(instance)
                }
            }
        }
    }

    override fun <T : Any> get(key: Key<T>): T = getOrNull(key) ?: throw MissingBindingException(
        "No binding found for key: $key",
    )

    override fun <T : Any> getOrNull(key: Key<T>): T? {
        ensureOpen()
        val binding = bindings[key] ?: return parent?.getOrNull(key)
        return resolveFastOrLocked(key, binding)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> resolveFastOrLocked(key: Key<T>, binding: Binding<*>): T = when {
        binding is InstanceBinding<*> -> (binding as InstanceBinding<T>).resolve(this)
        binding is SingletonBinding<*> ->
            (binding as SingletonBinding<T>).cachedOrNull() ?: rootLock.withLock { resolveLocked(key, binding) }
        else -> rootLock.withLock { resolveLocked(key, binding) }
    }

    override fun createChild(configure: ModuleBuilder.() -> Unit): Container {
        val childModule = ModuleBuilder().apply(configure).build()
        return createChild(childModule)
    }

    override fun createChild(module: Module): Container = rootLock.withLock {
        ensureOpen()
        DefaultContainer(module.bindings, parent = this).also { children.add(it) }
    }

    override fun verify() {
        rootLock.withLock {
            ensureOpen()
            for ((key, binding) in bindings) {
                if (binding is InstanceBinding<*>) continue
                val value = resolveLocked(key, binding)
                if (binding is FactoryBinding<*> && value is AutoCloseable && !isOwnedInHierarchy(value)) {
                    value.close()
                }
            }
        }
    }

    override fun close() {
        val snapshot = rootLock.withLock {
            if (closed) return@withLock null
            closed = true
            (parent as? DefaultContainer)?.children?.remove(this)
            val snapshotChildren = children.toList()
            children.clear()
            val snapshotCloseables = ownedCloseables.toList()
            ownedCloseables.clear()
            for (binding in bindings.values) {
                if (binding is SingletonBinding<*>) {
                    binding.clear()
                }
            }
            snapshotChildren to snapshotCloseables
        } ?: return

        val (childrenToClose, closeablesToClose) = snapshot

        var firstError: Throwable? = null
        for (child in childrenToClose.asReversed()) {
            firstError = closeSafely(child, firstError)
        }
        for (resource in closeablesToClose.asReversed()) {
            firstError = closeSafely(resource, firstError)
        }
        if (firstError != null) throw firstError
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> resolveLocked(key: Key<T>, binding: Binding<*>): T {
        ensureOpen()
        val frame = ResolutionFrame(this, key)
        if (!resolving.add(frame)) {
            val cycle = cycleDescription(resolving, frame)
            throw CyclicDependencyException("Cyclic dependency detected while resolving $key: $cycle")
        }
        try {
            return if (binding is SingletonBinding<*>) {
                (binding as SingletonBinding<T>).resolveUnderContainerLock(this) { created ->
                    if (created is AutoCloseable && ownedCloseables.none { it === created }) {
                        ownedCloseables.add(created)
                    }
                }
            } else {
                (binding as Binding<T>).resolve(this)
            }
        } finally {
            resolving.remove(frame)
        }
    }

    private fun isOwnedInHierarchy(closeable: AutoCloseable): Boolean =
        ownedCloseables.any { it === closeable } ||
            (parent as? DefaultContainer)?.isOwnedInHierarchy(closeable) == true

    private fun ensureOpen() {
        if (closed) {
            throw ContainerClosedException("Container has already been closed")
        }
    }
}

private fun cycleDescription(resolving: Set<ResolutionFrame>, duplicate: ResolutionFrame): String {
    val frames = resolving.toList()
    val startIndex = frames.indexOf(duplicate).coerceAtLeast(0)
    val path = frames.subList(startIndex, frames.size).map { it.key.toString() } + duplicate.key.toString()
    return path.joinToString(" -> ")
}

@Suppress("TooGenericExceptionCaught")
private fun closeSafely(resource: AutoCloseable, existingError: Throwable?): Throwable? =
    try {
        resource.close()
        existingError
    } catch (error: Throwable) {
        if (existingError == null) {
            error
        } else {
            existingError.addSuppressed(error)
            existingError
        }
    }
