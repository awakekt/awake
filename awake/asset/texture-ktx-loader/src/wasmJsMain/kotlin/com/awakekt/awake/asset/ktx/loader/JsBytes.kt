/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmJsInterop::class)

package com.awakekt.awake.asset.ktx.loader

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/** These bytes as a JS `Uint8Array` of their own: written to linear memory, then copied out in one JS call. */
internal fun ByteArray.toUint8Array(): JsAny = withScopedMemoryAllocator { allocator ->
    val base = allocator.allocate(size.coerceAtLeast(1))
    storeBytes(base, this)
    // Read after the allocation, which may have grown the memory and replaced its buffer.
    copyOfLinearMemory(wasmMemory, base.address.toInt(), size)
}

/** This JS `Uint8Array`'s bytes: copied into linear memory in one JS call, then read from it. */
internal fun JsAny.toByteArray(): ByteArray {
    val size = byteLength(this)
    return withScopedMemoryAllocator { allocator ->
        val base = allocator.allocate(size.coerceAtLeast(1))
        copyIntoLinearMemory(this, wasmMemory, base.address.toInt())
        ByteArray(size) { (base + it).loadByte() }
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

@Suppress("UnusedParameter") // Read by the JS body.
private fun copyOfLinearMemory(memory: JsAny, byteOffset: Int, byteSize: Int): JsAny =
    js("new Uint8Array(memory.buffer.slice(byteOffset, byteOffset + byteSize))")

@Suppress("UnusedParameter") // Read by the JS body.
private fun copyIntoLinearMemory(bytes: JsAny, memory: JsAny, byteOffset: Int): Unit =
    js("new Uint8Array(memory.buffer, byteOffset, bytes.length).set(bytes)")

@Suppress("UnusedParameter") // Read by the JS body.
private fun byteLength(bytes: JsAny): Int = js("bytes.length")

private const val BYTE = 0xFF
private const val BYTE_1 = 8
private const val BYTE_2 = 16
private const val BYTE_3 = 24
