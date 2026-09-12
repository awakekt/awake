/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/** Dense, incrementally maintained cache backing [Family2]. */
@PublishedApi
// TooManyFunctions: dense dual-column cache implements full set of FamilyCache lifecycle and indexing operations.
@Suppress("TooManyFunctions")
internal class Family2Cache<A : Any, B : Any>(
    private val typeA: KClass<A>,
    private val typeIdA: ComponentTypeId,
    private val storeA: ComponentStore<A>,
    private val typeB: KClass<B>,
    private val typeIdB: ComponentTypeId,
    private val storeB: ComponentStore<B>,
) : FamilyCache() {
    @PublishedApi
    internal var entities = LongArray(DEFAULT_FAMILY_CAPACITY)

    @PublishedApi
    internal val componentValuesA = FamilyValueColumn(typeA)

    @PublishedApi
    internal val componentValuesB = FamilyValueColumn(typeB)

    @PublishedApi
    internal var count: Int = 0

    private val sparse = EntityIndexMap()

    override fun types(): Set<KClass<out Any>> = setOf(typeA, typeB)

    /** Number of live dense entries. */
    val size: Int get() = count

    internal val hasMaterializedTagArrayA: Boolean
        get() = componentValuesA.hasMaterializedTagArray

    internal val hasMaterializedTagArrayB: Boolean
        get() = componentValuesB.hasMaterializedTagArray

    @PublishedApi
    internal fun componentsA(): Array<A> = componentValuesA.compatibilityArray(count, entities.size)

    @PublishedApi
    internal fun componentsB(): Array<B> = componentValuesB.compatibilityArray(count, entities.size)

    @PublishedApi
    internal fun componentA(index: Int): A = componentValuesA.valueAt(index)

    @PublishedApi
    internal fun componentB(index: Int): B = componentValuesB.valueAt(index)

    /** Adds an already matched entity while a family is initially populated. */
    fun add(entity: Entity, componentA: A, componentB: B) {
        append(entity, componentA, componentB)
    }

    /**
     * Iterates live entries. Each column's representation is selected before entering the
     * entity loop, so tags add no per-entry interface dispatch or payload-array read.
     */
    @PublishedApi
    internal inline fun forEach(block: (Entity, A, B) -> Unit) {
        val localEntities = entities
        iterateValues { index, componentA, componentB ->
            block(Entity(localEntities[index]), componentA, componentB)
        }
    }

    /** Iterates value pairs without allocating an intermediate collection. */
    @PublishedApi
    internal inline fun forEachComponents(block: (A, B) -> Unit) {
        iterateValues { _, componentA, componentB -> block(componentA, componentB) }
    }

    @PublishedApi
    internal inline fun iterateValues(block: (Int, A, B) -> Unit) {
        val localCount = count
        if (localCount == 0) return
        val localTagA = componentValuesA.tagForIteration()
        val localTagB = componentValuesB.tagForIteration()
        when {
            localTagA != null && localTagB != null -> {
                for (index in 0 until localCount) block(index, localTagA, localTagB)
            }
            localTagA != null -> {
                val localComponentsB = componentValuesB.payloadForIteration()
                for (index in 0 until localCount) block(index, localTagA, localComponentsB[index])
            }
            localTagB != null -> {
                val localComponentsA = componentValuesA.payloadForIteration()
                for (index in 0 until localCount) block(index, localComponentsA[index], localTagB)
            }
            else -> {
                val localComponentsA = componentValuesA.payloadForIteration()
                val localComponentsB = componentValuesB.payloadForIteration()
                for (index in 0 until localCount) block(index, localComponentsA[index], localComponentsB[index])
            }
        }
    }

    override fun remove(entity: Entity) {
        removeAt(indexOf(entity))
    }

    override fun addComponent(world: World, entity: Entity, typeId: ComponentTypeId, component: Any) {
        if (typeId == typeIdA) {
            val componentB = storeB.get(entity) ?: return
            // Safe: typeId == typeIdA check confirms component is of type A.
            @Suppress("UNCHECKED_CAST")
            append(entity, component as A, componentB)
        } else if (typeId == typeIdB) {
            val componentA = storeA.get(entity) ?: return
            // Safe: typeId == typeIdB check confirms component is of type B.
            @Suppress("UNCHECKED_CAST")
            append(entity, componentA, component as B)
        }
    }

    override fun replaceComponent(world: World, entity: Entity, typeId: ComponentTypeId, component: Any) {
        val index = indexOf(entity)
        if (index < 0) return
        if (typeIdA == typeId) {
            // Safe: typeIdA == typeId check confirms component is of type A.
            @Suppress("UNCHECKED_CAST")
            componentValuesA.replace(index, component as A)
        } else if (typeIdB == typeId) {
            // Safe: typeIdB == typeId check confirms component is of type B.
            @Suppress("UNCHECKED_CAST")
            componentValuesB.replace(index, component as B)
        }
    }

    override fun removeComponent(world: World, entity: Entity, typeId: ComponentTypeId) {
        if (typeIdA == typeId || typeIdB == typeId) remove(entity)
    }

    private fun append(entity: Entity, componentA: A, componentB: B) {
        ensureCapacity(count + 1)
        sparse.set(entity.id, count)
        entities[count] = entity.packed
        componentValuesA.insert(count, componentA, entities.size)
        componentValuesB.insert(count, componentB, entities.size)
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
        componentValuesA.remove(index, lastIndex)
        componentValuesB.remove(index, lastIndex)
        count -= 1
        sparse.remove(removedEntityId)
    }

    private fun indexOf(entity: Entity): Int {
        val denseIndex = sparse.get(entity.id)
        return if (denseIndex >= 0 && denseIndex < count) denseIndex else -1
    }

    private fun ensureCapacity(requiredCapacity: Int) {
        if (requiredCapacity <= entities.size) return
        val newCapacity = maxOf(requiredCapacity, entities.size * FAMILY_CAPACITY_GROWTH_FACTOR)
        entities = entities.copyOf(newCapacity)
        componentValuesA.ensureCapacity(newCapacity)
        componentValuesB.ensureCapacity(newCapacity)
    }
}
