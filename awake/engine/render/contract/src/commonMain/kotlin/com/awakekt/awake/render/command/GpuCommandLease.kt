/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

/** Lifecycle of a CPU-built command packet owned by one render session. */
enum class GpuCommandLeaseState {
    /** The command packet is actively being constructed. */
    Recording,
    /** The command packet is sealed and immutable, awaiting GPU submission. */
    Sealed,
    /** The command packet has been submitted to the GPU command queue. */
    Submitted,
    /** The command packet execution has finished on the GPU. */
    Retired,
    /** The command packet was cancelled before submission. */
    Cancelled,
}

/**
 * Owner-confined lifetime guard for a command packet.
 *
 * This type intentionally does not use atomics: the render owner performs these transitions in
 * order, while workers return immutable packet data before [seal]. A future multi-owner path must
 * introduce a separate synchronization policy instead of making every packet field mutable.
 */
class GpuCommandLease<T>(
    /** The command packet payload owned by this lease. */
    val packet: T,
) {
    /** The current lifecycle state of this command lease. */
    var state: GpuCommandLeaseState = GpuCommandLeaseState.Recording
        private set

    /** Seals the command packet, transitioning from [GpuCommandLeaseState.Recording] to [GpuCommandLeaseState.Sealed]. */
    fun seal() {
        transition(GpuCommandLeaseState.Recording, GpuCommandLeaseState.Sealed)
    }

    /** Transitions this lease from [GpuCommandLeaseState.Sealed] to [GpuCommandLeaseState.Submitted]. */
    fun submit() {
        transition(GpuCommandLeaseState.Sealed, GpuCommandLeaseState.Submitted)
    }

    /** Transitions this lease from [GpuCommandLeaseState.Submitted] to [GpuCommandLeaseState.Retired]. */
    fun retire() {
        transition(GpuCommandLeaseState.Submitted, GpuCommandLeaseState.Retired)
    }

    /** Cancels an unsubmitted command lease, transitioning to [GpuCommandLeaseState.Cancelled]. */
    fun cancel() {
        check(state == GpuCommandLeaseState.Recording || state == GpuCommandLeaseState.Sealed) {
            "Only an unsubmitted command lease can be cancelled; state=$state."
        }
        state = GpuCommandLeaseState.Cancelled
    }

    private fun transition(expected: GpuCommandLeaseState, next: GpuCommandLeaseState) {
        check(state == expected) {
            "Cannot transition command lease from $state to $next; expected $expected."
        }
        state = next
    }
}
