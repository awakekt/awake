/*
 * SPDX-FileCopyrightText: 2023-2026 Ron June Valdoz
 *
 * SPDX-License-Identifier: Apache-2.0
 */
@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package com.awakekt.awake.project

import com.awakekt.awake.core.io.AssetPath
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.js.JsAny
import kotlin.js.JsBoolean
import kotlin.js.JsString
import kotlin.js.Promise
import kotlin.js.toBoolean
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Verifies against real browser SubtleCrypto and IndexedDB that session-wrapped keys stay
 * non-extractable (`extractable === false`) both in memory and across IndexedDB structured-clone
 * persistence, and that `exportKey` is rejected.
 */
class BundleKeyWrappingWasmTest {
    private val scene = "scenes/main.scene.json"
    private val crate = "assets/models/crate.glb"

    @Test
    fun sessionPrivateKeyAndUnwrappedHkdfKeyAreNonExtractableAndRejectExport() = runTest {
        val project = EncryptedProject.write()
        val serverKey = BundleEncryptionKey(EncryptedProject.KEY_ID, project.key)
        val exchange = BundleSessionKeyExchange.generate()
        try {
            val unwrapper = assertIs<WebCryptoSessionKeyUnwrapper>(exchange.unwrapper)
            val privateKey = assertNotNull(unwrapper.privateCryptoKeyForTest)
            assertFalse(jsIsExtractable(privateKey), "ephemeral RSA-OAEP private key must be non-extractable")
            assertEquals("private", jsKeyType(privateKey))
            assertEquals("RSA-OAEP", jsKeyAlgorithmName(privateKey))
            assertEquals("unwrapKey", jsKeyUsages(privateKey))
            assertTrue(jsExportRejected(privateKey, "pkcs8").await<JsBoolean>().toBoolean())
            assertTrue(jsExportRejected(privateKey, "jwk").await<JsBoolean>().toBoolean())

            val wrapped = serverKey.wrapFor(exchange.publicKeySpki)
            val handle = exchange.unwrap(EncryptedProject.KEY_ID, wrapped)
            try {
                val webKey = assertIs<WebCryptoBundleKey>(handle.platformKey)
                val hkdfKey = assertNotNull(webKey.hkdfCryptoKeyForTest)
                assertFalse(jsIsExtractable(hkdfKey), "unwrapped HKDF CryptoKey must be non-extractable")
                assertEquals("secret", jsKeyType(hkdfKey))
                assertEquals("HKDF", jsKeyAlgorithmName(hkdfKey))
                assertEquals("deriveBits", jsKeyUsages(hkdfKey))
                assertTrue(jsExportRejected(hkdfKey, "raw").await<JsBoolean>().toBoolean())
                assertTrue(jsExportRejected(hkdfKey, "jwk").await<JsBoolean>().toBoolean())

                val source = IndexedAssetSource(
                    project.index,
                    project.server(),
                    keys = object : BundleKeyProvider {
                        override suspend fun key(keyId: String): ByteArray = error("raw key must not be called")
                        override suspend fun keyHandle(keyId: String): BundleKeyHandle = handle
                    },
                )
                project.files.forEach { (path, bytes) ->
                    assertContentEquals(bytes, source.read(AssetPath(path)).getOrThrow(), path)
                }
            } finally {
                handle.close()
            }
        } finally {
            exchange.close()
        }
    }

    @Test
    fun indexedDbBundleKeyStoreStructuredClonesNonExtractableCryptoKeyForOfflinePlay() = runTest {
        val suffix = Random.nextInt()
        val namespace = "harbor-town-${suffix.toUInt()}"
        val store = assertIs<IndexedDbBundleKeyStore>(platformBundleKeyStore(namespace))
        val cache = IndexedDbContentCache(namespace)
        val project = EncryptedProject.write()
        val serverKey = BundleEncryptionKey(EncryptedProject.KEY_ID, project.key)

        var onlineAsks = 0
        val onlineKeys = BundleKeyProvider.wrapped(store) { keyId, spki ->
            onlineAsks++
            assertEquals(EncryptedProject.KEY_ID, keyId)
            serverKey.wrapFor(spki)
        }
        IndexedAssetSource(project.index, project.server(), cache, keys = onlineKeys)
            .prefetch(project.files.keys.toList())
        assertEquals(1, onlineAsks)

        val reloaded = assertNotNull(store.get(EncryptedProject.KEY_ID))
        try {
            val reloadedWebKey = assertIs<WebCryptoBundleKey>(reloaded.platformKey)
            val reloadedCryptoKey = assertNotNull(reloadedWebKey.hkdfCryptoKeyForTest)
            assertFalse(jsIsExtractable(reloadedCryptoKey), "structured-cloned CryptoKey must keep extractable === false")
            assertEquals("secret", jsKeyType(reloadedCryptoKey))
            assertEquals("HKDF", jsKeyAlgorithmName(reloadedCryptoKey))
            assertTrue(jsExportRejected(reloadedCryptoKey, "raw").await<JsBoolean>().toBoolean())
        } finally {
            reloaded.close()
        }

        // Closing one reloaded handle does not invalidate IndexedDB; offline source reloads its own handle.
        val offlineKeys = BundleKeyProvider.wrapped(store) { _, _ -> error("offline: key should come from IndexedDB") }
        val offlineSource = IndexedAssetSource(project.index, { error("offline: $it") }, cache, keys = offlineKeys)
        assertContentEquals(project.files.getValue(scene), offlineSource.read(AssetPath(scene)).getOrThrow())
        assertContentEquals(project.files.getValue(crate), offlineSource.read(AssetPath(crate)).getOrThrow())
    }

