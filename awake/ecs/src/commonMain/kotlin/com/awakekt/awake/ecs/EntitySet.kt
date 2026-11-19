/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

/**
 * Unboxed hash set of [Entity] handles with dense linear iteration and O(1) membership checks.
 *
 * Stores packed 64-bit entity handles in a compact [LongArray] for cache-friendly [forEach]
 * iteration alongside a power-of-two open-addressing index table. Removals use backward-shift
 * deletion rather than tombstones, so repeated add/remove churn across server ticks never degrades
 * lookup probe length or requires rehash sweeps.
 *
 * Because full 64-bit [Entity.packed] handles (ID + generation) are stored and hashed, arbitrary
 * sparse entity IDs keep memory proportional to [size] and handles sharing an ID across distinct
 * generations never alias.
 *
 * @param initialCapacity Expected number of entities to hold without rehashing; `0` defers
 *   allocation until the first [add].
 */
@Suppress("TooManyFunctions") // Collection primitive mirroring the core MutableSet operations without boxing.
class EntitySet(initialCapacity: Int = 0) {
    init {
        require(initialCapacity >= 0) { "Initial capacity must be non-negative: $initialCapacity" }
    }

    @PublishedApi
    internal var dense: LongArray = if (initialCapacity == 0) EMPTY_LONGS else LongArray(initialCapacity)

    @PublishedApi
    internal var count: Int = 0

    private var slots: IntArray = if (initialCapacity == 0) {
        EMPTY_SLOTS
    } else {
        IntArray(tableSizeFor(initialCapacity)) { EMPTY }
    }
    private var mask: Int = if (slots.isEmpty()) 0 else slots.size - 1

    /** Number of distinct entities currently in this set. */
    val size: Int get() = count

    /** Returns `true` when this set contains no entities. */
    val isEmpty: Boolean get() = count == 0

    /** Returns `true` when this set contains at least one entity. */
    val isNotEmpty: Boolean get() = count > 0

    /**
     * Returns the entity stored at dense slot [index] (`0 until size`).
     *
     * Iteration order is not guaranteed to remain stable across removals.
     *
     * @throws IndexOutOfBoundsException if [index] is outside `0 until size`.
     */
    fun entityAt(index: Int): Entity {
        if (index < 0 || index >= count) {
            throw IndexOutOfBoundsException("Index $index out of bounds for length $count")
        }
        return Entity(dense[index])
    }

    /** Returns `true` if [entity] is present in this set. */
    operator fun contains(entity: Entity): Boolean = count > 0 && findSlot(entity.packed) >= 0

    /**
     * Adds [entity] to this set if not already present.
     *
     * @return `true` if [entity] was newly inserted, or `false` if already present.
     */
    fun add(entity: Entity): Boolean {
        val packed = entity.packed
        val slot = if (slots.isEmpty()) EMPTY else findSlotOrEmpty(packed)
        if (slot >= 0 && slots[slot] != EMPTY) return false
        val nextCount = count + 1
        if (slot < 0 || nextCount > dense.size || nextCount * 2 > slots.size) {
            ensureCapacity(nextCount)
            insertNew(packed)
        } else {
            dense[count] = packed
            slots[slot] = count
            count = nextCount
        }
        return true
    }

    /** Adds every entity from [other] without boxing. Returns `true` if this set changed. */
    fun addAll(other: EntityList): Boolean {
        val otherCount = other.count
        if (otherCount == 0) return false
        ensureCapacity(count + otherCount)
        val before = count
        val otherElements = other.elements
        for (index in 0 until otherCount) {
            add(Entity(otherElements[index]))
        }
        return count != before
    }

    /** Adds every entity from [other] without boxing. Returns `true` if this set changed. */
    fun addAll(other: EntitySet): Boolean {
        val otherCount = other.count
        if (otherCount == 0) return false
        ensureCapacity(count + otherCount)
        val before = count
        val otherDense = other.dense
        for (index in 0 until otherCount) {
            add(Entity(otherDense[index]))
        }
        return count != before
    }

