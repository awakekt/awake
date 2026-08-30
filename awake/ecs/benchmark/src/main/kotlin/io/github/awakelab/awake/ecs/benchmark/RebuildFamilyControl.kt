/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs.benchmark

import io.github.awakelab.awake.ecs.ComponentStore
import io.github.awakelab.awake.ecs.ComponentTypeId
import io.github.awakelab.awake.ecs.EcsTag
import io.github.awakelab.awake.ecs.Entity

/**
 * Benchmark-only replica of a maintained family, mirroring `Family1Cache` and `Family2Cache`.
 *
 * There is no production batch API: `:awake:ecs` cannot defer family maintenance, and its family
 * caches are `internal`. Replicating them here lets both the incremental and the forced-rebuild
 * strategy run against the same cache, so a head-to-head difference is the strategy alone. Decision
 * context is in `docs/tasks/2026-08-21-ecs-adaptive-bulk-mutation-plan.md`.
 */
internal abstract class ControlFamily {
    /** Number of live dense entries. */
    abstract val size: Int

    /** Incremental strategy: one maintenance step per store mutation, as `FamilyRegistry` does. */
    abstract fun onAdd(entity: Entity, typeId: ComponentTypeId, component: Any)

    /** Incremental strategy counterpart for a removal. */
    abstract fun onRemove(entity: Entity, typeId: ComponentTypeId)

    /** Rebuild strategy: discard dense state and refill once from the smallest required store. */
    abstract fun rebuild()

    /** Entity at dense [index]. */
    abstract fun entityAt(index: Int): Entity

    /** Dense index of [entity], or [ControlSparseIndex.ABSENT]. */
    abstract fun indexOf(entity: Entity): Int

    /** First column value at dense [index]. */
    abstract fun valueAt(index: Int): Any

    /** Second column value at dense [index], or `null` for a one-arity family. */
    open fun secondValueAt(index: Int): Any? = null
}

/** Replica of `Family1Cache`. */
internal class ControlFamily1<A : Any>(
    private val typeId: ComponentTypeId,
    private val store: ComponentStore<A>,
) : ControlFamily() {
    private var entities = LongArray(DEFAULT_FAMILY_CAPACITY)
    private val values = ControlValueColumn()
    private val sparse = ControlSparseIndex()
    private var count = 0

    override val size: Int get() = count

    override fun onAdd(entity: Entity, typeId: ComponentTypeId, component: Any) {
        if (this.typeId == typeId) append(entity, component)
    }

    override fun onRemove(entity: Entity, typeId: ComponentTypeId) {
        if (this.typeId == typeId) removeAt(indexOf(entity))
    }

    override fun rebuild() {
        reset()
        store.forEach { entity, component -> append(entity, component) }
    }

    override fun entityAt(index: Int): Entity = Entity(entities[index])

    override fun indexOf(entity: Entity): Int {
        val dense = sparse.get(entity.id)
        return if (dense >= 0 && dense < count) dense else ControlSparseIndex.ABSENT
    }

    override fun valueAt(index: Int): Any = values.valueAt(index)

    private fun append(entity: Entity, component: Any) {
        ensureCapacity(count + 1)
        sparse.set(entity.id, count)
        entities[count] = entity.packed
        values.insert(count, component, entities.size)
        count += 1
    }

    private fun removeAt(index: Int) {
        if (index < 0) return
        val removedEntityId = entities[index].toInt()
        val lastIndex = count - 1
        if (index != lastIndex) {
            val lastEntity = entities[lastIndex]
            entities[index] = lastEntity
            sparse.set(lastEntity.toInt(), index)
        }
        values.remove(index, lastIndex)
        count -= 1
        sparse.remove(removedEntityId)
    }

    // Clears the whole sparse map: a slot left by a non-member still addresses a live dense index.
    private fun reset() {
        values.clear(count)
        sparse.clear()
        count = 0
    }

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= entities.size) return
        val newCapacity = maxOf(requiredCapacity, entities.size * FAMILY_CAPACITY_GROWTH_FACTOR)
        entities = entities.copyOf(newCapacity)
        values.ensureCapacity(newCapacity)
    }
}

