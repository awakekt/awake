/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

/**
 * Stable component type IDs, keyed by the platform token [componentTypeKeyOf] gives a class, never
 * by the `KClass` itself: on Kotlin/Wasm a `KClass` hashes by building its qualified name anew.
 */
internal class ComponentTypeCatalog {
    private val typeIdsByKey = mutableMapOf<Any, ComponentTypeId>()
    private var typesById = arrayOfNulls<KClass<out Any>>(INITIAL_CAPACITY)

    val size: Int get() = typeIdsByKey.size

    fun register(type: KClass<out Any>): ComponentTypeId = typeIdsByKey.getOrPut(componentTypeKeyOf(type)) {
        val id = typeIdsByKey.size
        ensureCapacity(id)
        typesById[id] = type
        ComponentTypeId(id)
    }

    fun typeIdOrNull(type: KClass<out Any>): ComponentTypeId? = typeIdsByKey[componentTypeKeyOf(type)]

    fun typeIdForKeyOrNull(key: Any): ComponentTypeId? = typeIdsByKey[key]

    fun typeAt(id: Int): KClass<out Any>? = if (id < typesById.size) typesById[id] else null

    fun clear() {
        typeIdsByKey.clear()
        typesById.fill(null)
    }

    private fun ensureCapacity(id: Int) {
        if (id >= typesById.size) {
            typesById = typesById.copyOf(maxOf(id + 1, typesById.size * 2))
        }
    }

    private companion object {
        const val INITIAL_CAPACITY = 16
    }
}
