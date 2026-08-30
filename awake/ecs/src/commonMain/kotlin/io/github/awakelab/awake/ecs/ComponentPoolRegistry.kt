/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package io.github.awakelab.awake.ecs

import kotlin.reflect.KClass

/** Component factories and recycled instances, independent of storage and type registration. */
internal class ComponentPoolRegistry {
    private val pools = mutableMapOf<KClass<out Any>, ComponentPool<Any>>()
    private var poolsById = arrayOfNulls<ComponentPool<Any>>(INITIAL_CAPACITY)
    private var hasPools = false

    fun <T : Any> register(type: KClass<T>, factory: () -> T, typeId: ComponentTypeId?) {
        @Suppress("UNCHECKED_CAST")
        val pool = ComponentPool(factory) as ComponentPool<Any>
        pools[type] = pool
        hasPools = true
        if (typeId != null) bind(typeId, type)
    }

    fun bind(typeId: ComponentTypeId, type: KClass<out Any>) {
        ensureCapacity(typeId.value)
        poolsById[typeId.value] = pools[type]
    }

    fun recycle(component: Any) {
        if (hasPools) pools[component::class]?.free(component)
    }

    fun <T : Any> recycle(type: KClass<T>, component: T) {
        if (hasPools) pools[type]?.free(component)
    }

    fun recycle(typeId: ComponentTypeId, component: Any) {
        if (hasPools && typeId.value < poolsById.size) poolsById[typeId.value]?.free(component)
    }

    fun pool(type: KClass<out Any>): ComponentPool<Any> = pools.getOrPut(type) {
        hasPools = true
        ComponentPool { createComponentInstance(type) }
    }

    fun pool(typeId: ComponentTypeId): ComponentPool<Any>? =
        if (typeId.value < poolsById.size) poolsById[typeId.value] else null

    fun clear() {
        poolsById.fill(null)
        pools.values.forEach { it.clear() }
        hasPools = pools.isNotEmpty()
    }

    private fun ensureCapacity(id: Int) {
        if (id >= poolsById.size) {
            poolsById = poolsById.copyOf(maxOf(id + 1, poolsById.size * 2))
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
    }
}
