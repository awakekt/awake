/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

/**
 * Dispatcher for blocking file, cache, resource, and network I/O off the render/main thread and the
 * bounded CPU (`Dispatchers.Default`) pool.
 *
 * Resolves to `Dispatchers.IO` on Desktop JVM, Android, and iOS, and `Dispatchers.Default` on WasmJs
 * where `Dispatchers.IO` is not available.
 */
expect val ioDispatcher: CoroutineDispatcher

/**
 * Runs [block] on [ioDispatcher] when the caller has a confined dispatcher (such as `Main` or
 * `Default`) that can resume back on the caller's thread afterwards.
 *
 * Callers running under `Dispatchers.Unconfined` or `EmptyCoroutineContext` (such as frame-thread
 * bootstrap) keep executing inline so switching to a worker pool never strands subsequent frame-thread
 * work off the calling thread.
 */
internal suspend fun <T> withIoIfConfined(block: suspend () -> T): T {
    val interceptor = coroutineContext[ContinuationInterceptor]
    return if (interceptor == null || interceptor == Dispatchers.Unconfined || interceptor == ioDispatcher) {
        block()
    } else {
        withContext(ioDispatcher) { block() }
    }
}