    /** Adds every entity from [entities]. Returns `true` if this set changed. */
    fun addAll(entities: Iterable<Entity>): Boolean {
        if (entities is Collection<Entity>) {
            ensureCapacity(count + entities.size)
        }
        val before = count
        for (entity in entities) {
            add(entity)
        }
        return count != before
    }

    /**
     * Removes [entity] from this set if present.
     *
     * Uses swap-remove on the dense array and backward-shift deletion on the hash table so probe
     * chains stay clean without tombstones.
     *
     * @return `true` if [entity] was present and removed, or `false` otherwise.
     */
    fun remove(entity: Entity): Boolean {
        val slot = if (count == 0) EMPTY else findSlot(entity.packed)
        if (slot < 0) return false
        val denseIndex = slots[slot]
        val lastIndex = count - 1
        if (denseIndex != lastIndex) {
            val movedPacked = dense[lastIndex]
            val movedSlot = findSlot(movedPacked)
            dense[denseIndex] = movedPacked
            slots[movedSlot] = denseIndex
        }
        count = lastIndex
        deleteSlotBackwardShift(slot)
        return true
    }

    /** Removes all entities while retaining backing storage for zero-allocation reuse. */
    fun clear() {
        if (count == 0) return
        slots.fill(EMPTY)
        count = 0
    }

    /** Ensures this set can hold at least [minCapacity] entities without resizing or rehashing. */
    fun ensureCapacity(minCapacity: Int) {
        if (minCapacity > dense.size) {
            val grownDense = maxOf(minCapacity, maxOf(DEFAULT_CAPACITY, dense.size * GROWTH_FACTOR))
            dense = dense.copyOf(grownDense)
        }
        val requiredTable = tableSizeFor(minCapacity)
        if (requiredTable > slots.size) {
            rehash(requiredTable)
        }
    }

    /** Invokes [action] for each entity in this set without boxing. */
    inline fun forEach(action: (Entity) -> Unit) {
        val local = dense
        val limit = count
        for (index in 0 until limit) {
            action(Entity(local[index]))
        }
    }

    /** Removes every entity matching [predicate] without boxing. Returns `true` if modified. */
    inline fun removeAll(predicate: (Entity) -> Boolean): Boolean {
        val originalCount = count
        var index = 0
        while (index < count) {
            val entity = Entity(dense[index])
            if (predicate(entity)) {
                remove(entity)
            } else {
                index += 1
            }
        }
        return count != originalCount
    }

    /** Retains only entities matching [predicate] without boxing. Returns `true` if modified. */
    inline fun retainAll(predicate: (Entity) -> Boolean): Boolean {
        val originalCount = count
        var index = 0
        while (index < count) {
            val entity = Entity(dense[index])
            if (!predicate(entity)) {
                remove(entity)
            } else {
                index += 1
            }
        }
        return count != originalCount
    }

    /** Returns `true` if at least one entity matches [predicate]. */
    inline fun any(predicate: (Entity) -> Boolean): Boolean {
        val local = dense
        val limit = count
        for (index in 0 until limit) {
            if (predicate(Entity(local[index]))) return true
        }
        return false
    }

    /** Returns `true` if every entity matches [predicate] (or this set is empty). */
    inline fun all(predicate: (Entity) -> Boolean): Boolean {
        val local = dense
        val limit = count
        for (index in 0 until limit) {
            if (!predicate(Entity(local[index]))) return false
        }
        return true
    }

    /** Returns a copy of the packed entity handles currently in this set. */
    fun toLongArray(): LongArray = dense.copyOf(count)

    /** Materializes a standard [List] snapshot of the entities in this set. */
    fun toList(): List<Entity> = List(count) { index -> Entity(dense[index]) }

    /** Materializes a standard [Set] snapshot of the entities in this set. */
    fun toSet(): Set<Entity> = HashSet<Entity>(count).also { result ->
        for (index in 0 until count) {
            result.add(Entity(dense[index]))
        }
    }

