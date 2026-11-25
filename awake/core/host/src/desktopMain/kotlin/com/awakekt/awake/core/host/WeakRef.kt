/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import java.lang.ref.WeakReference

/**
 * Desktop JVM implementation of [WeakRef] backed by [WeakReference].
 *
 * @param T Non-null type of the referenced object.
 * @param value Target object to hold weakly.
 */
actual class WeakRef<T : Any> actual constructor(value: T) {
    private val reference = WeakReference(value)

    /** Returns the weakly referenced object, or `null` if collected or cleared. */
    actual fun get(): T? = reference.get()

    /** Clears the underlying weak reference so [get] returns `null`. */
    actual fun clear() {
        reference.clear()
    }
}
