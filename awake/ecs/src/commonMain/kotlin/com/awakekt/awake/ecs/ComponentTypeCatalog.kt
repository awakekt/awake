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
 *
 * Each ID is also kept at its class's process-wide [componentTypeIndexOf] slot, so a [ComponentType]
 * handle, and on the JVM a reified lookup, finds it by reading an array instead of hashing the class.
 *
 * @param beforeRegister Runs before a type not seen yet gets its ID, and may throw to refuse it.
 */
internal class ComponentTypeCatalog(private val beforeRegister: () -> Unit = {}) {
    private val typeIdsByKey = mutableMapOf<Any, ComponentTypeId>()
    private var typesById = arrayOfNulls<KClass<out Any>>(INITIAL_CAPACITY)

    // Boxed once here, so a lookup that returns `ComponentTypeId?` hands back a stored box, never a new one.
    private var typeIdsByIndex = arrayOfNulls<ComponentTypeId>(INITIAL_CAPACITY)

    val size: Int get() = typeIdsByKey.size

    fun register(type: KClass<out Any>): ComponentTypeId {
        val key = componentTypeKeyOf(type)
        return typeIdsByKey.getOrPut(key) {
            beforeRegister()
            val id = typeIdsByKey.size
            ensureCapacity(id)
            typesById[id] = type
            ComponentTypeId(id).also { typeId -> indexTypeId(componentTypeIndexOf(key), typeId) }
        }
    }

    fun typeIdOrNull(type: KClass<out Any>): ComponentTypeId? = typeIdForKeyOrNull(componentTypeKeyOf(type))

    fun typeIdForKeyOrNull(key: Any): ComponentTypeId? =
        if (componentTypeIndexIsCheap) typeIdAtIndexOrNull(componentTypeIndexOf(key)) else typeIdsByKey[key]

    /** The ID of the class whose [componentTypeIndexOf] is [index], or `null` when this world has not registered it. */
    fun typeIdAtIndexOrNull(index: Int): ComponentTypeId? = if (index < typeIdsByIndex.size) typeIdsByIndex[index] else null

    fun typeAt(id: Int): KClass<out Any>? = if (id < typesById.size) typesById[id] else null

    fun clear() {
        typeIdsByKey.clear()
        typesById.fill(null)
        typeIdsByIndex.fill(null)
    }

    private fun indexTypeId(index: Int, typeId: ComponentTypeId) {
        if (index >= typeIdsByIndex.size) {
            typeIdsByIndex = typeIdsByIndex.copyOf(maxOf(index + 1, typeIdsByIndex.size * 2))
        }
        typeIdsByIndex[index] = typeId
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
