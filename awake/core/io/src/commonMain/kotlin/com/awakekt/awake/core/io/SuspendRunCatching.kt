/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] for a [block] that suspends: a failure becomes a failed [Result], except a
 * [CancellationException], which is rethrown so the coroutine that was cancelled stops instead of
 * carrying on with a failed result.
 *
 * `runCatching` around a suspend call catches the cancellation too, so a cancelled read reports an
 * error and the coroutine keeps running; detekt's `SuspendFunSwallowedCancellation` reports it.
 *
 * @param T The type [block] returns.
 * @param block The work to run.
 * @return [block]'s value, or the failure it threw.
 */
// TooGenericExceptionCaught: as runCatching, every failure but a cancellation becomes the result.
@Suppress("TooGenericExceptionCaught")
inline fun <T> suspendRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Throwable) {
    Result.failure(failure)
}
