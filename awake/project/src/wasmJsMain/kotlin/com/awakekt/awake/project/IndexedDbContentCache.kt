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
import org.khronos.webgl.set
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.JsAny
import kotlin.js.JsArray
import kotlin.js.JsNumber
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toInt
import kotlin.js.toJsArray
import kotlin.js.toJsString

private const val DATABASE_NAME = "awake-content-cache-v1"

/**
 * Caches [IndexedAssetSource]'s verified files in the browser's IndexedDB, keyed by SHA-256, so a
 * return visit to the same project neither downloads nor hashes a file whose content hasn't changed. Bytes are
 * stored as an [ArrayBuffer], with no base64 round trip.
 *
 * The cache never fails a load: storage the browser blocks, fills or loses reads as a miss, so the
 * file comes from the network, and a file it can't keep is dropped, costing only the next visit a
 * download.
 *
 * Every read and write shares one connection, opened on first use. Another tab upgrading the
 * store closes it, and the next read or write opens a new one.
 *
 * Files are kept by content and never expire, so call [retainOnly] with each release's index, or
 * the store keeps every release's changed files until the browser's quota runs out.
 */
class IndexedDbContentCache internal constructor(
    private val namespace: String,
    private val databaseName: String,
) : ContentCache {
    /** @param namespace Separates one project's cached files from another's under the same origin. */
    constructor(namespace: String = "default") : this(namespace, DATABASE_NAME)

    // Null until first use, and again after the connection stops working, so the next call reopens it.
    private var opening: Promise<JsAny?>? = null

    override suspend fun get(sha256: String): ByteArray? =
        useDatabase { jsContentCacheGet(it, namespace, sha256) }?.unsafeCast<ArrayBuffer>()?.copyToByteArray()

    override suspend fun put(sha256: String, bytes: ByteArray) {
        val content = bytes.toArrayBuffer()
        useDatabase { jsContentCachePut(it, namespace, sha256, content) }
    }

    /**
     * Deletes this namespace's files that [index] doesn't list, such as files an earlier release
     * used and this one doesn't. Other namespaces are untouched.
     *
     * @return How many files were deleted; 0 when the browser's storage is blocked or unavailable.
     */
    suspend fun retainOnly(index: ProjectIndex): Int {
        val keep = index.files.map { it.sha256.lowercase().toJsString() }.toTypedArray().toJsArray()
        return useDatabase { jsContentCacheRetain(it, namespace, keep) }?.unsafeCast<JsNumber>()?.toInt() ?: 0
    }

    private suspend fun useDatabase(request: (database: JsAny) -> Promise<JsAny?>): JsAny? {
        val pending = opening ?: jsContentCacheOpen(databaseName).also { opening = it }
        var database: JsAny? = null
        return try {
            database = pending.await<JsAny?>() ?: return null
            request(database).await<JsAny?>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ignored: Throwable) {
            // Only a broken connection rejects; a failed read or write resolves to null instead.
            if (opening === pending) {
                opening = null
                database?.let { jsContentCacheClose(it) }
            }
            null
        }
    }
}

// Mirrors awake:core:host's Resource.kt: indexing a typed array crosses the JS/Wasm boundary once
// per element, so both directions move 32-bit words, crossing once per four bytes. Duplicated
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

private fun ByteArray.toArrayBuffer(): ArrayBuffer {
    val buffer = ArrayBuffer(size)
    val wordCount = size / 4
    val words = Int32Array(buffer, 0, wordCount)
    for (i in 0 until wordCount) {
        val offset = i * 4
        words[i] = (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            (this[offset + 3].toInt() shl 24)
    }
    val tailStart = wordCount * 4
    if (tailStart < size) {
        val tail = Int8Array(buffer, tailStart, size - tailStart)
        for (i in 0 until size - tailStart) {
            tail[i] = this[tailStart + i]
        }
    }
    return buffer
}

// Resolves to the connection, or to null where the browser has no IndexedDB.
@JsFun(
    """
(name) => new Promise((resolve, reject) => {
  if (!globalThis.indexedDB) { resolve(null); return; }
  const request = indexedDB.open(name, 1);
  request.onupgradeneeded = () => {
    if (!request.result.objectStoreNames.contains('files')) request.result.createObjectStore('files');
  };
  request.onerror = () => reject(request.error);
  request.onsuccess = () => {
    const db = request.result;
    db.onversionchange = () => db.close();
    resolve(db);
  };
})
    """,
)
private external fun jsContentCacheOpen(name: String): Promise<JsAny?>

@JsFun("(db) => { try { db.close(); } catch (e) {} }")
private external fun jsContentCacheClose(db: JsAny)

// Rejects only when the connection itself can't start a transaction; a failed read is a miss.
@JsFun(
    """
(db, namespace, key) => new Promise((resolve, reject) => {
  let tx;
  try { tx = db.transaction('files', 'readonly'); } catch (e) { reject(e); return; }
  tx.onerror = () => resolve(null);
  tx.onabort = () => resolve(null);
  try {
    const result = tx.objectStore('files').get([namespace, key]);
    result.onsuccess = () => resolve(result.result ?? null);
  } catch (e) { resolve(null); }
})
    """,
)
private external fun jsContentCacheGet(db: JsAny, namespace: String, key: String): Promise<JsAny?>

// Rejects only when the connection itself can't start a transaction. A full origin aborts the
// write with QuotaExceededError, which fires abort rather than error; the file just isn't kept.
@JsFun(
    """
(db, namespace, key, content) => new Promise((resolve, reject) => {
  let tx;
  try { tx = db.transaction('files', 'readwrite'); } catch (e) { reject(e); return; }
  tx.oncomplete = () => resolve(null);
  tx.onerror = () => resolve(null);
  tx.onabort = () => resolve(null);
  try { tx.objectStore('files').put(content, [namespace, key]); } catch (e) { resolve(null); }
})
    """,
)
private external fun jsContentCachePut(db: JsAny, namespace: String, key: String, content: ArrayBuffer): Promise<JsAny?>

// Walks only [namespace]'s keys: [namespace] sorts before every [namespace, sha256], and
// [namespace, []] after, since IndexedDB orders an array after any string.
@JsFun(
    """
(db, namespace, keep) => new Promise((resolve, reject) => {
  let tx;
  try { tx = db.transaction('files', 'readwrite'); } catch (e) { reject(e); return; }
  const kept = new Set(keep);
  let deleted = 0;
  tx.oncomplete = () => resolve(deleted);
  tx.onerror = () => resolve(null);
  tx.onabort = () => resolve(null);
  try {
    const store = tx.objectStore('files');
    const cursor = store.openKeyCursor(IDBKeyRange.bound([namespace], [namespace, []]));
    cursor.onsuccess = () => {
      const item = cursor.result;
      if (!item) return;
      if (!kept.has(item.key[1])) { store.delete(item.primaryKey); deleted++; }
      item.continue();
    };
  } catch (e) { resolve(null); }
})
    """,
)
private external fun jsContentCacheRetain(db: JsAny, namespace: String, keep: JsArray<JsString>): Promise<JsAny?>
