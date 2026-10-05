/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import kotlinx.atomicfu.atomic

/** Terminal state for an upload payload owned by a render session. */
enum class GpuUploadLeaseState {
    /** The upload payload has been prepared and staged, awaiting submission. */
    Prepared,
    /** The upload operation has been submitted to the GPU command queue. */
    Submitted,
    /** The upload operation has completed on the GPU and resources are released. */
    Completed,
    /** The upload operation was cancelled prior to submission. */
    Cancelled,
    /** The upload operation or device context failed. */
    Failed,
}

/**
 * Owner-confined lifetime guard for CPU upload data.
 *
 * Backends call [submit] only after the device has accepted the operation and call [complete]
 * from the backend's completion point. The payload remains owned by this lease until a terminal
 * transition, so cancellation and device loss can release it exactly once.
 */
class GpuUploadLease<T>(
    /** The upload payload object being leased. */
    val payload: T,
    /** Total size in bytes of the leased upload payload. */
    val byteCount: Long,
    private val release: (T) -> Unit = {},
) {
    private val stateRef = atomic(GpuUploadLeaseState.Prepared)
    private val releasedRef = atomic(false)

    /** A cross-thread snapshot; completion callbacks and cancellation may race. */
    val state: GpuUploadLeaseState get() = stateRef.value

    init {
        require(byteCount >= 0L) { "An upload lease byte count must be non-negative." }
    }

    /** Transitions this lease from [GpuUploadLeaseState.Prepared] to [GpuUploadLeaseState.Submitted]. */
    fun submit() {
        transition(GpuUploadLeaseState.Prepared, GpuUploadLeaseState.Submitted)
    }

    /** Transitions this lease to [GpuUploadLeaseState.Completed] and invokes the release callback once. */
    fun complete() {
        transition(GpuUploadLeaseState.Submitted, GpuUploadLeaseState.Completed)
        releaseOnce()
    }

    /** Cancels an unsubmitted lease and invokes the release callback once. */
    fun cancel() {
        check(stateRef.compareAndSet(GpuUploadLeaseState.Prepared, GpuUploadLeaseState.Cancelled)) {
            "Only an unsubmitted upload lease can be cancelled; state=${stateRef.value}."
        }
        releaseOnce()
    }

    /** Marks this lease as failed and invokes the release callback once. */
    fun fail() {
        while (true) {
            val current = stateRef.value
            check(current == GpuUploadLeaseState.Prepared || current == GpuUploadLeaseState.Submitted) {
                "Only an active upload lease can fail; state=$current."
            }
            if (stateRef.compareAndSet(current, GpuUploadLeaseState.Failed)) break
        }
        releaseOnce()
    }

    private fun releaseOnce() {
        if (releasedRef.compareAndSet(expect = false, update = true)) release(payload)
    }

    private fun transition(expected: GpuUploadLeaseState, next: GpuUploadLeaseState) {
        check(stateRef.compareAndSet(expected, next)) {
            "Cannot transition upload lease from ${stateRef.value} to $next; expected $expected."
        }
    }
}
