/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.state

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CancellableCatchingTest {
    private class FakeFetchError : Error("Fail to fetch")

    @Test
    fun capturesValuesNormalExceptionsAndNonExceptionErrors() = runTest {
        assertEquals(Result.success(7), runCancellableCatching { 7 })

        val failed = runCancellableCatching<Int> { error("boom") }
        assertTrue(failed.isFailure)
        assertIs<IllegalStateException>(failed.exceptionOrNull())

        val fetchError = runCancellableCatching<Int> { throw FakeFetchError() }
        assertTrue(fetchError.isFailure)
        assertIs<FakeFetchError>(fetchError.exceptionOrNull())
    }

    @Test
    fun convertsForeignCancellationIntoFailureWhileCallerIsActive() = runTest {
        val result = runCancellableCatching<Unit> {
            throw CancellationException("downstream client cancelled")
        }
        assertTrue(result.isFailure)
        assertIs<CancellationException>(result.exceptionOrNull())
    }

    @Test
    fun rethrowsOwnCoroutineCancellationAndTimeouts() = runTest {
        val started = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        var swallowed = false
        val job = launch {
            val result = runCancellableCatching {
                started.complete(Unit)
                never.await()
            }
            swallowed = result.isFailure
        }
        started.await()
        job.cancel()
        job.join()
        assertFalse(swallowed, "own cancellation must propagate instead of returning Result.failure")

        assertFailsWith<CancellationException> {
            withTimeout(1) {
                runCancellableCatching { delay(5_000) }
            }
        }
    }
}
