/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/** Payload handling selected once by a component type's first value. */
internal sealed class ComponentValueStorage<T : Any> {
    abstract val kind: ComponentStorageKind
    abstract val hasPayloadStorage: Boolean
    abstract fun validate(value: T)
    abstract fun valueAt(index: Int): T
    abstract fun insert(index: Int, value: T)
    abstract fun replace(index: Int, value: T): T
    abstract fun remove(removedIndex: Int, lastIndex: Int): T
    abstract fun clear(size: Int)
}

internal class PayloadColumn<T : Any>(type: KClass<T>) : ComponentValueStorage<T>() {
    private var values: Array<Any?> = newComponentArray(type, DEFAULT_CAPACITY)

    override val kind: ComponentStorageKind get() = ComponentStorageKind.SparseSet
    override val hasPayloadStorage: Boolean get() = true

    override fun validate(value: T) {
        require(value !is EcsTag) { "EcsTag component types must always use their singleton tag value: ${value::class}" }
    }

    // Safe: `values` array was allocated via newComponentArray(type) where type is KClass<T>.
    @Suppress("UNCHECKED_CAST")
    private inline val typedValues: Array<T> get() = values as Array<T>

    override fun valueAt(index: Int): T = typedValues[index]

    override fun insert(index: Int, value: T) {
        ensureCapacity(index + 1)
        values[index] = value
    }

    override fun replace(index: Int, value: T): T {
        val previous = typedValues[index]
        values[index] = value
        return previous
    }

    override fun remove(removedIndex: Int, lastIndex: Int): T {
        val removed = typedValues[removedIndex]
        if (removedIndex != lastIndex) {
            values[removedIndex] = values[lastIndex]
        }
        values[lastIndex] = null
        return removed
    }

    override fun clear(size: Int) {
        values.fill(null, fromIndex = 0, toIndex = size)
    }

    internal fun valuesForIteration(): Array<T> = typedValues

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity > values.size) {
            values = values.copyOf(maxOf(requiredCapacity, values.size * CAPACITY_GROWTH_FACTOR))
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
        const val CAPACITY_GROWTH_FACTOR = 2
    }
}

internal class SingletonTagValue<T : Any>(private val tag: T) : ComponentValueStorage<T>() {
    override val kind: ComponentStorageKind get() = ComponentStorageKind.TagSparseSet
    override val hasPayloadStorage: Boolean get() = false
    override fun validate(value: T) = requireSingleton(value)
    override fun valueAt(index: Int): T = tag
    override fun insert(index: Int, value: T) = requireSingleton(value)

    override fun replace(index: Int, value: T): T {
        requireSingleton(value)
        return tag
    }

    override fun remove(removedIndex: Int, lastIndex: Int): T = tag
    override fun clear(size: Int) = Unit
    internal fun valueForIteration(): T = tag

    private fun requireSingleton(value: T) {
        require(tag === value) { "EcsTag implementations must be singleton objects: ${tag::class}" }
    }
}
