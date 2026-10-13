/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu.renderer

import io.ygdrasil.webgpu.Buffer
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.Queue
import io.ygdrasil.webgpu.WGPUBuffer
import io.ygdrasil.webgpu.WGPUQueue
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * A Kotlin/Wasm array lives in the GC heap, where JS cannot see it, so the floats are stored into
 * linear memory, which JS can, and each block is written from there: wasm stores and one call per
 * buffer, where the direct path made a JS call per float.
 */
internal actual fun GPUQueue.writeStaged(uploads: UniformUploads) {
    val queue = (this as Queue).handler
    val floats = uploads.floats
    val starts = uploads.starts
    withScopedMemoryAllocator { allocator ->
        val base = allocator.allocate(uploads.floatCount * Float.SIZE_BYTES)
        for (i in 0 until uploads.floatCount) (base + i * Float.SIZE_BYTES).storeInt(floats[i].toRawBits())
        val address = base.address.toInt()
        for (block in uploads.buffers.indices) {
            writeFromLinearMemory(
                queue,
                (uploads.buffers[block] as Buffer).handler,
                address + starts[block] * Float.SIZE_BYTES,
                (starts[block + 1] - starts[block]) * Float.SIZE_BYTES,
            )
        }
    }
}

/** `wasmExports` is the generated glue's own module binding, which it keeps in scope for `js()` code. */
@Suppress("UnusedParameter") // Read by the JS body.
private fun writeFromLinearMemory(queue: WGPUQueue, buffer: WGPUBuffer, byteOffset: Int, byteSize: Int): Unit =
    js("queue.writeBuffer(buffer, 0, wasmExports.memory.buffer, byteOffset, byteSize)")