/** Replica of `Family2Cache`, which carries the same suppression for the same two-column shape. */
@Suppress("TooManyFunctions")
internal class ControlFamily2<A : Any, B : Any>(
    private val typeIdA: ComponentTypeId,
    private val storeA: ComponentStore<A>,
    private val typeIdB: ComponentTypeId,
    private val storeB: ComponentStore<B>,
) : ControlFamily() {
    private var entities = LongArray(DEFAULT_FAMILY_CAPACITY)
    private val valuesA = ControlValueColumn()
    private val valuesB = ControlValueColumn()
    private val sparse = ControlSparseIndex()
    private var count = 0

    override val size: Int get() = count

    override fun onAdd(entity: Entity, typeId: ComponentTypeId, component: Any) {
        if (typeId == typeIdA) {
            val componentB = storeB.get(entity) ?: return
            append(entity, component, componentB)
        } else if (typeId == typeIdB) {
            val componentA = storeA.get(entity) ?: return
            append(entity, componentA, component)
        }
    }

    override fun onRemove(entity: Entity, typeId: ComponentTypeId) {
        if (typeId == typeIdA || typeId == typeIdB) removeAt(indexOf(entity))
    }

    /** Mirrors `FamilyRegistry.fillFamily`: drive from the smaller store, probe the other. */
    override fun rebuild() {
        reset()
        if (storeA.size <= storeB.size) {
            storeA.forEach { entity, componentA ->
                storeB.get(entity)?.let { componentB -> append(entity, componentA, componentB) }
            }
        } else {
            storeB.forEach { entity, componentB ->
                storeA.get(entity)?.let { componentA -> append(entity, componentA, componentB) }
            }
        }
    }

    override fun entityAt(index: Int): Entity = Entity(entities[index])

    override fun indexOf(entity: Entity): Int {
        val dense = sparse.get(entity.id)
        return if (dense >= 0 && dense < count) dense else ControlSparseIndex.ABSENT
    }

    override fun valueAt(index: Int): Any = valuesA.valueAt(index)

    override fun secondValueAt(index: Int): Any = valuesB.valueAt(index)

    private fun append(entity: Entity, componentA: Any, componentB: Any) {
        ensureCapacity(count + 1)
        sparse.set(entity.id, count)
        entities[count] = entity.packed
        valuesA.insert(count, componentA, entities.size)
        valuesB.insert(count, componentB, entities.size)
        count += 1
    }

    private fun removeAt(index: Int) {
        if (index < 0) return
        val removedEntityId = entities[index].toInt()
        val lastIndex = count - 1
        if (index != lastIndex) {
            val lastEntity = entities[lastIndex]
            entities[index] = lastEntity
            sparse.set(lastEntity.toInt(), index)
        }
        valuesA.remove(index, lastIndex)
        valuesB.remove(index, lastIndex)
        count -= 1
        sparse.remove(removedEntityId)
    }

    private fun reset() {
        valuesA.clear(count)
        valuesB.clear(count)
        sparse.clear()
        count = 0
    }

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= entities.size) return
        val newCapacity = maxOf(requiredCapacity, entities.size * FAMILY_CAPACITY_GROWTH_FACTOR)
        entities = entities.copyOf(newCapacity)
        valuesA.ensureCapacity(newCapacity)
        valuesB.ensureCapacity(newCapacity)
    }
}

/**
 * Routes mutations to the families that care about a component type, replicating `FamilyRegistry`.
 *
 * Holds both strategies: [addComponent]/[removeComponent] maintain immediately, while
 * [markDirty]/[flush] defer to one rebuild per affected family.
 */
internal class ControlFamilyRegistry {
    private val families = ArrayList<ControlFamily>()
    private var familiesByTypeId = arrayOfNulls<Array<ControlFamily>>(INITIAL_TYPE_CAPACITY)
    private var dirtyBitsByTypeId = LongArray(INITIAL_TYPE_CAPACITY)
    private var dirty = 0L

    /** Number of registered families. */
    val familyCount: Int get() = families.size

    /** Family registered at [index], in registration order. */
    fun familyAt(index: Int): ControlFamily = families[index]

    /** Registers [family] as a listener of [typeIdA] and, for a two-arity family, [typeIdB]. */
    fun register(family: ControlFamily, typeIdA: ComponentTypeId, typeIdB: ComponentTypeId? = null) {
        require(families.size < MAX_FAMILIES) { "Control registry holds at most $MAX_FAMILIES families" }
        val bit = 1L shl families.size
        families.add(family)
        index(family, typeIdA, bit)
        typeIdB?.let { index(family, it, bit) }
    }

    /** Populates every family once, so a benchmark's first invocation starts from a correct cache. */
    fun rebuildAll() {
        families.forEach(ControlFamily::rebuild)
    }

