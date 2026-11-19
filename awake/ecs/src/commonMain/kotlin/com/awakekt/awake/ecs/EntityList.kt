/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

/**
 * Growable list of [Entity] handles backed directly by a [LongArray] of packed handles.
 *
 * Because [Entity] is a `value class`, storing handles in a standard `MutableList<Entity>` boxes
 * every handle on insertion and iteration. `EntityList` stores the raw 64-bit packed values inline
 * so systems can reuse scratch buffers, queues, and candidate lists across ticks without boxing or
 * per-frame allocations.
 *
 * @param initialCapacity Initial backing array length; `0` defers allocation until the first [add].
 */
@Suppress("TooManyFunctions") // Collection primitive mirroring the core MutableList operations without boxing.
class EntityList(initialCapacity: Int = 0) {
    init {
        require(initialCapacity >= 0) { "Initial capacity must be non-negative: $initialCapacity" }
    }

    @PublishedApi
    internal var elements: LongArray = if (initialCapacity == 0) EMPTY_LONGS else LongArray(initialCapacity)

    @PublishedApi
    internal var count: Int = 0

    /** Number of entities currently in this list. */
    val size: Int get() = count

    /** Returns `true` when this list contains no entities. */
    val isEmpty: Boolean get() = count == 0

    /** Returns `true` when this list contains at least one entity. */
    val isNotEmpty: Boolean get() = count > 0

    /** Index of the last entity in this list, or `-1` when empty. */
    val lastIndex: Int get() = count - 1

    /** Valid index range for this list (`0 until size`). */
    val indices: IntRange get() = 0 until count

    /**
     * Returns the entity at [index].
     *
     * @throws IndexOutOfBoundsException if [index] is outside `0 until size`.
     */
    operator fun get(index: Int): Entity {
        checkIndex(index)
        return Entity(elements[index])
    }

    /**
     * Replaces the entity at [index] with [entity] and returns the previous entity.
     *
     * @throws IndexOutOfBoundsException if [index] is outside `0 until size`.
     */
    operator fun set(index: Int, entity: Entity): Entity {
        checkIndex(index)
        val previous = elements[index]
        elements[index] = entity.packed
        return Entity(previous)
    }

    /**
     * Returns the first entity in this list.
     *
     * @throws NoSuchElementException if this list is empty.
     */
    fun first(): Entity {
        if (count == 0) throw NoSuchElementException("EntityList is empty.")
        return Entity(elements[0])
    }

    /**
     * Returns the last entity in this list.
     *
     * @throws NoSuchElementException if this list is empty.
     */
    fun last(): Entity {
        if (count == 0) throw NoSuchElementException("EntityList is empty.")
        return Entity(elements[count - 1])
    }

    /** Appends [entity] to the end of this list. */
    fun add(entity: Entity) {
        ensureCapacity(count + 1)
        elements[count] = entity.packed
        count += 1
    }

    /** Appends every entity from [other] in order without boxing. */
    fun addAll(other: EntityList) {
        val otherCount = other.count
        if (otherCount == 0) return
        ensureCapacity(count + otherCount)
        other.elements.copyInto(elements, destinationOffset = count, startIndex = 0, endIndex = otherCount)
        count += otherCount
    }

    /** Appends every entity from [other] without boxing. */
    fun addAll(other: EntitySet) {
        val otherCount = other.count
        if (otherCount == 0) return
        ensureCapacity(count + otherCount)
        other.dense.copyInto(elements, destinationOffset = count, startIndex = 0, endIndex = otherCount)
        count += otherCount
    }

    /** Appends every entity from [entities]. */
    fun addAll(entities: Iterable<Entity>) {
        if (entities is Collection<Entity>) {
            ensureCapacity(count + entities.size)
        }
        for (entity in entities) {
            add(entity)
        }
    }

    /** Returns the first index of [entity], or `-1` if absent. */
    fun indexOf(entity: Entity): Int {
        val packed = entity.packed
        val local = elements
        for (index in 0 until count) {
            if (local[index] == packed) return index
        }
        return ABSENT
    }

    /** Returns `true` if [entity] is present in this list. */
    operator fun contains(entity: Entity): Boolean = indexOf(entity) >= 0

    /**
     * Removes and returns the entity at [index], shifting subsequent elements left to preserve order.
     *
     * @throws IndexOutOfBoundsException if [index] is outside `0 until size`.
     */
    fun removeAt(index: Int): Entity {
        checkIndex(index)
        val removed = elements[index]
        val nextIndex = index + 1
        if (nextIndex < count) {
            elements.copyInto(elements, destinationOffset = index, startIndex = nextIndex, endIndex = count)
        }
        count -= 1
        return Entity(removed)
    }

    /**
     * Removes and returns the entity at [index] in O(1) time by moving the last entity into [index].
     *
     * Does not preserve list order.
     *
     * @throws IndexOutOfBoundsException if [index] is outside `0 until size`.
     */
    fun swapRemoveAt(index: Int): Entity {
        checkIndex(index)
        val removed = elements[index]
        val last = count - 1
        if (index != last) {
            elements[index] = elements[last]
        }
        count = last
        return Entity(removed)
    }

