/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmJsInterop::class)

package com.awakekt.awake.webgpu

import io.ygdrasil.webgpu.ArrayBuffer
import kotlin.js.ExperimentalWasmJsInterop
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

internal actual fun fastArrayBufferOf(data: FloatArray): ArrayBuffer = fromLinearMemory(data.size * Float.SIZE_BYTES) { base ->
    for (index in data.indices) (base + index * Float.SIZE_BYTES).storeInt(data[index].toRawBits())
}

internal actual fun fastArrayBufferOf(data: IntArray): ArrayBuffer = fromLinearMemory(data.size * Int.SIZE_BYTES) { base ->
    for (index in data.indices) (base + index * Int.SIZE_BYTES).storeInt(data[index])
}

internal actual fun fastArrayBufferOf(data: ByteArray): ArrayBuffer = fromLinearMemory(data.size) { base ->
    // A word at a time, little-endian as linear memory is, then the bytes left over.
    val words = data.size / Int.SIZE_BYTES
    for (word in 0 until words) {
        val at = word * Int.SIZE_BYTES
        val packed = (data[at].toInt() and BYTE) or
            ((data[at + 1].toInt() and BYTE) shl 8) or
            ((data[at + 2].toInt() and BYTE) shl 16) or
            (data[at + 3].toInt() shl 24)
        (base + at).storeInt(packed)
    }
    for (at in words * Int.SIZE_BYTES until data.size) (base + at).storeByte(data[at])
}

/** A JS ArrayBuffer of [size] bytes, which [store] writes into wasm linear memory first and JS copies out at once. */
private inline fun fromLinearMemory(size: Int, store: (Pointer) -> Unit): ArrayBuffer {
    if (size == 0) return ArrayBuffer.allocate(0uL)
    return withScopedMemoryAllocator { allocator ->
        val base = allocator.allocate(size)
        store(base)
        // Read after the allocation, which may have grown the memory and replaced its buffer.
        ArrayBuffer.wrap(copyOfLinearMemory(wasmMemory, base.address.toInt(), size))
    }
}

/** A copy of [byteSize] bytes of the module's linear [memory] from [byteOffset], made in one JS call. */
@Suppress("UnusedParameter") // Read by the JS body.
private fun copyOfLinearMemory(memory: JsAny, byteOffset: Int, byteSize: Int): js.buffer.ArrayBuffer =
    js("memory.buffer.slice(byteOffset, byteOffset + byteSize)")

private const val BYTE = 0xFF
