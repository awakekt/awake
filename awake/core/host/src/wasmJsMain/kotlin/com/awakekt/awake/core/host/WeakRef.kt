/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * WasmJS implementation of [WeakRef].
 *
 * WebAssembly GC does not provide weak references for Kotlin [Any] objects (and wrapping a fresh
 * `JsReference` box in JavaScript `WeakRef` would allow premature collection while the Kotlin
 * object is still strongly reachable), so this target falls back to a clearable reference. Call
 * [clear] explicitly on teardown or cache eviction.
 *
 * @param T Non-null type of the referenced object.
 * @param value Target object to reference until cleared.
 */
actual class WeakRef<T : Any> actual constructor(value: T) {
    private var reference: T? = value

    /** Returns the referenced object, or `null` once cleared via [clear]. */
    actual fun get(): T? = reference

    /** Releases the retained reference so [get] returns `null`. */
    actual fun clear() {
        reference = null
    }
}
