// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ecs

import kotlin.reflect.KClass

/** Dense, incrementally maintained cache backing [Family1]. */
@PublishedApi
@Suppress("TooManyFunctions")
internal class Family1Cache<A : Any>(
    private val type: KClass<A>,
    private val typeId: ComponentTypeId,
    @Suppress("unused") private val store: ComponentStore<A>,
) : FamilyCache() {
    @PublishedApi
    internal var entities = LongArray(DEFAULT_FAMILY_CAPACITY)

    @PublishedApi
    internal val componentValues = FamilyValueColumn(type)

    @PublishedApi
    internal var count: Int = 0

    private val sparse = EntityIndexMap()

    override fun types(): Set<KClass<out Any>> = setOf(type)

    /** Number of live dense entries. */
    val size: Int get() = count

    internal val hasMaterializedTagArray: Boolean
        get() = componentValues.hasMaterializedTagArray

    @PublishedApi
    internal fun components(): Array<A> = componentValues.compatibilityArray(count, entities.size)

    @PublishedApi
    internal fun componentAt(index: Int): A = componentValues.valueAt(index)

    /** Adds an already matched entity while a family is initially populated. */
    fun add(entity: Entity, component: A) {
        append(entity, component)
    }

    /**
     * Iterates live entries. Structural mutation affecting this family inside [block] is invalid
     * because removal swap-moves the final entry into the current dense slot.
     */
    @PublishedApi
    internal inline fun forEach(block: (Entity, A) -> Unit) {
        val localEntities = entities
        val localCount = count
        if (localCount == 0) return
        val localTag = componentValues.tagForIteration()
        if (localTag != null) {
            for (index in 0 until localCount) {
                block(Entity(localEntities[index]), localTag)
            }
        } else {
            val localComponents = componentValues.payloadForIteration()
            for (index in 0 until localCount) {
                block(Entity(localEntities[index]), localComponents[index])
            }
        }
    }

    /** Iterates component values with a single representation branch outside the loop. */
    @PublishedApi
    internal inline fun forEachComponent(block: (A) -> Unit) {
        val localCount = count
        if (localCount == 0) return
        val localTag = componentValues.tagForIteration()
        if (localTag != null) {
            for (index in 0 until localCount) {
                block(localTag)
            }
        } else {
            val localComponents = componentValues.payloadForIteration()
            for (index in 0 until localCount) {
                block(localComponents[index])
            }
        }
    }

    override fun remove(entity: Entity) {
        removeAt(indexOf(entity))
    }

    override fun addComponent(world: World, entity: Entity, typeId: ComponentTypeId, component: Any) {
        if (this.typeId == typeId) {
            @Suppress("UNCHECKED_CAST")
            append(entity, component as A)
        }
    }

    override fun replaceComponent(world: World, entity: Entity, typeId: ComponentTypeId, component: Any) {
        if (this.typeId != typeId) return
        val index = indexOf(entity)
        if (index >= 0) {
            @Suppress("UNCHECKED_CAST")
            componentValues.replace(index, component as A)
        }
    }

    override fun removeComponent(world: World, entity: Entity, typeId: ComponentTypeId) {
        if (this.typeId == typeId) remove(entity)
    }

    private fun append(entity: Entity, component: A) {
        ensureCapacity(count + 1)
        sparse.set(entity.id, count)
        entities[count] = entity.packed
        componentValues.insert(count, component, entities.size)
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
        componentValues.remove(index, lastIndex)
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
        componentValues.ensureCapacity(newCapacity)
    }
}