    /** Incremental strategy. */
    fun addComponent(entity: Entity, typeId: ComponentTypeId, component: Any) {
        val caches = cachesFor(typeId) ?: return
        for (index in caches.indices) caches[index].onAdd(entity, typeId, component)
    }

    /** Incremental strategy. */
    fun removeComponent(entity: Entity, typeId: ComponentTypeId) {
        val caches = cachesFor(typeId) ?: return
        for (index in caches.indices) caches[index].onRemove(entity, typeId)
    }

    /**
     * Rebuild strategy, recording half — the per-mutation cost that deferral cannot remove, since
     * the batch still has to learn which families a mutation invalidated.
     */
    fun markDirty(typeId: ComponentTypeId) {
        val id = typeId.value
        if (id < dirtyBitsByTypeId.size) dirty = dirty or dirtyBitsByTypeId[id]
    }

    /** Rebuild strategy, commit half: one rebuild per affected family, regardless of batch size. */
    fun flush() {
        var remaining = dirty
        while (remaining != 0L) {
            families[remaining.countTrailingZeroBits()].rebuild()
            remaining = remaining and (remaining - 1L)
        }
        dirty = 0L
    }

    private fun index(family: ControlFamily, typeId: ComponentTypeId, bit: Long) {
        val id = typeId.value
        ensureTypeCapacity(id)
        val existing = familiesByTypeId[id]
        familiesByTypeId[id] = existing?.plus(family) ?: arrayOf(family)
        dirtyBitsByTypeId[id] = dirtyBitsByTypeId[id] or bit
    }

    private fun cachesFor(typeId: ComponentTypeId): Array<ControlFamily>? {
        val id = typeId.value
        return if (id < familiesByTypeId.size) familiesByTypeId[id] else null
    }

    private fun ensureTypeCapacity(id: Int) {
        if (id < familiesByTypeId.size) return
        val newSize = maxOf(id + 1, familiesByTypeId.size * FAMILY_CAPACITY_GROWTH_FACTOR)
        familiesByTypeId = familiesByTypeId.copyOf(newSize)
        dirtyBitsByTypeId = dirtyBitsByTypeId.copyOf(newSize)
    }

    private companion object {
        const val INITIAL_TYPE_CAPACITY = 16
        const val MAX_FAMILIES = 64
    }
}

/** Reduced copy of `:awake:ecs`'s internal `EntityIndexMap`. */
internal class ControlSparseIndex {
    private var sparse = IntArray(0)

    fun get(id: Int): Int = if (id >= 0 && id < sparse.size) sparse[id] else ABSENT

    fun set(id: Int, denseIndex: Int) {
        if (id >= sparse.size) grow(id)
        sparse[id] = denseIndex
    }

    fun remove(id: Int) {
        if (id >= 0 && id < sparse.size) sparse[id] = ABSENT
    }

    fun clear() {
        sparse.fill(ABSENT)
    }

    private fun grow(id: Int) {
        val previousSize = sparse.size
        val newSize = maxOf(id + 1, maxOf(DEFAULT_CAPACITY, previousSize * FAMILY_CAPACITY_GROWTH_FACTOR))
        sparse = sparse.copyOf(newSize)
        sparse.fill(ABSENT, fromIndex = previousSize, toIndex = newSize)
    }

    companion object {
        const val ABSENT = -1
        private const val DEFAULT_CAPACITY = 16
    }
}

/** Reduced copy of `:awake:ecs`'s internal `FamilyValueColumn`; an [EcsTag] keeps no payload array. */
internal class ControlValueColumn {
    private var values: Array<Any?>? = null
    private var tag: Any? = null

    fun insert(index: Int, value: Any, capacity: Int) {
        if (values == null && tag == null) {
            if (value is EcsTag) tag = value else values = arrayOfNulls(capacity)
        }
        values?.set(index, value)
    }

    fun remove(removedIndex: Int, lastIndex: Int) {
        val localValues = values ?: return
        if (removedIndex != lastIndex) localValues[removedIndex] = localValues[lastIndex]
        localValues[lastIndex] = null
    }

    fun valueAt(index: Int): Any = tag ?: requireNotNull(values?.get(index)) { "No value at dense index $index" }

    fun ensureCapacity(requiredCapacity: Int) {
        val localValues = values ?: return
        if (requiredCapacity > localValues.size) values = localValues.copyOf(requiredCapacity)
    }

    fun clear(count: Int) {
        values?.fill(null, fromIndex = 0, toIndex = count)
    }
}

private const val DEFAULT_FAMILY_CAPACITY = 16
private const val FAMILY_CAPACITY_GROWTH_FACTOR = 2
