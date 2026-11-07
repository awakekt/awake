/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.JvmArrayBuffer
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

internal actual fun GPUQueue.writeMeshFloats(buffer: GPUBuffer, data: FloatArray, staging: MeshUploadStaging) {
    val bytes = data.size.toLong() * Float.SIZE_BYTES
    val host = staging.hostFor(bytes)
    MemorySegment.copy(data, 0, (host as JvmArrayBuffer).buffer, ValueLayout.JAVA_FLOAT_UNALIGNED, 0L, data.size)
    writeBuffer(buffer, 0uL, host, 0uL, bytes.toULong())
}

internal actual fun GPUQueue.writeMeshInts(buffer: GPUBuffer, data: IntArray, staging: MeshUploadStaging) {
    val bytes = data.size.toLong() * Int.SIZE_BYTES
    val host = staging.hostFor(bytes)
    MemorySegment.copy(data, 0, (host as JvmArrayBuffer).buffer, ValueLayout.JAVA_INT_UNALIGNED, 0L, data.size)
    writeBuffer(buffer, 0uL, host, 0uL, bytes.toULong())
}

/** The kept host bytes, doubled when [bytes] do not fit, so a steady mesh stops allocating. */
private fun MeshUploadStaging.hostFor(bytes: Long): ArrayBuffer {
    val held = hostBytes
    if (held != null && held.size.toLong() >= bytes) return held
    return ArrayBuffer.allocate(maxOf(bytes, 2 * (held?.size?.toLong() ?: 0L)).toULong()).also { hostBytes = it }
}
