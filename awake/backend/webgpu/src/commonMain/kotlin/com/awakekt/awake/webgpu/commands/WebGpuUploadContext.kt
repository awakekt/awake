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
