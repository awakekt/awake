/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/** Component stores keyed by type ID; owns no type or pool policy. */
internal class ComponentStorageRegistry {
    private var stores = arrayOfNulls<ComponentStore<Any>>(INITIAL_CAPACITY)
    private var storesByIndex = arrayOfNulls<ComponentStore<Any>>(INITIAL_CAPACITY)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> store(typeId: ComponentTypeId, type: KClass<T>, typeIndex: Int = -1): ComponentStore<T> {
        val id = typeId.value
        if (id < stores.size) stores[id]?.let { return it as ComponentStore<T> }

        ensureCapacity(id)
        val created = ComponentStore(type)
        val raw = created as ComponentStore<Any>
        stores[id] = raw
        if (typeIndex >= 0) indexStore(typeIndex, raw)
        return created
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> storeOrNull(typeId: ComponentTypeId): ComponentStore<T>? =
        if (typeId.value < stores.size) stores[typeId.value] as ComponentStore<T>? else null

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> storeAtIndexOrNull(index: Int): ComponentStore<T>? =
        if (index < storesByIndex.size) storesByIndex[index] as ComponentStore<T>? else null

    fun storageKind(typeId: ComponentTypeId): ComponentStorageKind =
        storeOrNull<Any>(typeId)?.storageKind ?: ComponentStorageKind.Uninitialized

    /** Storage of every registered type whose ID passes [includes]. */
    inline fun storageInfo(types: ComponentTypeCatalog, includes: (Int) -> Boolean): List<ComponentStorageInfo> =
        buildList {
            for (id in 0 until types.size) {
                if (includes(id)) {
                    val type = types.typeAt(id) ?: continue
                    val store = storeOrNull<Any>(ComponentTypeId(id))
                    add(ComponentStorageInfo(type, store?.storageKind ?: ComponentStorageKind.Uninitialized, store?.size ?: 0))
                }
            }
        }

    fun forEach(action: (ComponentStore<Any>) -> Unit) {
        stores.forEach { store -> store?.let(action) }
    }

    fun clear() {
        forEach { it.clear() }
        stores.fill(null)
        storesByIndex.fill(null)
    }

    private fun indexStore(index: Int, store: ComponentStore<Any>) {
        if (index >= storesByIndex.size) {
            storesByIndex = storesByIndex.copyOf(maxOf(index + 1, storesByIndex.size * 2))
        }
        storesByIndex[index] = store
    }

    private fun ensureCapacity(id: Int) {
        if (id >= stores.size) {
            stores = stores.copyOf(maxOf(id + 1, stores.size * 2))
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
    }
}
