// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ecs

/**
 * Read-only view of a maintained one-component family.
 *
 * Membership is updated incrementally as [World] changes. Prefer [forEach] or
 * [forEachComponent] on hot paths: both iterate the cache directly without allocating a
 * collection or materializing a repeated array for an [EcsTag].
 *
 * Structural changes that affect this family must not be made from an iteration callback.
 */
class Family1<A : Any> @PublishedApi internal constructor(
    @PublishedApi internal val cache: Family1Cache<A>,
) {
    /** Number of entities currently matching this family. */
    val size: Int get() = cache.size

    /**
     * Returns the cache's dense component array.
     *
     * Only indices in `0 until [size]` are live. For an [EcsTag], calling this compatibility
     * API lazily materializes repeated singleton references; iteration APIs avoid that cost.
     */
    fun components(): Array<A> = cache.components()

    /** Returns the component at [index] in dense family order. */
    fun componentAt(index: Int): A = cache.componentAt(index)

    /** Iterates each matching entity and component without allocating an intermediate list. */
    inline fun forEach(block: (Entity, A) -> Unit) {
        cache.forEach(block)
    }

    /** Iterates each matching component without allocating an intermediate list. */
    inline fun forEachComponent(block: (A) -> Unit) {
        cache.forEachComponent(block)
    }
}

/**
 * Read-only view of a maintained two-component family.
 *
 * Membership is updated incrementally as [World] changes. Prefer [forEach] or
 * [forEachComponents] on hot paths so tag columns remain payload-free.
 *
 * Structural changes that affect this family must not be made from an iteration callback.
 */
class Family2<A : Any, B : Any> @PublishedApi internal constructor(
    @PublishedApi internal val cache: Family2Cache<A, B>,
) {
    /** Number of entities currently matching this family. */
    val size: Int get() = cache.size

    /**
     * Returns the first dense component array.
     *
     * Only indices in `0 until [size]` are live. If `A` is an [EcsTag], this compatibility
     * API lazily materializes repeated singleton references.
     */
    fun componentsA(): Array<A> = cache.componentsA()

    /**
     * Returns the second dense component array.
     *
     * Only indices in `0 until [size]` are live. If `B` is an [EcsTag], this compatibility
     * API lazily materializes repeated singleton references.
     */
    fun componentsB(): Array<B> = cache.componentsB()

    /** Returns the first component at [index] in dense family order. */
    fun componentA(index: Int): A = cache.componentA(index)

    /** Returns the second component at [index] in dense family order. */
    fun componentB(index: Int): B = cache.componentB(index)

    /** Iterates every matching entity and component pair without an intermediate list. */
    inline fun forEach(block: (Entity, A, B) -> Unit) {
        cache.forEach(block)
    }

    /** Iterates every matching component pair without an intermediate list. */
    inline fun forEachComponents(block: (A, B) -> Unit) {
        cache.forEachComponents(block)
    }
}
