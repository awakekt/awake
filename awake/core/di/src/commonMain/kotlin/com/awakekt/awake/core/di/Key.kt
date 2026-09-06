/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.di

import kotlin.reflect.KClass

/**
 * Key identifying a dependency by its [type] and optional [qualifier].
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
 */
inline fun <reified T : Any> key(qualifier: String? = null): Key<T> = Key(T::class, qualifier)
