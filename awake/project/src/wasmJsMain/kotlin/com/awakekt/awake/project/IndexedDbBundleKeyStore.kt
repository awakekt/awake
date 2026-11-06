/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.project

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import kotlin.coroutines.cancellation.CancellationException
import kotlin.js.JsAny
import kotlin.js.Promise

/**
 * Persists non-extractable WebCrypto `HKDF` `CryptoKey` handles in the browser's IndexedDB through
 * HTML structured clone, keyed by `[namespace, keyId]`, so a return visit can open cached encrypted
 * bundles offline without ever storing or exporting raw key bytes (`extractable` stays `false`).
 *
 * Like [IndexedDbContentCache], this store never fails a load when browser storage is blocked or
 * full: [get] reads as a miss so [BundleKeyProvider.wrapped] fetches a fresh wrapped key online, and
 * [put] or [delete] drops silently when a transaction aborts.
 */
class IndexedDbBundleKeyStore internal constructor(
    private val namespace: String,
    private val databaseName: String,
) : BundleKeyStore {
    /** @param namespace Separates one project's persisted key handles from another's under the same origin. */
    constructor(namespace: String = "default") : this(namespace, DATABASE_NAME)

    private var opening: Promise<JsAny?>? = null

    override suspend fun get(keyId: String): BundleKeyHandle? {
        require(keyId.isNotBlank()) { "A bundle key needs a non-blank keyId" }
        val cryptoKey = useDatabase { jsKeyStoreGet(it, namespace, keyId) } ?: return null
        return BundleKeyHandle(keyId, WebCryptoBundleKey(keyId, cryptoKey))
    }

    override suspend fun put(handle: BundleKeyHandle) {
        val cryptoKey = toNonExtractableCryptoKey(handle.activePlatformKey())
        useDatabase { jsKeyStorePut(it, namespace, handle.keyId, cryptoKey) }
    }

    override suspend fun delete(keyId: String) {
        require(keyId.isNotBlank()) { "A bundle key needs a non-blank keyId" }
        useDatabase { jsKeyStoreDelete(it, namespace, keyId) }
    }

    private suspend fun toNonExtractableCryptoKey(platformKey: PlatformBundleKey): JsAny =
        when (platformKey) {
            is WebCryptoBundleKey -> platformKey.activeCryptoKey()
            is SoftwareBundleKey -> {
                val raw = platformKey.copyActiveMasterBytes()
                val buffer = try {
                    raw.toArrayBuffer()
                } finally {
                    raw.fill(0)
                }
                try {
                    jsImportNonExtractableHkdfKey(buffer).await()
                } finally {
                    jsZeroArrayBuffer(buffer)
                }
            }
            else -> error("Unsupported PlatformBundleKey: $platformKey")
        }

    private suspend fun useDatabase(request: (database: JsAny) -> Promise<JsAny?>): JsAny? {
        val pending = opening ?: jsKeyStoreOpen(databaseName).also { opening = it }
        var database: JsAny? = null
        return try {
            database = pending.await<JsAny?>() ?: return null
            request(database).await<JsAny?>()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (ignored: Throwable) {
            if (opening === pending) {
                opening = null
                database?.let { jsKeyStoreClose(it) }
            }
            null
        }
    }

    private companion object {
        const val DATABASE_NAME = "awake-bundle-keys-v1"
    }
}

@JsFun(
    """
(name) => new Promise((resolve, reject) => {
  if (!globalThis.indexedDB) { resolve(null); return; }
  const request = indexedDB.open(name, 1);
  request.onupgradeneeded = () => {
    if (!request.result.objectStoreNames.contains('keys')) request.result.createObjectStore('keys');
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
private external fun jsKeyStoreOpen(name: String): Promise<JsAny?>

@JsFun("(db) => { try { db.close(); } catch (e) {} }")
private external fun jsKeyStoreClose(db: JsAny)

@JsFun(
    """
(db, namespace, key) => new Promise((resolve, reject) => {
  let tx;
  try { tx = db.transaction('keys', 'readonly'); } catch (e) { reject(e); return; }
  tx.onerror = () => resolve(null);
  tx.onabort = () => resolve(null);
  try {
    const result = tx.objectStore('keys').get([namespace, key]);
    result.onsuccess = () => {
      const v = result.result;
      if (
        v &&
        typeof CryptoKey !== 'undefined' &&
        v instanceof CryptoKey &&
        v.type === 'secret' &&
        v.extractable === false &&
        v.algorithm &&
        v.algorithm.name === 'HKDF'
      ) {
        resolve(v);
      } else {
        resolve(null);
      }
    };
  } catch (e) { resolve(null); }
})
    """,
)
private external fun jsKeyStoreGet(db: JsAny, namespace: String, key: String): Promise<JsAny?>

@JsFun(
    """
(db, namespace, key, cryptoKey) => new Promise((resolve, reject) => {
  let tx;
  try { tx = db.transaction('keys', 'readwrite'); } catch (e) { reject(e); return; }
  tx.oncomplete = () => resolve(null);
  tx.onerror = () => resolve(null);
  tx.onabort = () => resolve(null);
  try { tx.objectStore('keys').put(cryptoKey, [namespace, key]); } catch (e) { resolve(null); }
})
    """,
)
private external fun jsKeyStorePut(db: JsAny, namespace: String, key: String, cryptoKey: JsAny): Promise<JsAny?>

@JsFun(
    """
(db, namespace, key) => new Promise((resolve, reject) => {
  let tx;
  try { tx = db.transaction('keys', 'readwrite'); } catch (e) { reject(e); return; }
  tx.oncomplete = () => resolve(null);
  tx.onerror = () => resolve(null);
  tx.onabort = () => resolve(null);
  try { tx.objectStore('keys').delete([namespace, key]); } catch (e) { resolve(null); }
})
    """,
)
private external fun jsKeyStoreDelete(db: JsAny, namespace: String, key: String): Promise<JsAny?>

@JsFun(
    """
(rawBuffer) => globalThis.crypto.subtle.importKey(
  'raw',
  rawBuffer,
  { name: 'HKDF' },
  false,
  ['deriveBits']
)
    """,
)
private external fun jsImportNonExtractableHkdfKey(rawBuffer: ArrayBuffer): Promise<JsAny>
