/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.JvmArrayBuffer
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal actual fun fastArrayBufferOf(data: FloatArray): ArrayBuffer =
    ArrayBuffer.allocate((Float.SIZE_BYTES * data.size).toULong()).apply { setFloats(0uL, data) }

internal actual fun fastArrayBufferOf(data: IntArray): ArrayBuffer =
    ArrayBuffer.allocate((Int.SIZE_BYTES * data.size).toULong()).apply { setInts(0uL, data) }

internal actual fun fastArrayBufferOf(data: ByteArray): ArrayBuffer =
    ArrayBuffer.allocate(data.size.toULong()).apply { setBytes(0uL, data) }

internal actual fun GPUQueue.writeBufferFloats(buffer: GPUBuffer, bufferOffset: ULong, data: FloatArray, count: Int) {
    if (count == 0) return
    val bytes = count.toULong() * Float.SIZE_BYTES.toULong()
    val host = ArrayBuffer.allocate(bytes)
    MemorySegment.copy(data, 0, (host as JvmArrayBuffer).buffer, ValueLayout.JAVA_FLOAT_UNALIGNED, 0L, count)
    writeBuffer(buffer, bufferOffset, host, dataOffset = 0uL, size = bytes)
}

internal actual fun GPUQueue.writeBufferInts(buffer: GPUBuffer, bufferOffset: ULong, data: IntArray, count: Int) {
    if (count == 0) return
    val bytes = count.toULong() * Int.SIZE_BYTES.toULong()
    val host = ArrayBuffer.allocate(bytes)
    MemorySegment.copy(data, 0, (host as JvmArrayBuffer).buffer, ValueLayout.JAVA_INT_UNALIGNED, 0L, count)
    writeBuffer(buffer, bufferOffset, host, dataOffset = 0uL, size = bytes)
}
