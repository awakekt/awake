/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.webgpu

import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUQueue

/**
 * Faster replacement for wgpu4k's [ArrayBuffer.of] on every hot per-frame `writeBuffer` call
 * site in this backend (vertex/index uploads, MVP and screen-size uniforms).
 *
 * Confirmed via a real Chrome DevTools Performance recording of `samples/ui-showcase` on
 * WebGPU (wasmJs): `ArrayBuffer.of` accounted for ~84% of total per-frame scripting time.
 * wgpu4k's `ArrayBuffer.of(FloatArray)`/`of(IntArray)` (webgpu-ktypes,
 * `wasmJsMain/ArrayBuffer.wasmJs.kt`, `toArrayBuffer()`) boxes every element into a
 * `JsArray<JsNumber>` and then reconstructs a typed array *from that boxed array* -- two
 * per-element wasm/JS boundary crossings per value. `ArrayBuffer.allocate` + `setFloats`/
 * `setInts` writes directly into a typed-array view over the target buffer in a single
 * per-element pass, skipping the intermediate boxed `JsArray` entirely.
 *
 * On the web that is still a JS call per element, since a Kotlin/Wasm array lives in the GC heap
 * where JS cannot see it. There the data is stored into wasm linear memory instead, four bytes per
 * wasm store, and JS copies it out in one call: a texture's or a mesh's upload, a font atlas and the
 * per-frame instance buffers then cost a store per word rather than a JS call per value.
 * `UniformUploads` writes a frame's per-draw uniforms from linear memory the same way.
 */
internal expect fun fastArrayBufferOf(data: FloatArray): ArrayBuffer

internal expect fun fastArrayBufferOf(data: IntArray): ArrayBuffer

internal expect fun fastArrayBufferOf(data: ByteArray): ArrayBuffer

/**
 * Upload exactly the source bytes. The JVM binding's default `size` uses the destination's
 * capacity, which can exceed a small instance or debug-line upload and read past native memory.
 */
internal fun GPUQueue.writeBufferData(buffer: GPUBuffer, bufferOffset: ULong, data: ArrayBuffer) {
    writeBuffer(buffer, bufferOffset, data, dataOffset = 0uL, size = data.size)
}
