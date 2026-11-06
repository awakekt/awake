/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for a [block] that suspends: a failure becomes a failed [Result], except the
 * cancellation of the coroutine running it, which is rethrown so that coroutine stops instead of
 * carrying on with a failed result.
 *
 * `runCatching` around a suspend call catches that cancellation too, so a cancelled read reports an
 * error and the coroutine keeps running; detekt's `SuspendFunSwallowedCancellation` reports it. A
 * cancellation [block] raised itself while this coroutine is still active, such as its own
 * `withTimeout` running out, is a failure like any other, as it was with `runCatching`.
 *
 * @param T The type [block] returns.
 * @param block The work to run.
 * @return [block]'s value, or the failure it threw.
 */
// TooGenericExceptionCaught: as runCatching, every failure but this coroutine's cancellation becomes the result.
// SuspendFunSwallowedCancellation: ensureActive rethrows when this coroutine is the one cancelled.
@Suppress("TooGenericExceptionCaught", "SuspendFunSwallowedCancellation")
suspend inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (failure: Throwable) {
    if (failure is CancellationException) currentCoroutineContext().ensureActive()
    Result.failure(failure)
}
