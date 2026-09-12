/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GpuCommandLeaseTest {
    @Test
    fun ownerTransitionsFromRecordingToRetired() {
        val lease = GpuCommandLease("packet")
        assertEquals(GpuCommandLeaseState.Recording, lease.state)
        lease.seal()
        lease.submit()
        lease.retire()
        assertEquals(GpuCommandLeaseState.Retired, lease.state)
    }

    @Test
    fun duplicateSubmissionAndLateCancellationAreRejected() {
        val lease = GpuCommandLease("packet")
        lease.seal()
        lease.submit()
        assertFailsWith<IllegalStateException> { lease.submit() }
        assertFailsWith<IllegalStateException> { lease.cancel() }
    }

    @Test
    fun unsubmittedPacketCanBeCancelledOnce() {
        val lease = GpuCommandLease("packet")
        lease.cancel()
        assertEquals(GpuCommandLeaseState.Cancelled, lease.state)
        assertFailsWith<IllegalStateException> { lease.cancel() }
    }

    @Test
    fun sealedUnsubmittedPacketCanBeCancelled() {
        val lease = GpuCommandLease("packet")
        lease.seal()
        assertEquals(GpuCommandLeaseState.Sealed, lease.state)
        lease.cancel()
        assertEquals(GpuCommandLeaseState.Cancelled, lease.state)
        assertFailsWith<IllegalStateException> { lease.submit() }
    }
}
