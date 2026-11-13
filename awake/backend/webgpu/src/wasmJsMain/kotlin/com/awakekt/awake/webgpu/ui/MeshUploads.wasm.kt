/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.ui

import io.ygdrasil.webgpu.Buffer
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.Queue
import io.ygdrasil.webgpu.WGPUBuffer
import io.ygdrasil.webgpu.WGPUQueue
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * Stores the floats into scoped linear memory and writes the GPU buffer from a view of it: no
 * `ArrayBuffer` is made, where `fastArrayBufferOf` sliced a new one out per array per upload.
 */
internal actual fun GPUQueue.writeMeshFloatRange(buffer: GPUBuffer, data: FloatArray, first: Int, count: Int, staging: MeshUploadStaging) {
    if (count == 0) return
    withScopedMemoryAllocator { allocator ->
        val base = allocator.allocate(count * Float.SIZE_BYTES)
        for (i in 0 until count) (base + i * Float.SIZE_BYTES).storeInt(data[first + i].toRawBits())
        writeFromLinearMemoryAt(
            (this as Queue).handler,
            (buffer as Buffer).handler,
            first * Float.SIZE_BYTES,
            base.address.toInt(),
            count * Float.SIZE_BYTES,
        )
    }
}

/** [writeMeshFloatRange] for indices: one copy into linear memory and one `writeBuffer` per range. */
internal actual fun GPUQueue.writeMeshIntRange(buffer: GPUBuffer, data: IntArray, first: Int, count: Int, staging: MeshUploadStaging) {
    if (count == 0) return
    withScopedMemoryAllocator { allocator ->
        val base = allocator.allocate(count * Int.SIZE_BYTES)
        for (i in 0 until count) (base + i * Int.SIZE_BYTES).storeInt(data[first + i])
        writeFromLinearMemoryAt(
            (this as Queue).handler,
            (buffer as Buffer).handler,
            first * Int.SIZE_BYTES,
            base.address.toInt(),
            count * Int.SIZE_BYTES,
        )
    }
}

/** Read after the allocation, which may have grown the memory and replaced its buffer. `wasmExports`
 * is the generated glue's own module binding, which it keeps in scope for `js()` code. */
@Suppress("UnusedParameter") // Read by the JS body.
private fun writeFromLinearMemoryAt(queue: WGPUQueue, buffer: WGPUBuffer, bufferOffset: Int, byteOffset: Int, byteSize: Int): Unit =
    js("queue.writeBuffer(buffer, bufferOffset, wasmExports.memory.buffer, byteOffset, byteSize)")
