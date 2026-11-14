/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

internal actual fun <T : Any> newComponentArray(type: KClass<T>, capacity: Int): Array<Any?> = arrayOfNulls<Any>(capacity)

internal actual fun <T : Any> createComponentInstance(type: KClass<T>): T {
    error("Automatic component instantiation not supported on this platform. Register a factory via registerPool.")
}

// The class itself, with no wrapper allocated per lookup: TypeKeyTable hashes it by its simple name.
@PublishedApi
internal actual inline fun <reified T : Any> componentTypeKey(): Any = T::class

internal actual fun <T : Any> componentTypeKeyOf(type: KClass<T>): Any = type

/**
 * A `KClass` hashes by building its qualified name, a new string each call. Its simple name is a
 * pooled string that keeps its hash; two classes of one simple name share a probe sequence and are
 * told apart by comparing the classes, which is a reference check.
 */
internal actual fun componentTypeKeyHash(key: Any): Int = typeOfKey(key).simpleName.hashCode()

internal actual fun normalizedComponentTypeKey(key: Any): Any = typeOfKey(key)

/** A token's class. Code inlined from a library built before the table may still hand a [WasmTypeKey]. */
private fun typeOfKey(key: Any): KClass<*> = if (key is WasmTypeKey) key.type else key as KClass<*>

/**
 * A class as a map key, for the process-wide index, which hashes its keys only when a class is first
 * seen: the same simple-name hash as [componentTypeKeyHash], with class equality. Published, because
 * the reified lookups of earlier releases inlined its constructor into their callers.
 */
@PublishedApi
internal class WasmTypeKey(internal val type: KClass<*>) {
    override fun equals(other: Any?): Boolean = other is WasmTypeKey && other.type == type

    override fun hashCode(): Int = type.simpleName.hashCode()
}

internal actual fun componentTypeIndexOf(key: Any): Int = SharedComponentTypeIndex.indexOf(WasmTypeKey(typeOfKey(key)))

internal actual val componentTypeIndexIsCheap: Boolean = false
