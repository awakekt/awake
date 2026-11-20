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
    /** The current value. */
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

    /** The current value, for `val (value, setValue) = state`. */
    operator fun component1(): T = value

    /** A function that sets [value], for `val (value, setValue) = state`. */
    operator fun component2(): (T) -> Unit = { value = it }
}

/**
 * Default implementation of [MutableState], read-tracked.
 *
 * A [Composition] pass that reads [value] is recorded as its reader, and a write of a value not equal
 * to the current one invalidates every composition whose latest pass read it (see
 * [Composition.isInvalid]). A write of an equal value is ignored. Read and write it on the thread
 * that composes; a value another thread updates belongs in a [PolledState] instead.
 */
class SnapshotMutableState<T>(
    value: T,
) : MutableState<T> {
    private var current: T = value
    private var readers: StateReaders? = null

    /**
     * What this state is, for a diagnostic that names it: `ComposeHost.explainPlacement` reports the
     * state placement wrote, and a bare `MutableState(value=272)` cannot be traced to its owner (#1512).
     */
    var label: String? = null
        private set

    /** A state labelled [label], which [toString] and so every diagnostic that prints it shows. */
    constructor(value: T, label: String) : this(value) {
        this.label = label
    }

    override var value: T
        get() {
            val observer = ReadTracking.current
            if (observer != null) (readers ?: StateReaders().also { readers = it }).record(observer)
            return current
        }
        set(value) {
            if (current != value) {
                current = value
                readers?.invalidate(this)
            }
        }

    override fun toString(): String = label?.let { "MutableState($it, value=$current)" } ?: "MutableState(value=$current)"
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

/** Creates a new [MutableState] initialized with [value], named [label] in diagnostics (#1512). */
fun <T> mutableStateOf(value: T, label: String): MutableState<T> = SnapshotMutableState(value, label)

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