    /** Returns `true` when [other] is an [EntitySet] containing the exact same entities. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EntitySet || count != other.count) return false
        for (index in 0 until count) {
            if (!other.contains(Entity(dense[index]))) return false
        }
        return true
    }

    /** Returns an order-independent hash code over the packed entity handles. */
    override fun hashCode(): Int {
        var result = 0
        for (index in 0 until count) {
            result += dense[index].hashCode()
        }
        return result
    }

    /** Returns a readable representation of this set's entities. */
    override fun toString(): String = buildString {
        append('[')
        for (index in 0 until count) {
            if (index > 0) append(", ")
            append(Entity(dense[index]))
        }
        append(']')
    }

    private fun insertNew(packed: Long) {
        val table = slots
        val tableMask = mask
        var slot = hashPacked(packed) and tableMask
        while (table[slot] != EMPTY) {
            slot = (slot + 1) and tableMask
        }
        val newIndex = count
        dense[newIndex] = packed
        table[slot] = newIndex
        count = newIndex + 1
    }

    private fun findSlot(packed: Long): Int {
        val slot = findSlotOrEmpty(packed)
        return if (slots[slot] == EMPTY) EMPTY else slot
    }

    private fun findSlotOrEmpty(packed: Long): Int {
        val table = slots
        val tableMask = mask
        var slot = hashPacked(packed) and tableMask
        while (true) {
            val denseIndex = table[slot]
            if (denseIndex == EMPTY || dense[denseIndex] == packed) return slot
            slot = (slot + 1) and tableMask
        }
    }

    private fun deleteSlotBackwardShift(startHole: Int) {
        val table = slots
        val tableMask = mask
        var hole = startHole
        var next = (hole + 1) and tableMask
        while (true) {
            val candidateDense = table[next]
            if (candidateDense == EMPTY) {
                table[hole] = EMPTY
                return
            }
            val ideal = hashPacked(dense[candidateDense]) and tableMask
            val distToHole = (hole - ideal) and tableMask
            val distToNext = (next - ideal) and tableMask
            if (distToHole < distToNext) {
                table[hole] = candidateDense
                hole = next
            }
            next = (next + 1) and tableMask
        }
    }

    private fun rehash(newTableSize: Int) {
        val newSlots = IntArray(newTableSize) { EMPTY }
        val newMask = newTableSize - 1
        val localDense = dense
        for (denseIndex in 0 until count) {
            var slot = hashPacked(localDense[denseIndex]) and newMask
            while (newSlots[slot] != EMPTY) {
                slot = (slot + 1) and newMask
            }
            newSlots[slot] = denseIndex
        }
        slots = newSlots
        mask = newMask
    }

    private companion object {
        const val EMPTY = -1
        const val DEFAULT_CAPACITY = 16
        const val MIN_TABLE_SIZE = 32
        const val GROWTH_FACTOR = 2
        const val HASH_SHIFT = 33
        const val HASH_MIX_MULTIPLIER = -0xae502812aa7333L
        val EMPTY_LONGS = LongArray(0)
        val EMPTY_SLOTS = IntArray(0)

        fun hashPacked(packed: Long): Int {
            var mixed = packed xor (packed ushr HASH_SHIFT)
            mixed *= HASH_MIX_MULTIPLIER
            mixed = mixed xor (mixed ushr HASH_SHIFT)
            return mixed.toInt()
        }

        /** Smallest power of two keeping load factor `<= 0.5` (and at least [MIN_TABLE_SIZE]). */
        fun tableSizeFor(elementCount: Int): Int {
            if (elementCount <= 0) return MIN_TABLE_SIZE
            val minSlots = maxOf(MIN_TABLE_SIZE, elementCount * 2)
            var power = MIN_TABLE_SIZE
            while (power < minSlots && power < (1 shl 30)) {
                power = power shl 1
            }
            return power
        }
    }
}
