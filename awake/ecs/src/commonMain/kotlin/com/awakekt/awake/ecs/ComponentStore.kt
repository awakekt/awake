/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/** Public sparse-set façade. Membership and value representation are independent collaborators. */
class ComponentStore<T : Any>(
    internal val type: KClass<T>,
) {
    private val membership = SparseEntitySet()
    private var values: ComponentValueStorage<T>? = null

    /** Number of entities currently carrying this component. */
    val size: Int get() = membership.size

    /** The storage strategy selected for this component type. */
    val storageKind: ComponentStorageKind
        get() = values?.kind ?: ComponentStorageKind.Uninitialized

    internal val hasPayloadStorage: Boolean
        get() = values?.hasPayloadStorage == true

    /** List of all entities currently carrying this component. */
    val entities: List<Entity> get() = membership.entities

    /**
     * Adds the [component] to the [entity], replacing any existing value.
     *
     * @return the previous component instance, or null if none was present.
     */
    fun add(entity: Entity, component: T): T? {
        val storage = values ?: selectStorage(component).also { values = it }
        storage.validate(component)
        val existingIndex = membership.indexOf(entity)
        if (existingIndex >= 0) return storage.replace(existingIndex, component)

        val index = membership.addNew(entity)
        storage.insert(index, component)
        return null
    }

    /**
     * Returns the component instance attached to the [entity], or null if absent.
     */
    fun get(entity: Entity): T? {
        val index = membership.indexOf(entity)
        return if (index >= 0) values?.valueAt(index) else null
    }

    /** Returns true if the [entity] is carrying this component. */
    fun contains(entity: Entity): Boolean = membership.indexOf(entity) >= 0

    /**
     * Removes this component from the [entity].
     *
     * @return the removed component instance, or null if none was present.
     */
    fun remove(entity: Entity): T? {
        val index = membership.indexOf(entity)
        val storage = values
        if (index < 0 || storage == null) return null

        val lastIndex = membership.removeAt(index)
        val removed = storage.remove(index, lastIndex)
        return removed
    }

    /** Removes all components from this store. */
    fun clear() {
        values?.clear(membership.size)
        membership.clear()
    }

    /** Iterates each entity and its component instance in this store. */
    fun forEach(block: (Entity, T) -> Unit) {
        when (val storage = values) {
            null -> Unit
            is PayloadColumn -> forEachPayload(storage, block)
            is SingletonTagValue -> forEachTag(storage, block)
        }
    }

    private fun forEachPayload(storage: PayloadColumn<T>, block: (Entity, T) -> Unit) {
        val localValues = storage.valuesForIteration()
        val localSize = membership.size
        for (index in 0 until localSize) {
            block(membership.entityAt(index), localValues[index])
        }
    }

    private fun forEachTag(storage: SingletonTagValue<T>, block: (Entity, T) -> Unit) {
        val tag = storage.valueForIteration()
        val localSize = membership.size
        for (index in 0 until localSize) {
            block(membership.entityAt(index), tag)
        }
    }

    private fun selectStorage(component: T): ComponentValueStorage<T> =
        if (component is EcsTag) SingletonTagValue(component) else PayloadColumn(type)
}
