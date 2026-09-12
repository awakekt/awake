/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

import kotlinx.atomicfu.atomic

/** Terminal state for an upload payload owned by a render session. */
enum class GpuUploadLeaseState {
    Prepared,
    Submitted,
    Completed,
    Cancelled,
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
    val payload: T,
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

    fun submit() {
        transition(GpuUploadLeaseState.Prepared, GpuUploadLeaseState.Submitted)
    }

    fun complete() {
        transition(GpuUploadLeaseState.Submitted, GpuUploadLeaseState.Completed)
        releaseOnce()
    }

    fun cancel() {
        check(stateRef.compareAndSet(GpuUploadLeaseState.Prepared, GpuUploadLeaseState.Cancelled)) {
            "Only an unsubmitted upload lease can be cancelled; state=${stateRef.value}."
        }
        releaseOnce()
    }

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
