/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.compose.runtime

import kotlin.reflect.KProperty

/**
 * A value holder that can be read.
 */
interface State<out T> {
    val value: T
}

/**
 * A mutable value holder that can be read and written.
 *
 * Commonly used with [remember] and property delegation:
 * ```kotlin
 * var count by remember { mutableStateOf(0) }
 * ```
 */
interface MutableState<T> : State<T> {
    override var value: T

    operator fun component1(): T = value
    operator fun component2(): (T) -> Unit = { value = it }
}

/**
 * Default implementation of [MutableState].
 */
class SnapshotMutableState<T>(
    override var value: T,
) : MutableState<T> {
    override fun toString(): String = "MutableState(value=$value)"
}

/**
 * Creates a new [MutableState] initialized with [value].
 *
 * Commonly combined with [remember] to retain state across passes:
 * ```kotlin
 * var expanded by remember { mutableStateOf(false) }
 * ```
 */
fun <T> mutableStateOf(value: T): MutableState<T> = SnapshotMutableState(value)

/**
 * Permits using [State] as a property delegate.
 *
 * Example:
 * ```kotlin
 * val count by state
 * ```
 */
operator fun <T> State<T>.getValue(thisRef: Any?, property: KProperty<*>): T = value

/**
 * Permits using [MutableState] as a mutable property delegate.
 *
 * Example:
 * ```kotlin
 * var count by mutableState
 * ```
 */
operator fun <T> MutableState<T>.setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    this.value = value
}
