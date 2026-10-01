/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

import kotlin.reflect.KClass

/**
 * Key identifying a dependency by its [type] and optional [qualifier].
 *
 * @param T The dependency type.
 * @property type The target dependency class.
 * @property qualifier An optional string qualifier to differentiate multiple bindings of [type].
 */
data class Key<T : Any>(
    val type: KClass<T>,
    val qualifier: String? = null,
) {
    override fun toString(): String =
        if (qualifier == null) {
            type.simpleName ?: type.toString()
        } else {
            "${type.simpleName ?: type.toString()}('$qualifier')"
        }
}

/**
 * Helper to build a typed [Key] with an optional qualifier.
 *
 * @param T The dependency type.
 * @param qualifier Optional string qualifier to distinguish bindings of the same type.
 * @return A typed [Key] instance.
 */
inline fun <reified T : Any> key(qualifier: String? = null): Key<T> = Key(T::class, qualifier)
