/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.ecs

import kotlin.reflect.KClass

internal actual fun <T : Any> newComponentArray(type: KClass<T>, capacity: Int): Array<Any?> = java.lang.reflect.Array.newInstance(type.java, capacity) as Array<Any?>

internal actual fun <T : Any> createComponentInstance(type: KClass<T>): T {
    // KClass<T>.javaObjectType always returns the Class object for that exact T; the
    // platform API just types it as Class<*> instead of Class<T>.
    @Suppress("UNCHECKED_CAST")
    val clazz = type.javaObjectType as Class<T>
    return clazz.getDeclaredConstructor().newInstance()
}

// `T::class.java` on a reified type parameter is a recognized Kotlin compiler idiom that
// compiles directly to an `LDC <Class>` constant, skipping `Reflection.getOrCreateKotlinClass`
// entirely -- see the commonMain expect declaration's doc comment for how this was verified.
@PublishedApi
internal actual inline fun <reified T : Any> componentTypeKey(): Any = T::class.java

internal actual fun <T : Any> componentTypeKeyOf(type: KClass<T>): Any = type.java

internal actual fun componentTypeIndexOf(key: Any): Int = ComponentTypeIndexValue.get(key as Class<*>)

internal actual val componentTypeIndexIsCheap: Boolean = true

/**
 * Each component class's index, held by the class itself. A `ClassValue` read does not hash the
 * class: it probes the class's own small table at a slot fixed for this `ClassValue`. The keys
 * [componentTypeKey] and [componentTypeKeyOf] give here are always the `Class`.
 */
private object ComponentTypeIndexValue : ClassValue<Int>() {
    private val next = java.util.concurrent.atomic.AtomicInteger()

    override fun computeValue(type: Class<*>): Int = next.getAndIncrement()
}

internal actual fun componentTypeKeyHash(key: Any): Int = key.hashCode()

internal actual fun normalizedComponentTypeKey(key: Any): Any = key
