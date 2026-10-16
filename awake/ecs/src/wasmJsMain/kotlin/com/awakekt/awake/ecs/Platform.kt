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

@PublishedApi
internal actual inline fun <reified T : Any> componentTypeKey(): Any = WasmTypeKey(T::class)

internal actual fun <T : Any> componentTypeKeyOf(type: KClass<T>): Any = WasmTypeKey(type)

/**
 * A class as a map key. Kotlin/Wasm hashes a `KClass` by building its qualified name, a new string
 * each call, so every type lookup cost a string build and a full hash. This hashes the simple name,
 * a pooled string that keeps its hash, and compares the classes themselves, which is a reference
 * check: two classes of one simple name are two keys that share a bucket.
 */
@PublishedApi
internal class WasmTypeKey(private val type: KClass<*>) {
    override fun equals(other: Any?): Boolean = other is WasmTypeKey && other.type == type

    override fun hashCode(): Int = type.simpleName.hashCode()
}
