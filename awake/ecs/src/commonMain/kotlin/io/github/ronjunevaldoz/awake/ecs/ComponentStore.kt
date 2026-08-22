// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ecs

import kotlin.reflect.KClass

/** Public sparse-set façade. Membership and value representation are independent collaborators. */
class ComponentStore<T : Any>(
    internal val type: KClass<T>,
) {
    private val membership = SparseEntitySet()
    private var values: ComponentValueStorage<T>? = null

    val size: Int get() = membership.size

    val storageKind: ComponentStorageKind
        get() = values?.kind ?: ComponentStorageKind.Uninitialized

    internal val hasPayloadStorage: Boolean
        get() = values?.hasPayloadStorage == true

    val entities: List<Entity> get() = membership.entities

    fun add(entity: Entity, component: T): T? {
        val storage = values ?: selectStorage(component).also { values = it }
        storage.validate(component)
        val existingIndex = membership.indexOf(entity)
        if (existingIndex >= 0) return storage.replace(existingIndex, component)

        val index = membership.addNew(entity)
        storage.insert(index, component)
        return null
    }

    fun get(entity: Entity): T? {
        val index = membership.indexOf(entity)
        return if (index >= 0) values?.valueAt(index) else null
    }

    fun contains(entity: Entity): Boolean = membership.indexOf(entity) >= 0

    fun remove(entity: Entity): T? {
        val index = membership.indexOf(entity)
        val storage = values
        if (index < 0 || storage == null) return null

        val lastIndex = membership.removeAt(index)
        val removed = storage.remove(index, lastIndex)
        return removed
    }

    fun clear() {
        values?.clear(membership.size)
        membership.clear()
    }

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