    /** Removes the first occurrence of [entity], preserving order. Returns `true` if removed. */
    fun remove(entity: Entity): Boolean {
        val index = indexOf(entity)
        if (index < 0) return false
        removeAt(index)
        return true
    }

    /**
     * Removes the first occurrence of [entity] in O(1) swap time after finding it.
     *
     * Does not preserve list order. Returns `true` if removed.
     */
    fun swapRemove(entity: Entity): Boolean {
        val index = indexOf(entity)
        if (index < 0) return false
        swapRemoveAt(index)
        return true
    }

    /**
     * Removes and returns the last entity in this list in O(1) time.
     *
     * @throws NoSuchElementException if this list is empty.
     */
    fun removeLast(): Entity {
        if (count == 0) throw NoSuchElementException("EntityList is empty.")
        count -= 1
        return Entity(elements[count])
    }

    /** Removes all entities while retaining backing storage for zero-allocation reuse. */
    fun clear() {
        count = 0
    }

    /** Ensures this list can hold at least [minCapacity] entities without resizing. */
    fun ensureCapacity(minCapacity: Int) {
        if (minCapacity <= elements.size) return
        val grown = maxOf(minCapacity, maxOf(DEFAULT_CAPACITY, elements.size * GROWTH_FACTOR))
        elements = elements.copyOf(grown)
    }

    /** Trims the backing array to match [size]. */
    fun trimToSize() {
        if (elements.size > count) {
            elements = if (count == 0) EMPTY_LONGS else elements.copyOf(count)
        }
    }

    /** Invokes [action] for each entity in order without boxing. */
    inline fun forEach(action: (Entity) -> Unit) {
        val local = elements
        val limit = count
        for (index in 0 until limit) {
            action(Entity(local[index]))
        }
    }

    /** Invokes [action] with each index and entity in order without boxing. */
    inline fun forEachIndexed(action: (index: Int, entity: Entity) -> Unit) {
        val local = elements
        val limit = count
        for (index in 0 until limit) {
            action(index, Entity(local[index]))
        }
    }

    /** Removes every entity matching [predicate] in a single pass, preserving order. Returns `true` if modified. */
    inline fun removeAll(predicate: (Entity) -> Boolean): Boolean {
        val local = elements
        val originalCount = count
        var write = 0
        for (read in 0 until originalCount) {
            val packed = local[read]
            if (!predicate(Entity(packed))) {
                local[write] = packed
                write += 1
            }
        }
        count = write
        return write != originalCount
    }

    /** Retains only entities matching [predicate] in a single pass, preserving order. Returns `true` if modified. */
    inline fun retainAll(predicate: (Entity) -> Boolean): Boolean {
        val local = elements
        val originalCount = count
        var write = 0
        for (read in 0 until originalCount) {
            val packed = local[read]
            if (predicate(Entity(packed))) {
                local[write] = packed
                write += 1
            }
        }
        count = write
        return write != originalCount
    }

    /** Returns `true` if at least one entity matches [predicate]. */
    inline fun any(predicate: (Entity) -> Boolean): Boolean {
        val local = elements
        val limit = count
        for (index in 0 until limit) {
            if (predicate(Entity(local[index]))) return true
        }
        return false
    }

    /** Returns `true` if every entity matches [predicate] (or this list is empty). */
    inline fun all(predicate: (Entity) -> Boolean): Boolean {
        val local = elements
        val limit = count
        for (index in 0 until limit) {
            if (!predicate(Entity(local[index]))) return false
        }
        return true
    }

    /** Returns a copy of the packed entity handles in order. */
    fun toLongArray(): LongArray = elements.copyOf(count)

    /** Materializes a standard [List] snapshot of the entities in order. */
    fun toList(): List<Entity> = List(count) { index -> Entity(elements[index]) }

    /** Returns `true` when [other] is an [EntityList] containing the same entities in the same order. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntityList || count != other.count) return false
        for (index in 0 until count) {
            if (elements[index] != other.elements[index]) return false
        }
        return true
    }

    /** Returns an order-sensitive hash code over the packed entity handles. */
    override fun hashCode(): Int {
        var result = 1
        for (index in 0 until count) {
            result = 31 * result + elements[index].hashCode()
        }
        return result
    }

    /** Returns a readable representation of this list's entities. */
    override fun toString(): String = buildString {
        append('[')
        for (index in 0 until count) {
            if (index > 0) append(", ")
            append(Entity(elements[index]))
        }
        append(']')
    }

    private fun checkIndex(index: Int) {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("Index $index out of bounds for length $count")
        }
    }

    private companion object {
        const val ABSENT = -1
        const val DEFAULT_CAPACITY = 16
        const val GROWTH_FACTOR = 2
        val EMPTY_LONGS = LongArray(0)
    }
}
