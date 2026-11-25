/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

/**
 * Cross-platform weak reference wrapper that lets the host garbage collector reclaim [value] once
 * no strong references remain, and supports explicit release through [clear].
 *
 * - **Desktop JVM / Android**: backed by `java.lang.ref.WeakReference`.
 * - **iOS**: backed by `kotlin.native.ref.WeakReference`.
 * - **WasmJS**: WebAssembly GC does not provide weak references for Kotlin [Any] objects, so this
 *   target falls back to a clearable reference; callers should still invoke [clear] on disposal or
 *   [MemoryPressure] trims.
 *
 * @param T Non-null type of the referenced object.
 * @param value Target object to reference.
 */
expect class WeakRef<T : Any>(value: T) {
    /**
     * Returns the referenced object, or `null` if it has been collected or cleared via [clear].
     *
     * @return The referenced instance, or `null`.
     */
    fun get(): T?

    /** Explicitly releases the reference so subsequent [get] calls return `null`. */
    fun clear()
}
