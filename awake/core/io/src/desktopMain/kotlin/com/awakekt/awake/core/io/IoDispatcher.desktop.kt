/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private const val DESKTOP_MAX_CONCURRENT_DECODES = 4

/** Desktop JVM implementation of [ioDispatcher], backed by [Dispatchers.IO]. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

/** Desktop JVM implementation of [decodeDispatcher], capping concurrent heavy decodes at 4. */
actual val decodeDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(DESKTOP_MAX_CONCURRENT_DECODES)
