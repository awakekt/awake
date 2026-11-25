/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.WeakReference

/**
 * iOS implementation of [WeakRef] backed by Kotlin/Native [WeakReference].
 *
 * @param T Non-null type of the referenced object.
 * @param value Target object to hold weakly.
 */
@OptIn(ExperimentalNativeApi::class)
actual class WeakRef<T : Any> actual constructor(value: T) {
    private val reference = WeakReference(value)

    /** Returns the weakly referenced object, or `null` if collected or cleared. */
    actual fun get(): T? = reference.get()

    /** Clears the underlying weak reference so [get] returns `null`. */
    actual fun clear() {
        reference.clear()
    }
}
