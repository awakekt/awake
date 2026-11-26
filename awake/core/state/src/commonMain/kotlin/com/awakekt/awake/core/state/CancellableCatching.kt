/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Runs [block] and wraps any failure in [Result.failure] while preserving structured concurrency:
 * - If the calling coroutine is cancelled, rethrows [CancellationException] via
 *   [currentCoroutineContext]`().ensureActive()` so timeouts and parent cancellations are never
 *   swallowed.
 * - If [block] throws a *foreign* [CancellationException] (for example, from a closed channel,
 *   aborted socket, or cancelled downstream client while the caller is still active), converts it
 *   into [Result.failure] instead of cancelling the caller.
 *
 * @param T Return value type.
 * @param block Suspending computation to execute.
 * @return [Result.success] with the return value, or [Result.failure] with the non-local failure.
 */
@Suppress("TooGenericExceptionCaught")
suspend inline fun <T> runCancellableCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    currentCoroutineContext().ensureActive()
    Result.failure(cancelled)
} catch (failure: Throwable) {
    Result.failure(failure)
}
