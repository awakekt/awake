/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** WasmJs implementation of [ioDispatcher], backed by [Dispatchers.Default]. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default

/** WasmJs implementation of [decodeDispatcher], backed by [Dispatchers.Default]. */
actual val decodeDispatcher: CoroutineDispatcher = Dispatchers.Default
