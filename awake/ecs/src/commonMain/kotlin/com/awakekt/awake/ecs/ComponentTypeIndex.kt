/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.concurrent.atomics.AtomicReference
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * The process-wide index of the component class [key] stands for, the same in every [World] and
 * on every thread. [key] is what [componentTypeKey] or [componentTypeKeyOf] gives for the class.
 *
 * An index is a slot in each world's array of type ids, so a world turns a class into its own
 * [ComponentTypeId] by reading an array instead of hashing the class. Indices start at 0, are
 * dense, and are never reused; the first call for a class assigns its index.
 */
internal expect fun componentTypeIndexOf(key: Any): Int

/**
 * Whether [componentTypeIndexOf] is cheaper than hashing [key] into a world's own map. True on the
 * JVM, where a `ClassValue` holds the index on the class itself. Elsewhere the index is a map lookup
 * of its own, so a reified `World.get` keeps the world's map and only [ComponentType] handles,
 * which carry their index, read the array.
 */
internal expect val componentTypeIndexIsCheap: Boolean

/**
 * The shared form of [componentTypeIndexOf], for platforms with no per-class slot: a map from key to
 * index, replaced whole on each new class. It is only written when a class is first seen, which is
 * rare, so a reader never takes a lock.
 */
@OptIn(ExperimentalAtomicApi::class)
internal object SharedComponentTypeIndex {
    private val indices = AtomicReference<Map<Any, Int>>(emptyMap())

    fun indexOf(key: Any): Int {
        while (true) {
            val current = indices.load()
            current[key]?.let { return it }
            val index = current.size
            if (indices.compareAndSet(current, current + (key to index))) return index
        }
    }
}
