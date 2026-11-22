/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatcher for blocking file, cache, resource, and network I/O off the render/main thread and the
 * bounded CPU (`Dispatchers.Default`) pool.
 *
 * Resolves to `Dispatchers.IO` on Desktop JVM, Android, and iOS, and `Dispatchers.Default` on WasmJs
 * where `Dispatchers.IO` is not available.
 */
expect val ioDispatcher: CoroutineDispatcher