    @Test
    fun indexedDbBundleKeyStoreImportsRawHandlesDeletesIsolatesNamespacesAndRejectsTamperedRecords() = runTest {
        val suffix = Random.nextInt().toUInt()
        val storeA = IndexedDbBundleKeyStore("test-$suffix-a")
        val storeB = IndexedDbBundleKeyStore("test-$suffix-b")

        val fromRaw = BundleKeyHandle.fromRawBytes("shared-id", EncryptedProject.KEY)
        storeA.put(fromRaw)
        fromRaw.close()

        assertNull(storeB.get("shared-id"), "namespaces stay isolated")
        val imported = assertNotNull(storeA.get("shared-id"))
        val importedCryptoKey = assertNotNull(assertIs<WebCryptoBundleKey>(imported.platformKey).hkdfCryptoKeyForTest)
        assertFalse(jsIsExtractable(importedCryptoKey), "raw handles are converted to non-extractable CryptoKeys before IndexedDB put")
        assertTrue(jsExportRejected(importedCryptoKey, "raw").await<JsBoolean>().toBoolean())
        imported.close()

        // Inject an extractable CryptoKey directly into IndexedDB; IndexedDbBundleKeyStore.get must reject it as null.
        val dbName = "awake-bundle-keys-tampered-$suffix"
        val tamperedStore = IndexedDbBundleKeyStore("ns", dbName)
        val dummy = BundleKeyHandle.fromRawBytes("k1", EncryptedProject.KEY)
        tamperedStore.put(dummy)
        dummy.close()
        jsOverwriteWithExtractableKey(dbName, "ns", "k1").await<JsString>()
        assertNull(tamperedStore.get("k1"), "extractable CryptoKey in IndexedDB must be rejected")

        storeA.delete("shared-id")
        assertNull(storeA.get("shared-id"))
    }

    @Test
    fun blockedKeyStoreStillOpensBundlesOnline() = runTest {
        val project = EncryptedProject.write()
        val serverKey = BundleEncryptionKey(EncryptedProject.KEY_ID, project.key)
        installBlockedKeyStoreIndexedDb()
        try {
            val store = IndexedDbBundleKeyStore("blocked-${Random.nextInt().toUInt()}")
            assertNull(store.get(EncryptedProject.KEY_ID))
            val keys = BundleKeyProvider.wrapped(store) { _, spki -> serverKey.wrapFor(spki) }
            val source = IndexedAssetSource(project.index, project.server(), keys = keys)
            assertContentEquals(project.files.getValue(crate), source.read(AssetPath(crate)).getOrThrow())
        } finally {
            restoreKeyStoreIndexedDb()
        }
    }
}

@JsFun("(key) => Boolean(key.extractable)")
private external fun jsIsExtractable(key: JsAny): Boolean

@JsFun("(key) => String(key.type)")
private external fun jsKeyType(key: JsAny): String

@JsFun("(key) => String(key.algorithm && key.algorithm.name)")
private external fun jsKeyAlgorithmName(key: JsAny): String

@JsFun("(key) => Array.from(key.usages || []).join(',')")
private external fun jsKeyUsages(key: JsAny): String

@JsFun(
    """
(key, format) => globalThis.crypto.subtle.exportKey(format, key).then(
  () => false,
  () => true
)
    """,
)
private external fun jsExportRejected(key: JsAny, format: String): Promise<JsBoolean>

@JsFun(
    """
(dbName, namespace, key) => (async () => {
  const extractableKey = await globalThis.crypto.subtle.generateKey(
    { name: 'AES-GCM', length: 256 },
    true,
    ['encrypt', 'decrypt']
  );
  await new Promise((resolve, reject) => {
    const req = indexedDB.open(dbName, 1);
    req.onerror = () => reject(req.error);
    req.onsuccess = () => {
      const db = req.result;
      const tx = db.transaction('keys', 'readwrite');
      tx.oncomplete = () => { db.close(); resolve(); };
      tx.onerror = () => { db.close(); reject(tx.error); };
      tx.objectStore('keys').put(extractableKey, [namespace, key]);
    };
  });
  return 'done';
})()
    """,
)
private external fun jsOverwriteWithExtractableKey(dbName: String, namespace: String, key: String): Promise<JsString>

@JsFun(
    """
() => {
  globalThis.__awakeRealKeyIndexedDb = Object.getOwnPropertyDescriptor(globalThis, 'indexedDB');
  Object.defineProperty(globalThis, 'indexedDB', {
    configurable: true,
    value: { open: () => { throw new DOMException('Access to storage is denied.', 'SecurityError'); } },
  });
}
    """,
)
private external fun installBlockedKeyStoreIndexedDb()

@JsFun(
    """
() => {
  const real = globalThis.__awakeRealKeyIndexedDb;
  if (real) Object.defineProperty(globalThis, 'indexedDB', real); else delete globalThis.indexedDB;
  delete globalThis.__awakeRealKeyIndexedDb;
}
    """,
)
private external fun restoreKeyStoreIndexedDb()
