/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.io

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SuspendRunCatchingTest {
    @Test
    fun aValueAndAFailureBecomeTheResult() = runTest {
        assertEquals(Result.success(3), suspendRunCatching { 3 })
        val failed = suspendRunCatching<Int> { error("unreadable") }
        assertEquals("unreadable", failed.exceptionOrNull()?.message)
    }

    /** runCatching would return the cancellation as a failure and let the cancelled read carry on. */
    @Test
    fun aCancellationIsRethrownSoTheCoroutineStops() = runTest {
        val never = CompletableDeferred<Unit>()
        var carriedOn = false
        val read = async(start = CoroutineStart.UNDISPATCHED) {
            suspendRunCatching { never.await() }
            carriedOn = true
        }
        read.cancel()

        assertFailsWith<CancellationException> { read.await() }
        assertTrue(read.isCancelled)
        assertEquals(false, carriedOn, "nothing after the cancelled call ran")
    }
}
