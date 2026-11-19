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
@Suppress("TooManyFunctions") // Focused per-world catalog exposing boxed, unboxed, and store-bound lookup views.
internal class ComponentTypeCatalog(private val beforeRegister: () -> Unit = {}) {
    private val typeIdsByKey = TypeKeyTable()
    private var typesById = arrayOfNulls<KClass<out Any>>(INITIAL_CAPACITY)
    private var keysById = arrayOfNulls<Any>(INITIAL_CAPACITY)
    private var indicesById = IntArray(INITIAL_CAPACITY) { ABSENT }

    // Boxed once here, so a lookup that returns `ComponentTypeId?` hands back a stored box, never a new one.
    private var typeIdsByIndex = arrayOfNulls<ComponentTypeId>(INITIAL_CAPACITY)
    private var typeIdValuesByIndex = IntArray(INITIAL_CAPACITY) { ABSENT }

    val size: Int get() = typeIdsByKey.size

    fun register(type: KClass<out Any>): ComponentTypeId {
        val key = componentTypeKeyOf(type)
        typeIdsByKey[key]?.let { return it }
        beforeRegister()
        val id = typeIdsByKey.size
        val index = componentTypeIndexOf(key)
        ensureCapacity(id)
        typesById[id] = type
        keysById[id] = key
        indicesById[id] = index
        return ComponentTypeId(id).also { typeId ->
            typeIdsByKey.put(key, typeId)
            indexTypeId(index, typeId)
        }
    }

    fun typeIdOrNull(type: KClass<out Any>): ComponentTypeId? = typeIdForKeyOrNull(componentTypeKeyOf(type))

    fun typeIdForKeyOrNull(key: Any): ComponentTypeId? {
        val index = cheapComponentTypeIndexOf(key)
        return if (index >= 0) typeIdAtIndexOrNull(index) else typeIdsByKey[key]
    }

    /** The unboxed ID value for [key], or [ABSENT] when unregistered. */
    fun typeIdValueForKeyOrAbsent(key: Any): Int {
        val index = cheapComponentTypeIndexOf(key)
        return if (index >= 0) typeIdValueAtIndexOrAbsent(index) else typeIdsByKey.typeIdValueOrAbsent(key)
    }

    /** The ID of the class whose [componentTypeIndexOf] is [index], or `null` when this world has not registered it. */
    fun typeIdAtIndexOrNull(index: Int): ComponentTypeId? = if (index < typeIdsByIndex.size) typeIdsByIndex[index] else null

    /** The unboxed ID of the class whose [componentTypeIndexOf] is [index], or [ABSENT] when unregistered. */
    fun typeIdValueAtIndexOrAbsent(index: Int): Int =
        if (index < typeIdValuesByIndex.size) typeIdValuesByIndex[index] else ABSENT

    /** The [ComponentStore] bound in [typeIdsByKey] for [key], or `null`. */
    fun <T : Any> storeForKeyOrNull(key: Any): ComponentStore<T>? = typeIdsByKey.storeOrNull(key)

    /** Binds [store] into [typeIdsByKey] for the type registered at [id]. */
    fun bindStore(id: Int, store: ComponentStore<Any>) {
        val key = if (id < keysById.size) keysById[id] else null
        if (key != null) typeIdsByKey.bindStore(key, store)
    }

    /** Process-wide [componentTypeIndexOf] for the type registered at [id], or [ABSENT]. */
    fun indexAt(id: Int): Int = if (id < indicesById.size) indicesById[id] else ABSENT

    fun typeAt(id: Int): KClass<out Any>? = if (id < typesById.size) typesById[id] else null

    fun clear() {
        typeIdsByKey.clear()
        typesById.fill(null)
        keysById.fill(null)
        indicesById.fill(ABSENT)
        typeIdsByIndex.fill(null)
        typeIdValuesByIndex.fill(ABSENT)
    }

    private fun indexTypeId(index: Int, typeId: ComponentTypeId) {
        if (index >= typeIdsByIndex.size) {
            val newCapacity = maxOf(index + 1, typeIdsByIndex.size * 2)
            typeIdsByIndex = typeIdsByIndex.copyOf(newCapacity)
            val oldSize = typeIdValuesByIndex.size
            typeIdValuesByIndex = typeIdValuesByIndex.copyOf(newCapacity)
            typeIdValuesByIndex.fill(ABSENT, fromIndex = oldSize, toIndex = newCapacity)
        }
        typeIdsByIndex[index] = typeId
        typeIdValuesByIndex[index] = typeId.value
    }

    private fun ensureCapacity(id: Int) {
        if (id >= typesById.size) {
            val newCapacity = maxOf(id + 1, typesById.size * 2)
            typesById = typesById.copyOf(newCapacity)
            keysById = keysById.copyOf(newCapacity)
            val oldSize = indicesById.size
            indicesById = indicesById.copyOf(newCapacity)
            indicesById.fill(ABSENT, fromIndex = oldSize, toIndex = newCapacity)
        }
    }

    private companion object {
        const val ABSENT = -1
        const val INITIAL_CAPACITY = 16
    }
}
