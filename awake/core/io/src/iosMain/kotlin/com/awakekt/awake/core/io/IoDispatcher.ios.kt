/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO

private const val IOS_MAX_CONCURRENT_DECODES = 2

/** iOS implementation of [ioDispatcher], backed by [Dispatchers.IO]. */
actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

/** iOS implementation of [decodeDispatcher], capping concurrent heavy decodes at 2. */
actual val decodeDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(IOS_MAX_CONCURRENT_DECODES)
