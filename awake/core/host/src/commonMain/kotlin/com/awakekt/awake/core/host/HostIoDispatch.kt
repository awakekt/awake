/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.host

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

internal suspend fun <T> withHostIoIfConfined(io: CoroutineDispatcher, block: () -> T): T {
    val interceptor = coroutineContext[ContinuationInterceptor]
    return if (interceptor == null || interceptor == Dispatchers.Unconfined || interceptor == io) {
        block()
    } else {
        withContext(io) { block() }
    }
}
