/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs

import kotlin.reflect.KClass

/** Component stores keyed by type ID; owns no type or pool policy. */
internal class ComponentStorageRegistry {
    private var stores = arrayOfNulls<ComponentStore<Any>>(INITIAL_CAPACITY)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> store(typeId: ComponentTypeId, type: KClass<T>): ComponentStore<T> {
        val id = typeId.value
        if (id < stores.size) stores[id]?.let { return it as ComponentStore<T> }

        ensureCapacity(id)
        return ComponentStore(type).also { stores[id] = it as ComponentStore<Any> }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> storeOrNull(typeId: ComponentTypeId): ComponentStore<T>? =
        if (typeId.value < stores.size) stores[typeId.value] as? ComponentStore<T> else null

    fun storageKind(typeId: ComponentTypeId): ComponentStorageKind =
        storeOrNull<Any>(typeId)?.storageKind ?: ComponentStorageKind.Uninitialized

    fun storageInfo(types: ComponentTypeCatalog, signature: Long? = null): List<ComponentStorageInfo> =
        buildList(if (signature == null) types.size else signature.countOneBits()) {
            for (id in 0 until types.size) {
                if (signature == null || (signature and (1L shl id)) != 0L) {
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
