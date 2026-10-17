/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import io.ygdrasil.webgpu.ArrayBuffer

internal actual fun fastArrayBufferOf(data: FloatArray): ArrayBuffer =
    ArrayBuffer.allocate((Float.SIZE_BYTES * data.size).toULong()).apply { setFloats(0uL, data) }

internal actual fun fastArrayBufferOf(data: IntArray): ArrayBuffer =
    ArrayBuffer.allocate((Int.SIZE_BYTES * data.size).toULong()).apply { setInts(0uL, data) }

internal actual fun fastArrayBufferOf(data: ByteArray): ArrayBuffer =
    ArrayBuffer.allocate(data.size.toULong()).apply { setBytes(0uL, data) }
