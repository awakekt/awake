/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

/** Lifecycle of a CPU-built command packet owned by one render session. */
enum class GpuCommandLeaseState {
    Recording,
    Sealed,
    Submitted,
    Retired,
    Cancelled,
}

/**
 * Owner-confined lifetime guard for a command packet.
 *
 * This type intentionally does not use atomics: the render owner performs these transitions in
 * order, while workers return immutable packet data before [seal]. A future multi-owner path must
 * introduce a separate synchronization policy instead of making every packet field mutable.
 */
class GpuCommandLease<T>(val packet: T) {
    var state: GpuCommandLeaseState = GpuCommandLeaseState.Recording
        private set

    fun seal() {
        transition(GpuCommandLeaseState.Recording, GpuCommandLeaseState.Sealed)
    }

    fun submit() {
        transition(GpuCommandLeaseState.Sealed, GpuCommandLeaseState.Submitted)
    }

    fun retire() {
        transition(GpuCommandLeaseState.Submitted, GpuCommandLeaseState.Retired)
    }

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
