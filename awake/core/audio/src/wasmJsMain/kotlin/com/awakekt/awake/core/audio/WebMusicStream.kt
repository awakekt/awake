/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(UnsafeWasmMemoryApi::class, ExperimentalWasmJsInterop::class, ExperimentalWasmInterop::class)

package com.awakekt.awake.core.audio

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.wasm.ExperimentalWasmInterop
import kotlin.wasm.unsafe.Pointer
import kotlin.wasm.unsafe.UnsafeWasmMemoryApi
import kotlin.wasm.unsafe.wasmMemory
import kotlin.wasm.unsafe.withScopedMemoryAllocator

/**
 * Starts streaming encoded audio through the browser's native `HTMLAudioElement` media pipeline,
 * avoiding full-track PCM decoding into Web Audio buffers. Returns `null` when DOM audio is absent.
 */
internal fun startWebMusicStream(
    source: MusicStreamSource,
    effectiveVolume: Float,
    loop: Boolean,
): WebMusicStreamHandle? {
    val volume = effectiveVolume.coerceIn(0f, 1f)
    return when (source) {
        is MusicStreamSource.FilePath -> {
            if (source.path.isBlank()) null else startAudioElementFromUrl(source.path, volume, loop)
        }
        is MusicStreamSource.EncodedBytes -> {
            if (source.bytes.isEmpty()) {
                null
            } else {
                val blob = audioBlobOf(source.bytes, source.mimeType.orEmpty())
                startAudioElementFromBlob(blob, volume, loop)
            }
        }
    }
}

private fun audioBlobOf(bytes: ByteArray, mimeType: String): JsAny = withScopedMemoryAllocator { allocator ->
    val base = allocator.allocate(maxOf(bytes.size, 1))
    storeStreamBytes(base, bytes)
    audioBlobFromLinearMemory(wasmMemory, base.address.toInt(), bytes.size, mimeType)
}

private fun storeStreamBytes(base: Pointer, bytes: ByteArray) {
    val words = bytes.size / Int.SIZE_BYTES
    for (word in 0 until words) {
        val at = word * Int.SIZE_BYTES
        val packed = (bytes[at].toInt() and BYTE_MASK) or
            ((bytes[at + 1].toInt() and BYTE_MASK) shl BYTE_1_SHIFT) or
            ((bytes[at + 2].toInt() and BYTE_MASK) shl BYTE_2_SHIFT) or
            ((bytes[at + 3].toInt() and BYTE_MASK) shl BYTE_3_SHIFT)
        (base + at).storeInt(packed)
    }
    for (at in words * Int.SIZE_BYTES until bytes.size) {
        (base + at).storeByte(bytes[at])
    }
}

@JsFun(
    """
(memory, byteOffset, byteSize, mimeType) => {
    const view = new Uint8Array(memory.buffer, byteOffset, byteSize);
    return mimeType ? new Blob([view], { type: mimeType }) : new Blob([view]);
}
""",
)
internal external fun audioBlobFromLinearMemory(
    memory: JsAny,
    byteOffset: Int,
    byteSize: Int,
    mimeType: String,
): JsAny

@JsFun(
    """
(url, volume, loop) => {
    if (typeof Audio === 'undefined') return null;
    const element = new Audio(url);
    element.loop = loop;
    element.volume = volume;
    const playPromise = element.play();
    if (playPromise && typeof playPromise.catch === 'function') playPromise.catch(() => {});
    return { element, objectUrl: null, stopped: false };
}
""",
)
internal external fun startAudioElementFromUrl(
    url: String,
    volume: Float,
    loop: Boolean,
): WebMusicStreamHandle?

@JsFun(
    """
(blob, volume, loop) => {
    if (typeof Audio === 'undefined' || typeof URL === 'undefined' || typeof URL.createObjectURL !== 'function') return null;
    const objectUrl = URL.createObjectURL(blob);
    const element = new Audio(objectUrl);
    element.loop = loop;
    element.volume = volume;
    const playPromise = element.play();
    if (playPromise && typeof playPromise.catch === 'function') playPromise.catch(() => {});
    return { element, objectUrl, stopped: false };
}
""",
)
internal external fun startAudioElementFromBlob(
    blob: JsAny,
    volume: Float,
    loop: Boolean,
): WebMusicStreamHandle?

@JsFun("(stream, volume) => { if (!stream.stopped) { stream.element.volume = volume; } }")
internal external fun setWebMusicStreamVolume(stream: WebMusicStreamHandle, volume: Float)

@JsFun(
    """
(stream) => {
    if (stream.stopped) return;
    stream.stopped = true;
    try { stream.element.pause(); } catch (_) {}
    stream.element.src = '';
    if (stream.objectUrl && typeof URL !== 'undefined' && typeof URL.revokeObjectURL === 'function') {
        URL.revokeObjectURL(stream.objectUrl);
        stream.objectUrl = null;
    }
}
""",
)
internal external fun stopWebMusicStream(stream: WebMusicStreamHandle)

private const val BYTE_MASK = 0xFF
private const val BYTE_1_SHIFT = 8
private const val BYTE_2_SHIFT = 16
private const val BYTE_3_SHIFT = 24
