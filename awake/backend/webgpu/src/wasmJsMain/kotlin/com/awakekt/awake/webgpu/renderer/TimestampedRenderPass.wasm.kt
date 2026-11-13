/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import io.ygdrasil.webgpu.CommandEncoder
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUComputePassDescriptor
import io.ygdrasil.webgpu.GPUComputePassEncoder
import io.ygdrasil.webgpu.GPURenderPassDescriptor
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.QuerySet
import io.ygdrasil.webgpu.WGPUCommandEncoder
import io.ygdrasil.webgpu.WGPUQuerySet
import kotlin.js.ExperimentalWasmJsInterop

/**
 * The browser binding's descriptor mapper drops `timestampWrites`, so no pass ever wrote a
 * timestamp, every resolved query read 0, and the frame's GPU time read 0.0 (#1545). The writes
 * are put back on the mapped descriptor, for the next pass this encoder begins only.
 */
internal actual fun GPUCommandEncoder.beginTimestampedRenderPass(descriptor: GPURenderPassDescriptor): GPURenderPassEncoder {
    descriptor.timestampWrites?.let { writes ->
        armTimestampWrites(
            (this as CommandEncoder).handler,
            "beginRenderPass",
            (writes.querySet as QuerySet).handler,
            checkNotNull(writes.beginningOfPassWriteIndex).toInt(),
            checkNotNull(writes.endOfPassWriteIndex).toInt(),
        )
    }
    return beginRenderPass(descriptor)
}

/** [beginTimestampedRenderPass] for compute passes, whose mapper drops the writes the same way. */
internal actual fun GPUCommandEncoder.beginTimestampedComputePass(descriptor: GPUComputePassDescriptor): GPUComputePassEncoder {
    descriptor.timestampWrites?.let { writes ->
        armTimestampWrites(
            (this as CommandEncoder).handler,
            "beginComputePass",
            (writes.querySet as QuerySet).handler,
            checkNotNull(writes.beginningOfPassWriteIndex).toInt(),
            checkNotNull(writes.endOfPassWriteIndex).toInt(),
        )
    }
    return beginComputePass(descriptor)
}

/** Shadows [method] on this one encoder for one call, adding the writes to the descriptor it gets. */
@Suppress("UnusedParameter") // Read by the JS body.
private fun armTimestampWrites(encoder: WGPUCommandEncoder, method: String, querySet: WGPUQuerySet, begin: Int, end: Int): Unit =
    js(
        """{
        const original = Object.getPrototypeOf(encoder)[method];
        encoder[method] = function (descriptor) {
            delete encoder[method];
            const mapped = Object.assign({}, descriptor || {});
            mapped.timestampWrites = { querySet: querySet, beginningOfPassWriteIndex: begin, endOfPassWriteIndex: end };
            return original.call(encoder, mapped);
        };
    }""",
    )
