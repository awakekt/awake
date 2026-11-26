/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.JvmArrayBuffer
import io.ygdrasil.webgpu.Origin3D
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
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

@Suppress("LongParameterList")
internal actual fun GPUQueue.writeTextureBytes(
    texture: GPUTexture,
    data: ByteArray,
    bytesPerRow: Int,
    rowsPerImage: Int,
    width: Int,
    height: Int,
    depthOrArrayLayers: Int,
    mipLevel: Int,
    originX: Int,
    originY: Int,
    originZ: Int,
) {
    if (data.isEmpty()) return
    if (width <= 0 || height <= 0 || depthOrArrayLayers <= 0) return
    val host = ArrayBuffer.allocate(data.size.toULong())
    MemorySegment.copy(data, 0, (host as JvmArrayBuffer).buffer, ValueLayout.JAVA_BYTE, 0L, data.size)
    writeTexture(
        destination = TexelCopyTextureInfo(
            texture = texture,
            mipLevel = mipLevel.toUInt(),
            origin = Origin3D(originX.toUInt(), originY.toUInt(), originZ.toUInt()),
        ),
        data = host,
        dataLayout = TexelCopyBufferLayout(
            bytesPerRow = bytesPerRow.toUInt(),
            rowsPerImage = rowsPerImage.toUInt(),
        ),
        size = Extent3D(
            width = width.toUInt(),
            height = height.toUInt(),
            depthOrArrayLayers = depthOrArrayLayers.toUInt(),
        ),
    )
}
