/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.math

/**
 * A grow-on-demand linear scratch pool for frame-scoped object reuse.
 *
 * In real-time rendering and simulation loops, systems frequently need a dynamic number of
 * scratch objects (e.g. bounding boxes, transformation matrices, draw commands) within a single
 * frame. Allocating these dynamically causes GC pressure, while holding a single scratch field
 * is insufficient when multiple scratch values must coexist across a loop or hierarchy.
 *
 * [ScratchPool] maintains a grow-only contiguous list of reusable instances. Callers retrieve
 * instances sequentially via [obtain] each frame and call [reset] once at the frame boundary to
 * rewind the active pointer to 0. In steady state, [obtain] and [reset] perform zero heap allocations.
 *
 * @param T The pooled object type.
 * @param initialCapacity Initial number of slots to pre-allocate.
 * @param resetInstance Optional callback invoked on active instances when [reset] is called.
 * @param factory Factory function used to instantiate new instances when capacity is exhausted.
 */
class ScratchPool<T>(
    initialCapacity: Int = DEFAULT_CAPACITY,
    private val resetInstance: ((T) -> Unit)? = null,
    private val factory: () -> T,
) {
    constructor(factory: () -> T) : this(DEFAULT_CAPACITY, null, factory)

    constructor(initialCapacity: Int, factory: () -> T) : this(initialCapacity, null, factory)

    @PublishedApi
    internal val items = ArrayList<T>(initialCapacity)

    /** Number of active instances obtained in the current frame/cycle. */
    var count: Int = 0
        private set

    /** Total number of allocated instances currently retained in the pool. */
    val capacity: Int get() = items.size

    /**
     * Obtains the next available pooled instance, creating a new one via [factory]
     * only if the pool has not yet reached the required capacity.
     */
    fun obtain(): T {
        if (count < items.size) {
            return items[count++]
        }
        val instance = factory()
        items.add(instance)
        count++
        return instance
    }

    /**
     * Obtains the next available pooled instance and configures it using [init].
     */
    inline fun obtain(init: (T) -> Unit): T {
        val instance = obtain()
        init(instance)
        return instance
    }

    /**
     * Accesses the pooled instance at [index], creating instances up to [index] if needed.
     */
    operator fun get(index: Int): T {
        while (items.size <= index) {
            items.add(factory())
        }
        return items[index]
    }

    /**
     * Rewinds the active instance counter back to 0 for the next frame.
     *
     * If a [resetInstance] callback was provided, it is invoked on all instances that were
     * active during the preceding cycle before rewinding.
     */
    fun reset() {
        val onReset = resetInstance
        if (onReset != null) {
            var i = 0
            while (i < count) {
                onReset(items[i])
                i++
            }
        }
        count = 0
    }

    /**
     * Clears all pooled instances and resets [count] to 0, allowing retained references
     * to be garbage collected (e.g. during scene destruction or subsystem teardown).
     */
    fun clear() {
        items.clear()
        count = 0
    }

    /**
     * Iterates over all instances that were obtained during the current frame cycle without allocating.
     */
    inline fun forEachActive(action: (T) -> Unit) {
        var i = 0
        while (i < count) {
            action(items[i])
            i++
        }
    }

    companion object {
        const val DEFAULT_CAPACITY: Int = 16
    }
}
