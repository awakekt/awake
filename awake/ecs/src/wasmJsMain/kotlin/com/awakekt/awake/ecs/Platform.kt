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

/**
 * A function object made once per call site: a lambda that captures nothing is a single instance,
 * where `T::class` builds a new class wrapper on every evaluation. [cheapComponentTypeIndexOf]
 * finds the token by identity, so a reified lookup allocates nothing.
 */
@PublishedApi
internal actual inline fun <reified T : Any> componentTypeKey(): Any = { T::class }

internal actual fun <T : Any> componentTypeKeyOf(type: KClass<T>): Any = type

/**
 * A `KClass` hashes by building its qualified name, a new string each call, and `String.hashCode`
 * allocates on Kotlin/Wasm too. The simple name is hashed by [nameHash] instead; two classes that
 * hash alike share a probe sequence and are told apart by comparing the classes, a reference check.
 */
internal actual fun componentTypeKeyHash(key: Any): Int = nameHash(typeOfKey(key).simpleName)

internal actual fun normalizedComponentTypeKey(key: Any): Any = typeOfKey(key)

internal actual fun cheapComponentTypeIndexOf(key: Any): Int =
    if (key is KClass<*> || key is WasmTypeKey) -1 else SiteTokenIndex.indexOf(key)

/** A key's class: a class, a call-site token, or a [WasmTypeKey] that code inlined from an earlier library still hands. */
@Suppress("UNCHECKED_CAST")
private fun typeOfKey(key: Any): KClass<*> = when (key) {
    is KClass<*> -> key
    is WasmTypeKey -> key.type
    else -> (key as () -> KClass<*>)()
}

/** Three reads, for a lookup that runs per entity: the length and the first and last characters. */
internal fun nameHash(name: String?): Int =
    if (name.isNullOrEmpty()) 0 else (name.length * 31 + name[0].code) * 31 + name[name.length - 1].code

/**
 * A class as a map key, for the process-wide index, which hashes its keys only when a class is first
 * seen: the same simple-name hash as [componentTypeKeyHash], with class equality. Published, because
 * the reified lookups of earlier releases inlined its constructor into their callers.
 */
@PublishedApi
internal class WasmTypeKey(internal val type: KClass<*>) {
    override fun equals(other: Any?): Boolean = other is WasmTypeKey && other.type == type

    override fun hashCode(): Int = nameHash(type.simpleName)
}

internal actual fun componentTypeIndexOf(key: Any): Int = SharedComponentTypeIndex.indexOf(WasmTypeKey(typeOfKey(key)))

/**
 * Call-site tokens to their class's [componentTypeIndexOf], by identity. A token's `hashCode` is its
 * identity hash, which costs nothing; the class is read from the token only when a site is first
 * seen. Kotlin/Wasm runs one thread, so the table takes no lock.
 */
private object SiteTokenIndex {
    private var tokens = arrayOfNulls<Any>(INITIAL_CAPACITY)
    private var indices = IntArray(INITIAL_CAPACITY)
    private var size = 0

    fun indexOf(token: Any): Int {
        val mask = tokens.size - 1
        var slot = token.hashCode() and mask
        while (true) {
            val held = tokens[slot] ?: break
            if (held === token) return indices[slot]
            slot = (slot + 1) and mask
        }
        val index = componentTypeIndexOf(token)
        if ((size + 1) * 2 > tokens.size) grow()
        insert(token, index)
        size += 1
        return index
    }

    private fun insert(token: Any, index: Int) {
        val mask = tokens.size - 1
        var slot = token.hashCode() and mask
        while (tokens[slot] != null) slot = (slot + 1) and mask
        tokens[slot] = token
        indices[slot] = index
    }

    private fun grow() {
        val oldTokens = tokens
        val oldIndices = indices
        tokens = arrayOfNulls(oldTokens.size * 2)
        indices = IntArray(oldTokens.size * 2)
        for (slot in oldTokens.indices) oldTokens[slot]?.let { insert(it, oldIndices[slot]) }
    }

    private const val INITIAL_CAPACITY = 64
}
