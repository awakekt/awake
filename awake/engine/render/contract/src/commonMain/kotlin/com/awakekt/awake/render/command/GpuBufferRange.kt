/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.render.command

/**
 * A bounded view of a GPU buffer used by a resolved command.
 *
 * The range is expressed in bytes so the compiler can share one packet between drivers. A
 * backend may translate it to a native buffer view or a WebGPU vertex/index binding without
 * learning why the bytes were uploaded.
 * @property buffer Hardware buffer handle reference.
 * @property offsetBytes Byte offset from the start of the buffer.
 * @property sizeBytes Number of bytes in the range, or null to bind through to the end of the buffer.
 */
data class GpuBufferRange(
    /** Hardware buffer handle reference. */
    val buffer: BufferHandle,
    /** Byte offset from the start of the buffer. */
    val offsetBytes: Long = 0L,
    /** Number of bytes in the range, or null to bind through to the end of the buffer. */
    val sizeBytes: Long? = null,
) {
    init {
        require(offsetBytes >= 0L) { "Buffer range offset must be non-negative." }
        require(sizeBytes == null || sizeBytes >= 0L) { "Buffer range size must be non-negative." }
    }
}
