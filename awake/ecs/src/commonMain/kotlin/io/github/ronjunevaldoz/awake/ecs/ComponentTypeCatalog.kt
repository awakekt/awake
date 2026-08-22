// Copyright (c) Ron June Valdoz
// SPDX-License-Identifier: Apache-2.0
package io.github.ronjunevaldoz.awake.ecs

import kotlin.reflect.KClass

/** Stable component type IDs and their platform-token fast path. */
internal class ComponentTypeCatalog {
    private val typeIds = mutableMapOf<KClass<out Any>, ComponentTypeId>()
    private val typeIdsByKey = mutableMapOf<Any, ComponentTypeId>()
    private var typesById = arrayOfNulls<KClass<out Any>>(INITIAL_CAPACITY)

    val size: Int get() = typeIds.size

    fun register(type: KClass<out Any>): ComponentTypeId = typeIds.getOrPut(type) {
        require(typeIds.size < MAX_COMPONENT_TYPES) {
            "Awake ECS currently supports up to $MAX_COMPONENT_TYPES component types per World."
        }
        val id = typeIds.size
        ensureCapacity(id)
        ComponentTypeId(id).also { typeId ->
            typesById[id] = type
            typeIdsByKey[componentTypeKeyOf(type)] = typeId
        }
    }

    fun typeIdOrNull(type: KClass<out Any>): ComponentTypeId? = typeIds[type]

    fun typeIdForKeyOrNull(key: Any): ComponentTypeId? = typeIdsByKey[key]

    fun typeAt(id: Int): KClass<out Any>? = if (id < typesById.size) typesById[id] else null

    fun clear() {
        typeIds.clear()
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
        const val MAX_COMPONENT_TYPES = Long.SIZE_BITS
    }
}
