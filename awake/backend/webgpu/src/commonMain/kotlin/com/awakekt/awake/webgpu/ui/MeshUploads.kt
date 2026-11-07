/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu.ui

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUQueue

/**
 * Host memory a [DynamicMesh] uploads its arrays through, kept from one upload to the next (#1408).
 *
 * A UI mesh used to upload through `fastArrayBufferOf`, which on the web copies the array out of
 * wasm linear memory into a new `ArrayBuffer` for every array of every changed run, every frame.
 * `queue.writeBuffer` copies its source before it returns, so nothing needs a buffer of its own:
 * the web writes straight from linear memory, as `UniformUploads` does, and the JVM copies into
 * [hostBytes], grown when an array does not fit and kept.
 */
internal class MeshUploadStaging {
    /** Native bytes the JVM stages through; unused on the web, which writes from linear memory. */
    internal var hostBytes: ArrayBuffer? = null
}

/** Writes [data] to the start of [buffer] through [staging], allocating nothing once it has grown. */
internal expect fun GPUQueue.writeMeshFloats(buffer: GPUBuffer, data: FloatArray, staging: MeshUploadStaging)

/** Writes [data] to the start of [buffer] through [staging], allocating nothing once it has grown. */
internal expect fun GPUQueue.writeMeshInts(buffer: GPUBuffer, data: IntArray, staging: MeshUploadStaging)
