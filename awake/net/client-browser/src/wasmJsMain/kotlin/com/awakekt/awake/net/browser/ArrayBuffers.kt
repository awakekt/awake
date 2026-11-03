/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmJsInterop::class)

package com.awakekt.awake.net.browser

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

// A packet crosses between Kotlin and JS with one copy each way, through wasm linear memory, as the
// WebGPU backend's uploads do: indexing a typed array from Kotlin would cross the boundary per element.

/** These bytes as a new `ArrayBuffer` of their own: written to linear memory, then copied out in one JS call. */
internal fun ByteArray.toArrayBuffer(): JsAny = withScopedMemoryAllocator { allocator ->
    val base = allocator.allocate(size.coerceAtLeast(1))
    storeBytes(base, this)
    // Read after the allocation, which may have grown the memory and replaced its buffer.
    sliceOfLinearMemory(wasmMemory, base.address.toInt(), size)
}

/** This `ArrayBuffer`'s bytes: copied into linear memory in one JS call, then read from it. */
internal fun JsAny.toByteArray(): ByteArray {
    val size = byteLength(this)
    if (size == 0) return ByteArray(0)
    return withScopedMemoryAllocator { allocator ->
        val base = allocator.allocate(size)
        copyIntoLinearMemory(this, wasmMemory, base.address.toInt())
        loadBytes(base, size)
    }
}

/** A word at a time, little-endian as linear memory is, then the bytes left over. */
private fun storeBytes(base: Pointer, bytes: ByteArray) {
    val words = bytes.size / Int.SIZE_BYTES
    for (word in 0 until words) {
        val at = word * Int.SIZE_BYTES
        val packed = (bytes[at].toInt() and BYTE) or
            ((bytes[at + 1].toInt() and BYTE) shl BYTE_1) or
            ((bytes[at + 2].toInt() and BYTE) shl BYTE_2) or
            (bytes[at + 3].toInt() shl BYTE_3)
        (base + at).storeInt(packed)
    }
    for (at in words * Int.SIZE_BYTES until bytes.size) (base + at).storeByte(bytes[at])
}

/** A word at a time, then the bytes left over. */
private fun loadBytes(base: Pointer, size: Int): ByteArray {
    val bytes = ByteArray(size)
    val words = size / Int.SIZE_BYTES
    for (word in 0 until words) {
        val at = word * Int.SIZE_BYTES
        val packed = (base + at).loadInt()
        bytes[at] = packed.toByte()
        bytes[at + 1] = (packed ushr BYTE_1).toByte()
        bytes[at + 2] = (packed ushr BYTE_2).toByte()
        bytes[at + 3] = (packed ushr BYTE_3).toByte()
    }
    for (at in words * Int.SIZE_BYTES until size) bytes[at] = (base + at).loadByte()
    return bytes
}

@Suppress("UnusedParameter") // Read by the JS body.
private fun sliceOfLinearMemory(memory: JsAny, byteOffset: Int, byteSize: Int): JsAny =
    js("memory.buffer.slice(byteOffset, byteOffset + byteSize)")

@Suppress("UnusedParameter") // Read by the JS body.
private fun copyIntoLinearMemory(buffer: JsAny, memory: JsAny, byteOffset: Int): Unit =
    js("new Uint8Array(memory.buffer, byteOffset, buffer.byteLength).set(new Uint8Array(buffer))")

@Suppress("UnusedParameter") // Read by the JS body.
private fun byteLength(buffer: JsAny): Int = js("buffer.byteLength")

private const val BYTE = 0xFF
private const val BYTE_1 = 8
private const val BYTE_2 = 16
private const val BYTE_3 = 24
