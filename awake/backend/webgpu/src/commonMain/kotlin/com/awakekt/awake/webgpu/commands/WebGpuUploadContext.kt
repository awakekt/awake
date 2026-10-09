/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.commands

import com.awakekt.awake.render.command.GpuUploadLease
import com.awakekt.awake.webgpu.device.GraphicsDevice

/**
 * Queue-write upload owner for WebGPU.
 *
 * WebGPU has no portable blocking idle operation. [runUpload] therefore suspends on the queue's
 * completion token and completes the lease only after the queue reports that prior writes have
 * finished. The caller's payload is released by the lease callback at that point.
 */
class WebGpuUploadContext(
    private val graphicsDevice: GraphicsDevice,
) {
    /**
     * Runs one queue-write upload and completes [lease] only after the queue has drained it.
     *
     * Calls [GpuUploadLease.submit], hands the lease's payload to [submit], then suspends on the
     * queue's submitted-work-done signal before calling [GpuUploadLease.complete]. If [submit] or
     * the completion signal throws, the lease is marked failed, which releases its payload exactly
     * once, and the original exception is rethrown.
     *
     * @param T The payload type the lease carries.
     * @param lease The lease owning the upload payload; it must still be in the prepared state.
     * @param submit Issues the queue writes for the payload (`writeBuffer`, `writeTexture`). It
     * runs synchronously on the calling coroutine, before the suspension.
     * @throws IllegalStateException If [lease] is not in the prepared state; the lease is left
     * untouched.
     */
    suspend fun <T> runUpload(
        lease: GpuUploadLease<T>,
        submit: (T) -> Unit,
    ) {
        lease.submit()
        try {
            submit(lease.payload)
            graphicsDevice.wgpuContext.device.queue.onSubmittedWorkDone().getOrThrow()
            lease.complete()
        } catch (failure: Throwable) {
            lease.fail()
            throw failure
        }
    }
}
