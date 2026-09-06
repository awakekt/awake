/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

/** Entity membership and packed iteration order shared by every component value strategy. */
internal class SparseEntitySet {
    private val sparse = EntityIndexMap()
    private var denseEntities = LongArray(DEFAULT_CAPACITY)
    private var count = 0

    val size: Int get() = count

    private val entitiesView: List<Entity> = object : AbstractList<Entity>() {
        override val size: Int get() = count

        override fun get(index: Int): Entity = entityAt(index)
    }

    val entities: List<Entity> get() = entitiesView

    fun indexOf(entity: Entity): Int {
        val index = sparse.get(entity.id)
        return if (index >= 0 && index < count) index else EntityIndexMap.ABSENT
    }

    /** Adds an entity already proven absent by [indexOf]. */
    fun addNew(entity: Entity): Int {
        ensureCapacity(count + 1)
        sparse.set(entity.id, count)
        denseEntities[count] = entity.packed
        return count++
    }

    /** Swap-removes [index] and returns the previous last index without allocating a result. */
    fun removeAt(index: Int): Int {
        val removedEntityId = denseEntities[index].toInt()
        val lastIndex = count - 1
        if (index != lastIndex) {
            val lastEntity = denseEntities[lastIndex]
            denseEntities[index] = lastEntity
            sparse.set(lastEntity.toInt(), index)
        }
        count -= 1
        sparse.remove(removedEntityId)
        return lastIndex
    }

    fun entityAt(index: Int): Entity = Entity(denseEntities[index])

    fun clear() {
        count = 0
        sparse.clear()
    }

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity > denseEntities.size) {
            denseEntities = denseEntities.copyOf(maxOf(requiredCapacity, denseEntities.size * CAPACITY_GROWTH_FACTOR))
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 16
        const val CAPACITY_GROWTH_FACTOR = 2
    }
}
