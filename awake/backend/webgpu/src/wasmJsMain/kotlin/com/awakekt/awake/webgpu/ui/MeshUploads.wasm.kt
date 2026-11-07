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
internal actual fun GPUQueue.writeMeshFloats(buffer: GPUBuffer, data: FloatArray, staging: MeshUploadStaging) {
    if (data.isEmpty()) return
    withScopedMemoryAllocator { allocator ->
        val base = allocator.allocate(data.size * Float.SIZE_BYTES)
        for (i in data.indices) (base + i * Float.SIZE_BYTES).storeInt(data[i].toRawBits())
        writeFromLinearMemory((this as Queue).handler, (buffer as Buffer).handler, base.address.toInt(), data.size * Float.SIZE_BYTES)
    }
}

/** [writeMeshFloats] for indices. */
internal actual fun GPUQueue.writeMeshInts(buffer: GPUBuffer, data: IntArray, staging: MeshUploadStaging) {
    if (data.isEmpty()) return
    withScopedMemoryAllocator { allocator ->
        val base = allocator.allocate(data.size * Int.SIZE_BYTES)
        for (i in data.indices) (base + i * Int.SIZE_BYTES).storeInt(data[i])
        writeFromLinearMemory((this as Queue).handler, (buffer as Buffer).handler, base.address.toInt(), data.size * Int.SIZE_BYTES)
    }
}

/** Read after the allocation, which may have grown the memory and replaced its buffer. `wasmExports`
 * is the generated glue's own module binding, which it keeps in scope for `js()` code. */
@Suppress("UnusedParameter") // Read by the JS body.
private fun writeFromLinearMemory(queue: WGPUQueue, buffer: WGPUBuffer, byteOffset: Int, byteSize: Int): Unit =
    js("queue.writeBuffer(buffer, 0, wasmExports.memory.buffer, byteOffset, byteSize)")
