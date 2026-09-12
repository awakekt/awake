/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GpuUploadLeaseTest {
    @Test
    fun completionIsDistinctFromSubmission() {
        val lease = GpuUploadLease(byteArrayOf(1, 2), byteCount = 2)

        lease.submit()
        assertEquals(GpuUploadLeaseState.Submitted, lease.state)
        lease.complete()
        assertEquals(GpuUploadLeaseState.Completed, lease.state)
    }

    @Test
    fun unsubmittedPayloadCanBeCancelled() {
        val lease = GpuUploadLease("bytes", byteCount = 5)

        lease.cancel()
        assertEquals(GpuUploadLeaseState.Cancelled, lease.state)
        assertFailsWith<IllegalStateException> { lease.cancel() }
    }

    @Test
    fun completionCannotBeClaimedBeforeSubmissionOrTwice() {
        val lease = GpuUploadLease(Unit, byteCount = 0)

        assertFailsWith<IllegalStateException> { lease.complete() }
        lease.submit()
        lease.complete()
        assertFailsWith<IllegalStateException> { lease.complete() }
    }

    @Test
    fun terminalTransitionReleasesPayloadOnce() {
        var releases = 0
        val lease = GpuUploadLease(Unit, byteCount = 0) { releases++ }

        lease.submit()
        lease.complete()
        assertEquals(1, releases)
    }

    @Test
    fun completionAndFailureRaceHasOneTerminalOwner() = runBlocking {
        val releases = atomic(0)
        val lease = GpuUploadLease(Unit, byteCount = 0) { releases.incrementAndGet() }
        lease.submit()

        val outcomes = listOf(
            async(Dispatchers.Default) { runCatching { lease.complete() }.isSuccess },
            async(Dispatchers.Default) { runCatching { lease.fail() }.isSuccess },
        ).awaitAll()

        assertEquals(1, outcomes.count { it })
        assertEquals(1, releases.value)
        assertEquals(
            true,
            lease.state == GpuUploadLeaseState.Completed || lease.state == GpuUploadLeaseState.Failed,
        )
    }

    @Test
    fun unsubmittedLeaseCanFailOnDeviceLossAndReleasesPayload() {
        var released = false
        val lease = GpuUploadLease("payload", byteCount = 7) { released = true }
        lease.fail()
        assertEquals(GpuUploadLeaseState.Failed, lease.state)
        kotlin.test.assertTrue(released)
        assertFailsWith<IllegalStateException> { lease.submit() }
        assertFailsWith<IllegalStateException> { lease.fail() }
    }

    @Test
    fun cancelledLeaseCannotFailOrComplete() {
        val lease = GpuUploadLease("payload", byteCount = 7)
        lease.cancel()
        assertFailsWith<IllegalStateException> { lease.fail() }
        assertFailsWith<IllegalStateException> { lease.complete() }
    }
}
