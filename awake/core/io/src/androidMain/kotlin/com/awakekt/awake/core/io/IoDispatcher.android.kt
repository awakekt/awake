/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

private const val ANDROID_MAX_CONCURRENT_DECODES = 2

/** Android implementation of [ioDispatcher], backed by [Dispatchers.IO]. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

/** Android implementation of [decodeDispatcher], capping concurrent heavy decodes at 2. */
actual val decodeDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(ANDROID_MAX_CONCURRENT_DECODES)
