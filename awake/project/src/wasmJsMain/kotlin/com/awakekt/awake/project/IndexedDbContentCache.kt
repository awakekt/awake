/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.project

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int32Array
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.khronos.webgl.toInt8Array
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Caches [IndexedAssetSource]'s verified files in the browser's IndexedDB, keyed by SHA-256, so a
 * return visit to the same project neither re-fetches nor re-hashes a file whose content hasn't
 * changed. IndexedDB stores the bytes as the [ArrayBuffer] they already are -- no base64 detour.
 *
 * @param namespace Separates one project's cached files from another's under the same origin.
 */
class IndexedDbContentCache(private val namespace: String = "default") : ContentCache {
    override suspend fun get(sha256: String): ByteArray? {
        val stored = jsContentCacheGet(namespace, sha256).await<JsAny?>() ?: return null
        return stored.unsafeCast<ArrayBuffer>().copyToByteArray()
    }

    override suspend fun put(sha256: String, bytes: ByteArray) {
        jsContentCachePut(namespace, sha256, bytes.toInt8Array().buffer).await<JsAny?>()
    }
}

// Mirrors awake:core:host's Resource.kt: Int8Array.get crosses the JS/Wasm boundary once per
// byte, so the buffer is read as 32-bit words instead, crossing once per four bytes. Duplicated
// rather than shared across modules -- see that file's note on why this isn't the full fix.
private fun ArrayBuffer.copyToByteArray(): ByteArray {
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

@JsFun(
    """
(namespace, key) => new Promise((resolve, reject) => {
  if (!globalThis.indexedDB) { resolve(null); return; }
  const request = indexedDB.open('awake-content-cache-v1', 1);
  request.onupgradeneeded = () => {
    if (!request.result.objectStoreNames.contains('files')) request.result.createObjectStore('files');
  };
  request.onerror = () => reject(request.error);
  request.onsuccess = () => {
    const result = request.result.transaction('files', 'readonly').objectStore('files').get(namespace + ':' + key);
    result.onerror = () => reject(result.error);
    result.onsuccess = () => resolve(result.result ?? null);
  };
})
    """,
)
private external fun jsContentCacheGet(namespace: String, key: String): Promise<JsAny?>

@JsFun(
    """
(namespace, key, content) => new Promise((resolve, reject) => {
  if (!globalThis.indexedDB) { resolve(null); return; }
  const request = indexedDB.open('awake-content-cache-v1', 1);
  request.onupgradeneeded = () => {
    if (!request.result.objectStoreNames.contains('files')) request.result.createObjectStore('files');
  };
  request.onerror = () => reject(request.error);
  request.onsuccess = () => {
    const tx = request.result.transaction('files', 'readwrite');
    tx.objectStore('files').put(content, namespace + ':' + key);
    tx.oncomplete = () => resolve(null);
    tx.onerror = () => reject(tx.error);
  };
})
    """,
)
private external fun jsContentCachePut(namespace: String, key: String, content: ArrayBuffer): Promise<JsAny?>
