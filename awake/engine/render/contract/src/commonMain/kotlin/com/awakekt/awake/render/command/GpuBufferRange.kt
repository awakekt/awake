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
 */
data class GpuBufferRange(
    val buffer: BufferHandle,
    val offsetBytes: Long = 0L,
    val sizeBytes: Long? = null,
) {
    init {
        require(offsetBytes >= 0L) { "Buffer range offset must be non-negative." }
        require(sizeBytes == null || sizeBytes >= 0L) { "Buffer range size must be non-negative." }
    }
}
