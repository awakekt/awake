/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.renderer

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.JvmArrayBuffer
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout

/** One native copy of the frame's floats, grown as it needs and kept, then a write per buffer from it. */
internal actual fun GPUQueue.writeStaged(uploads: UniformUploads) {
    val bytes = uploads.floatCount.toLong() * Float.SIZE_BYTES
    val held = uploads.hostBytes
    val host = held?.takeIf { it.size.toLong() >= bytes }
        ?: ArrayBuffer.allocate(maxOf(bytes, 2 * (held?.size?.toLong() ?: 0L)).toULong()).also { uploads.hostBytes = it }
    MemorySegment.copy(uploads.floats, 0, (host as JvmArrayBuffer).buffer, ValueLayout.JAVA_FLOAT_UNALIGNED, 0L, uploads.floatCount)
    val starts = uploads.starts
    for (block in uploads.buffers.indices) {
        writeBuffer(
            uploads.buffers[block],
            0uL,
            host,
            (starts[block].toLong() * Float.SIZE_BYTES).toULong(),
            ((starts[block + 1] - starts[block]).toLong() * Float.SIZE_BYTES).toULong(),
        )
    }
}
