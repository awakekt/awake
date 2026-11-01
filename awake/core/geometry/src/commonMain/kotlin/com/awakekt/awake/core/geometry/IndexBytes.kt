/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package com.awakekt.awake.core.geometry

/**
 * This index list as little-endian 32-bit bytes, the layout every GPU index buffer expects.
 *
 * Hand-rolled because Kotlin's COMMON stdlib has no endian-aware byte accessors -- verified
 * against 2.4.10, where `setIntLe`, `getIntAt`, `getIntLeAt` and `Int.toByteArray()` all fail to
 * resolve. The JVM answer, `ByteBuffer.order(LITTLE_ENDIAN)`, cannot be used from `commonMain`,
 * and pulling in kotlinx-io for one conversion would add a multiplatform dependency -- and a
 * transitive constraint on every consumer of this published library -- to delete a dozen lines.
 *
 * Here rather than in a backend because the gap it works around is platform-neutral, not
 * Vulkan's. It lives beside [MeshGeometry] since indices are what it converts; today's callers
 * are Vulkan's `Mesh` and `DynamicMesh`, which had byte-identical private copies of it.
 */
fun IntArray.toByteArrayLE(): ByteArray = toByteArrayLE(ByteArray(size * Int.SIZE_BYTES))

/**
 * Writes this index list as little-endian 32-bit bytes into [into], reusing it across calls.
 *
 * The allocation-free counterpart to [toByteArrayLE] for a per-frame caller: a UI mesh re-uploads
 * its indices every frame, and a fresh [ByteArray] per upload is exactly the per-frame garbage the
 * `awake-core-math` rules forbid. A caller keeps one buffer and hands it back each frame; this
 * returns that same buffer when it already fits, or a larger one grown to this list's byte length
 * when it does not. The returned buffer's length is always `size * 4` -- it never reports an old,
 * larger capacity -- so a backend can upload `returned` directly without tracking a separate count.
 *
 * Because the returned buffer may be [into] itself, a caller must finish using the previous result
 * (for example, the GPU upload copies it) before calling this again with the same [into].
 *
 * @param into A buffer to fill; grown when it is shorter than `size * 4` bytes.
 * @return [into] when it is exactly `size * 4` bytes, otherwise a freshly grown buffer of that length.
 */
fun IntArray.toByteArrayLE(into: ByteArray): ByteArray {
    val byteCount = size * Int.SIZE_BYTES
    val out = if (into.size == byteCount) into else ByteArray(byteCount)
    for (i in indices) {
        val value = this[i]
        out[i * Int.SIZE_BYTES] = (value and BYTE_MASK).toByte()
        out[i * Int.SIZE_BYTES + 1] = ((value shr 8) and BYTE_MASK).toByte()
        out[i * Int.SIZE_BYTES + 2] = ((value shr 16) and BYTE_MASK).toByte()
        out[i * Int.SIZE_BYTES + 3] = ((value shr 24) and BYTE_MASK).toByte()
    }
    return out
}

private const val BYTE_MASK = 0xFF
