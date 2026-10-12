/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.core.host

import kotlinx.browser.window
import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int32Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.fetch.Response

// Resources are served at the same relative path by webpack, so plain fetch() works.
/**
 * Reads a bundled resource from the host web server at [path] via the browser fetch API as raw bytes.
 *
 * @param path The relative resource path to read.
 * @return The raw byte contents of the resource.
 */
actual suspend fun readResourceBytes(path: String): ByteArray {
    val response: Response = window.fetch(path).await()
    if (!response.ok) {
        error("Resource not found: $path (HTTP ${response.status})")
    }
    val buffer: ArrayBuffer = response.arrayBuffer().await()
    return buffer.copyToByteArray()
}

// Int8Array.get crosses the JS/Wasm boundary once per byte. Viewing the same buffer as 32-bit
// words crosses it once per four bytes instead, unpacking each word into its bytes with plain
// shifts (no JS call). Kotlin/Wasm 2.4.10 has no way to view Wasm's own linear memory from JS, or
// this would cross the boundary only once per file, not once per word -- see awakekt/awake#709.
internal fun ArrayBuffer.copyToByteArray(): ByteArray {
    val length = byteLength
    val wordCount = length / 4
    val words = Int32Array(this, 0, wordCount)
    val bytes = ByteArray(length)
    for (i in 0 until wordCount) {
        val word = words[i]
        val offset = i * 4
        bytes[offset] = word.toByte()
        bytes[offset + 1] = (word ushr 8).toByte()
        bytes[offset + 2] = (word ushr 16).toByte()
        bytes[offset + 3] = (word ushr 24).toByte()
    }
    val tailStart = wordCount * 4
    if (tailStart < length) {
        val tail = Int8Array(this, tailStart, length - tailStart)
        for (i in 0 until tail.length) {
            bytes[tailStart + i] = tail[i]
        }
    }
    return bytes
}
