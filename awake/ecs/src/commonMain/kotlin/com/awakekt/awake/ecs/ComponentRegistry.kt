/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/** Thin internal façade coordinating type, pool, and storage registries. */
@PublishedApi
@Suppress("TooManyFunctions") // Deliberate façade: methods delegate policy to focused registries.
internal class ComponentRegistry {
    private val types = ComponentTypeCatalog()
    private val pools = ComponentPoolRegistry()
    private val storage = ComponentStorageRegistry()

    fun clear() {
        storage.clear()
        types.clear()
        pools.clear()
    }

    fun componentCount(type: KClass<out Any>): Int {
        val typeId = types.typeIdOrNull(type) ?: return 0
        return storage.storeOrNull<Any>(typeId)?.size ?: 0
    }

    fun <T : Any> registerPool(type: KClass<T>, factory: () -> T) =
        pools.register(type, factory, types.typeIdOrNull(type))

    fun recycle(component: Any) = pools.recycle(component)

    fun <T : Any> recycle(type: KClass<T>, component: T) = pools.recycle(type, component)

    fun recycle(typeId: ComponentTypeId, component: Any) = pools.recycle(typeId, component)

    @PublishedApi
    internal fun pool(type: KClass<out Any>): ComponentPool<Any> = pools.pool(type).also {
        types.typeIdOrNull(type)?.let { typeId -> pools.bind(typeId, type) }
    }

    @PublishedApi
    internal fun pool(typeId: ComponentTypeId): ComponentPool<Any>? = pools.pool(typeId)

    fun <T : Any> typeId(type: KClass<T>): ComponentTypeId = types.register(type).also { pools.bind(it, type) }

    fun typeIdOrNull(type: KClass<out Any>): ComponentTypeId? = types.typeIdOrNull(type)

    fun <T : Any> typeIdForKey(key: Any, type: () -> KClass<T>): ComponentTypeId =
        types.typeIdForKeyOrNull(key) ?: typeId(type())

    fun typeIdForKeyOrNull(key: Any): ComponentTypeId? = types.typeIdForKeyOrNull(key)

    fun storageKind(type: KClass<out Any>): ComponentStorageKind {
        val typeId = types.typeIdOrNull(type) ?: return ComponentStorageKind.Unregistered
        return storage.storageKind(typeId)
    }

    fun storageInfo(): List<ComponentStorageInfo> = storage.storageInfo(types)

    fun storageInfo(signature: Long): List<ComponentStorageInfo> = storage.storageInfo(types, signature)

    fun componentTypes(signature: Long): List<KClass<out Any>> =
        buildList(signature.countOneBits()) {
            for (id in 0 until types.size) {
                if ((signature and (1L shl id)) != 0L) types.typeAt(id)?.let(::add)
            }
        }

    fun <T : Any> store(typeId: ComponentTypeId, type: KClass<T>): ComponentStore<T> = storage.store(typeId, type)

    fun <T : Any> storeOrNull(typeId: ComponentTypeId): ComponentStore<T>? = storage.storeOrNull(typeId)

    fun <T : Any> storeForKey(typeId: ComponentTypeId, type: () -> KClass<T>): ComponentStore<T> =
        storage.storeOrNull(typeId) ?: storage.store(typeId, type())

    fun removeEntity(entity: Entity, signature: Long) {
        if (signature == 0L) return
        storage.forEach { store ->
            store.remove(entity)?.let { removed -> pools.recycle(store.type, removed) }
        }
    }
}
