/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.device

import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.WGPUContext

/**
 * Closes [device] and the queue handle it fetched at creation. wgpu4k's native `Device` takes a
 * reference to its queue and never gives it back, and the queue keeps the device alive: closing the
 * device alone leaves the native device and its memory allocated until the process exits.
 */
internal fun closeDevice(device: GPUDevice) {
    try {
        device.close()
    } finally {
        device.releaseQueueHandle()
    }
}

/**
 * Releases [context]'s objects children first, as [WGPUContext.close] does not: the surface, which is
 * configured against the device, then the device and its queue, then the adapter. Every resource
 * created from the device must already be closed. The instance that produced the adapter is not part
 * of the context, so whoever created it releases it after this.
 */
internal fun closeContext(context: WGPUContext) {
    try {
        context.surface.close()
    } finally {
        try {
            closeDevice(context.device)
        } finally {
            context.adapter.close()
        }
    }
}

/**
 * Finishes [encoder], submits its commands and releases both: the queue keeps what it runs, and
 * wgpu-native otherwise keeps every submitted command buffer registered against the device.
 */
internal fun GPUQueue.submitAndRelease(encoder: GPUCommandEncoder) {
    val commands = encoder.finish()
    try {
        submit(listOf(commands))
    } finally {
        commands.close()
        encoder.close()
    }
}

/** Gives back the queue reference a native device took at creation; nothing to do in a browser. */
internal expect fun GPUDevice.releaseQueueHandle()
